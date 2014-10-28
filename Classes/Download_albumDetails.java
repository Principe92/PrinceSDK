package prince.app.sphotos.download;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import prince.app.sphotos.tools.Album;
import prince.app.sphotos.tools.FBINIT;
import prince.app.sphotos.tools.Global;
import prince.app.sphotos.tools.Util;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

/** Private class that handles the download and JSON build of User's Photo Albums on Facebook*/
public class Download_albumDetails {
	private String URL = "https://graph.facebook.com/" + Global.getInstance().getStrPref(Util.FB_USER_ID) + "?fields=albums&access_token="
					+ FBINIT.FB_ACCESS_TOKEN;
	private String nextDataURL = null;
	private static String TAG = "Download_albumDetails";
	
	private boolean moreData;
	private boolean error;
	
	private int index = 0;
	private int numberOfAlbums = 0;
	private Context context;
	private HttpURLConnection connection;
	
	private Download task;
	private boolean taskStarted;
	protected boolean taskPaused;
	protected final Object pauseWorkLock = new Object();
	private boolean taskFinished;
	private int maxRetry  = 4;
	private int maxJRetry = 4;
	
	/**
	 * 
	 * @param pause - set to true to pause task and false to restart a paused task
	 */
	public  void pauseWork(boolean pause){
		synchronized(pauseWorkLock){
			taskPaused = pause;
			if (!pause){
				pauseWorkLock.notifyAll();
			}
		}
	}
	
	public void cancelWork(){
		if (taskStarted && !taskFinished && task != null){
			task.cancel(true);
		}
	}
	
	
	
	public Download_albumDetails(Context context) {
		this.context = context;
		FBINIT.ALL_ALBUM_AVAILABLE = false;
		FBINIT.ALL_ALBUM_TASK_SUCCESSFUL = false;
		FBINIT.ALL_ALBUM_TASK_DONE = false;
	}
	
	/** initialize variables*/
	private void initVar(){
		moreData = false;
		error = false;
	}
	
	/** start Async Task Request of User's Album(s) and their details*/
	public void startTask(boolean more){
		initVar();
		task = new Download();
		task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,more);
	}

	private boolean buildJSON(JSONArray resultAlbum, int length){
		try{
			for (int i=0; i<length; i++){
				if (error){
					i = index;
					error = false;
				}
				Log.d(TAG, "index: " + i + " and length: " + length);
				Album User = new Album();
				JSONObject eachAlbum = resultAlbum.getJSONObject(i);				
				User.setAlbumID(eachAlbum.optString(FBINIT.ID));					// 	Set ID
				
				JSONObject from = eachAlbum.getJSONObject(FBINIT.FROM);		
				User.setOwnerID(from.getString(FBINIT.ID));						// 	Set FROM ID
				User.setOwnerName(from.getString(FBINIT.NAME));					//	Set FROM NAME
				
				User.setName(eachAlbum.optString(FBINIT.NAME));					//	Set NAME
				User.setCoverphotoID(eachAlbum.optString(FBINIT.COVERPHOTO));		//	Set COVERPHOTO
				User.setPrivacy(eachAlbum.optString(FBINIT.PRIVACY));				//	Set PRIVACY
				User.setImages(eachAlbum.optInt(FBINIT.COUNT));					//	Set	COUNT
				User.setAlbumType(eachAlbum.optString(FBINIT.TYPE));				//	Set TYPE
				User.setCreatedTime(eachAlbum.optString(FBINIT.CTIME));			//	Set Created Time
				User.setUpdatedTime(eachAlbum.optString(FBINIT.UTIME)); 			//	Set Updated Time
		
				Log.d(TAG, "Done for: " + User.getName() + " at: " + Global.time());
						 
				synchronized (FBINIT.ALL_FACEBOOK_ALBUM) {
					FBINIT.ALL_FACEBOOK_ALBUM.append(index, User);
					index = index + 1;
				}
			}
			
			// Successfully saved Album
			Log.d(TAG, "Data Saved in ALL_FACEBOOK_ALBUM at: " + Global.time());
			return true;
		}catch (JSONException e){
			Log.d(TAG, "- JSONException");
			e.printStackTrace();
			error = true;
			return rebuild(resultAlbum, length);
		} 
	}
	
	/** Establish http connection and retrieve response*/
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private InputStream connect(String URL){
		try {
			URL newURL = new URL(URL);
			connection = (HttpURLConnection)newURL.openConnection();
	
			if (connection.getResponseCode() == HttpURLConnection.HTTP_OK){
				InputStream input = new BufferedInputStream(connection.getInputStream());
				return input;
			}else{
				return null;
			}
			
		}catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}catch (IOException e) {
			e.printStackTrace();
			Log.d(TAG, "IOException on connect");
			return retryConnection(URL);
		}
	}
	
	private InputStream retryConnection(String URL){
		if (Global.getInstance().isNetworkAvailable() && maxRetry > 0){
			maxRetry -= 1;
			return connect(URL);
		}
		else{
			// wait for network to restart connection
			synchronized(Global.netLock){
				while (!Global.getInstance().isNetworkAvailable()){
					try {
						Global.netLock.wait();
					} catch (InterruptedException e) {
						Log.d(TAG, "- retryConnection failed to Lock");
						e.printStackTrace();
						return null;
							
					}
				}
			}
			return connect(URL);
		}
	}
	
	private boolean rebuild(JSONArray resultAlbum, int length){
		if (maxJRetry > 0){
			maxJRetry -= 1;
			return buildJSON(resultAlbum, length);
		}
		return false;
	}
	
	private class Download extends AsyncTask<Boolean, Integer, Boolean>{
		private String line;
		private StringBuilder queryAlbums;
		private boolean successful;
		private  JSONArray json;
		private InputStream input;
		@Override
		protected Boolean doInBackground(Boolean...params){
			try{
				taskStarted = true;
				
				Log.d(TAG, "- download started - " + Global.time());
				
				synchronized (pauseWorkLock){
					if (taskPaused && !isCancelled()){
						try {
							pauseWorkLock.wait();
						} catch (InterruptedException e) {
							Log.d(TAG, "InterruptedException error");
							e.printStackTrace();
						}
					}
				}
				
				
				// make http call and retrieve result
				if (params[0]){
					input = connect(nextDataURL);
					Log.d(TAG, "- more data download URL: -" + nextDataURL);
				}
				else{
					input = connect(URL);
					Log.d(TAG, "- first data download URL: -" + URL);
				}
				
				if (input != null){
					BufferedReader reader = new BufferedReader(new InputStreamReader(input));
					queryAlbums = new StringBuilder();
					
					while ((line = reader.readLine()) != null) {
						queryAlbums.append(line);
					}
						 
					JSONObject Album = new JSONObject(queryAlbums.toString());
					
					if (params[0]){
						Log.d(TAG, "- more data download -");
						if (Album.has(FBINIT.PAGING)){
							JSONObject page = Album.getJSONObject(FBINIT.PAGING);
							if (page.has(FBINIT.NEXT)){
								Log.d(TAG, "We have more data");
								nextDataURL = page.getString(FBINIT.NEXT);
								moreData = true;
							}
						}
						json = Album.getJSONArray(FBINIT.DATA);
					}
					
					else{	
						Log.d(TAG, "- first data download -");
						if (Album.has(FBINIT.ALBUMS)){
							JSONObject oAlbum = Album.getJSONObject(FBINIT.ALBUMS);
							
							if (oAlbum.has(FBINIT.PAGING)){
								JSONObject page = oAlbum.getJSONObject(FBINIT.PAGING);
								if (page.has(FBINIT.NEXT)){
									Log.d(TAG, "We have more data");
									nextDataURL = page.getString(FBINIT.NEXT);
									moreData = true;
								}
							}
							json = oAlbum.getJSONArray(FBINIT.DATA);
						}
					}
						
					if (json != null){
						numberOfAlbums = numberOfAlbums + json.length();
						successful = buildJSON(json, json.length());
					}
							
					if (!successful)
						Log.d(TAG, "JSON Album Build not successful");
					
					connection.disconnect();
					return successful;
				}
				
				return false;
						
			}catch (FileNotFoundException e) {
				e.printStackTrace();
			}catch (JSONException e){
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}	
			return false; 
		}
			
		@Override
		protected void onPostExecute(Boolean successful){
			if (successful && !isCancelled() && !taskPaused){
				if (moreData && nextDataURL != null){
					startTask(true);
				}
				else {
					updatePreference();
					FBINIT.ALL_ALBUM_AVAILABLE = true;
					synchronized(FBINIT.ALL_ALBUM_LOCK){
						FBINIT.ALL_ALBUM_LOCK.notify();
					}
					
					Toast.makeText(context, "Albums downloaded", Toast.LENGTH_SHORT).show();
					Log.d(TAG, "Album downloads done at: " + Global.time());
				}
			}
			else{
				if (!isCancelled()&& !taskPaused){
					Log.d(TAG, "Error occured, try downloading again");
					error = true;
					synchronized(Global.getLock()){
						try {
							Toast.makeText(context, "An error occured while downloading albums!", Toast.LENGTH_SHORT).show();
							Toast.makeText(context, "Retrying download shortly", Toast.LENGTH_SHORT).show();
							Global.getLock().wait(500);
						
						} catch (InterruptedException e) {
							Toast.makeText(context, "Unable to restart automatically", Toast.LENGTH_SHORT).show();
							Toast.makeText(context, "Press the refresh button to restart download", Toast.LENGTH_SHORT).show();;
							e.printStackTrace();
						}
					}
				}
			}
		}
	}
	
	/** Updates the total number of: albums, images in cover photos and images in profile pictures, if modified */
	private void updatePreference(){
		Log.d(TAG, "updatePreference called at: " + Global.time());
		
		if (FBINIT.ALL_FACEBOOK_ALBUM.size()>0){
			/** Update total number of albums */
			if (Global.getInstance().prefExist(Util.FB_NUMBER_OF_ALBUMS)){
				int oldAlbumSize = Global.getInstance().getIntPref(Util.FB_NUMBER_OF_ALBUMS);
				if (oldAlbumSize != numberOfAlbums && numberOfAlbums > 0){
					Global.getInstance().modPref(Util.FB_NUMBER_OF_ALBUMS, numberOfAlbums);
					Log.d(TAG, "Total number of album modified");
				}
			}else{
				Global.getInstance().modPref(Util.FB_NUMBER_OF_ALBUMS, numberOfAlbums);
				Log.d(TAG, "Total number of album created");
			}
			
			for (int i = 0; i<numberOfAlbums; i++){
				String name = FBINIT.ALL_FACEBOOK_ALBUM.get(i).getName();
				if (name.equalsIgnoreCase("Cover Photos")){
					int newSize = FBINIT.ALL_FACEBOOK_ALBUM.get(i).getImages();
					if (Global.getInstance().prefExist(Util.FB_NUMBER_OF_COVER)){
						int oldSize = Global.getInstance().getIntPref(Util.FB_NUMBER_OF_COVER);
						if (oldSize != newSize && newSize > 0){
							Global.getInstance().modPref(Util.FB_NUMBER_OF_COVER, newSize);
							Log.d(TAG, "Number of images in Cover Photos modified");
						}
					}else{
						Global.getInstance().modPref(Util.FB_NUMBER_OF_COVER, newSize);
						Log.d(TAG, "Number of images in Cover Photos stored");
					}
				}
				
				else if (name.equalsIgnoreCase("Profile Pictures")){
					int newSize = FBINIT.ALL_FACEBOOK_ALBUM.get(i).getImages();
					if (Global.getInstance().prefExist(Util.FB_NUMBER_OF_PROFILE)){
						int oldSize = Global.getInstance().getIntPref(Util.FB_NUMBER_OF_PROFILE);
						if (oldSize != newSize && newSize > 0){
							Global.getInstance().modPref(Util.FB_NUMBER_OF_PROFILE, newSize);
							Log.d(TAG, "Number of images in Profile Pictures modified");
						}
					}else{
						Global.getInstance().modPref(Util.FB_NUMBER_OF_PROFILE, newSize);
						Log.d(TAG, "Number of images in Profile Pictures created");
					}
				}
			}
		}
	}
	

}
		
	
	