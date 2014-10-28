package prince.app.sphotos.download;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Random;

import org.apache.http.ParseException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import prince.app.sphotos.FB_Photos_Activity;
import prince.app.sphotos.tools.FBINIT;
import prince.app.sphotos.tools.Global;
import prince.app.sphotos.tools.ImageProperty;
import prince.app.sphotos.tools.Util;
import android.annotation.TargetApi;
import android.content.Context;
//import prince.app.sphotos.util.AsyncTask;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

public class Download_albumImages {

	private static final String TAG = "Download_albumImages";

	private String moreDataURL = null;
	private Boolean moreDataAvailable;
	private String next;
	private String initaddress;
	private String nameOfAlbum;
    private static HttpURLConnection connection;
	
	private int index = 0;
	private int numberOfImages;
	private int albumGridPosition;
	private int num;
	private int modifier;
	private int maxRetry;
	private int maxJRetry;
	private Download task;
	private boolean taskStarted;
	private boolean error;
	protected boolean taskPaused;
	protected final Object pauseWorkLock = new Object();
	private boolean taskFinished;
	private Context context;
	
	public Download_albumImages(int position, int numberOfImages, String name, Context context) {
		this.numberOfImages = numberOfImages;
		this.albumGridPosition = position;
		this.nameOfAlbum = name;
		modifier = 1;
		this.context = context;
		Random r  = new Random();
		num = r.nextInt(numberOfImages - 2) + 2;
	}
	
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
	
	/** initialize variables*/
	private void initVar(){
    	FBINIT.IMAGE_DETAILS_AVAILABLE = false;
    	FBINIT.IMAGE_DETAILS_TASK_DONE = false;
    	FBINIT.IMAGE_DETAILS_TASK_SUCCESSFUL = false;
		taskStarted = false;
		taskFinished = false;
		taskPaused = false;
		moreDataAvailable = false;
	}
	
	public void loadImage(String address, String albumName) {
        initVar();
        task = new Download();
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, address);
        initaddress = address;
	}
	

	private boolean buildJSON(JSONArray aAlbum){
		try{
			if (aAlbum != null){
				for (int i=0; i<aAlbum.length(); i++){
					ImageProperty User = new ImageProperty();
					
					if (error){
						i = index; // set index to the next available space in array
						error = false;
					}
					
					JSONObject eachAlbum = aAlbum.getJSONObject(i);				
					User.setImageID(eachAlbum.optString(FBINIT.ID));					// 	Set ID
					
					JSONObject from = eachAlbum.getJSONObject(FBINIT.FROM);		
					User.setOwnerID(from.getString(FBINIT.ID));						// 	Set FROM ID
					User.setOwnerName(from.getString(FBINIT.NAME));					//	Set FROM NAME
					
					User.setThumbnail(eachAlbum.optString(FBINIT.PICTURE));				//	Set Thumb nail
					User.setHeight(eachAlbum.optInt(FBINIT.HEIGHT));					//	Set HEIGHT
					User.setWidth(eachAlbum.optInt(FBINIT.WIDTH));						//	Set WIDTH
					User.setcTime(eachAlbum.optString(FBINIT.CTIME));					//	Set	COUNT
					User.setuTime(eachAlbum.optString(FBINIT.UTIME));				//	Set TYPE
					
					JSONArray image = eachAlbum.getJSONArray(FBINIT.IMAGE);
					JSONObject nImage = image.getJSONObject(0);
					User.setImage(nImage.optString(FBINIT.SOURCE));
	
					synchronized (FBINIT.IMAGE_DETAILS) {
					     FBINIT.IMAGE_DETAILS.append(index, User);
					     index = index + 1;
					}
					
					Log.d(TAG, "details of image " + (index-1) + " with id: " + eachAlbum.optString(FBINIT.ID) +"  downloaded");
				}
				return true;
			}else{
				Log.d(TAG, "Request not successful ");
				return false;
			}
		}catch (JSONException e){
			Log.d(TAG, "JSONException error");
			e.printStackTrace();
			error = true;
			return rebuild(aAlbum);
			
		}catch (ParseException e) {
			Log.d(TAG, "ParseException error");
			e.printStackTrace();
			error = true;
			return rebuild(aAlbum);
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
			Log.d(TAG, "FileNotFoundException in connect");
			e.printStackTrace();
			return null;
		}catch (IOException e) {
			Log.d(TAG, "IOException in connect");
			e.printStackTrace();
			return retryConnection(URL);
		}
	}
	
	private InputStream retryConnection(String URL){
		if (Global.getInstance().isNetworkAvailable() && maxRetry > 0){
			maxRetry -= 1;
			return connect(URL);
		}
			
		else{
			cancelWork();
		//	FB_Photos_Activity.noInternet(context);
		}
	
		return null;
	}
	
	private boolean rebuild(JSONArray resultAlbum){
		if (maxJRetry > 0){
			maxJRetry -= 1;
			return buildJSON(resultAlbum);
		}
		return false;
	}
	
	    
	class Download extends AsyncTask<String, Integer, Boolean>{
		private String line;
		private StringBuilder queryAlbums;
		private boolean successful = false;

		@Override
		protected Boolean doInBackground(String...params){
			taskStarted = true;
			
			// Wait here if work is paused and the task is not cancelled
            synchronized (pauseWorkLock) {
                while (taskPaused && !isCancelled()) {
                    try {
                        pauseWorkLock.wait();
                    } catch (InterruptedException e) {}
                }
            }
            
			try{
				 if (!isCancelled()) {
					 Log.d(TAG, "- download started -");
					 Log.d(TAG, " connection URL: " + params[0]);
					 
			
					 // make http call and retrieve result
					 InputStream input = connect(params[0]);
					
					 if (input != null){
						 BufferedReader reader = new BufferedReader(new InputStreamReader(input));
						 queryAlbums = new StringBuilder();
						
						 while ((line = reader.readLine()) != null) {
							 queryAlbums.append(line);
						 }
							 
						 JSONObject Album = new JSONObject(queryAlbums.toString());
						 
						
						 if (Album.has(FBINIT.PAGING)){
							 Log.d(TAG, "We have more data");
							 JSONObject page = Album.getJSONObject(FBINIT.PAGING);
							 if (page.has(FBINIT.NEXT)){
								 moreDataURL = page.getString(FBINIT.NEXT);
								 moreDataAvailable = true;
								 Log.d(TAG, "more data URL: " + moreDataURL);
							 }
							
							 if (page.has(FBINIT.CURSORS)){
								 JSONObject cursor = page.getJSONObject(FBINIT.CURSORS);
								 next = cursor.getString(FBINIT.AFTER);
								 moreDataAvailable = true;
							 }
						 }
						
						 if (Album.has(FBINIT.DATA)){
							 JSONArray aAlbum = Album.getJSONArray(FBINIT.DATA);
							
							 successful = buildJSON(aAlbum);
							
							 if (!successful)
								 Log.d(TAG, "Image JSON Build not successful");
							
							 return successful;
							
						 }
						 else{
							 Log.d(TAG, "Request has no Image JSON data ");
							 return false;
						 }
					 }
					 else
						 return false;
					 
				 }
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
		protected void onPostExecute(Boolean result){
			if (result && !isCancelled()){
				
				FBINIT.IMAGE_DETAILS_AVAILABLE = true;
				synchronized(FBINIT.IMAGE_DETAILS_LOCK){
					FBINIT.IMAGE_DETAILS_LOCK.notify();
				}
				
				if (moreDataAvailable && index < numberOfImages){
					if (moreDataURL != null)
						loadImage(moreDataURL, nameOfAlbum+"_"+modifier);
					else
						loadImage(initaddress+"&limit=25&after="+next, nameOfAlbum+"_"+modifier);
				}else
					FBINIT.IMAGE_DETAILS_TASK_SUCCESSFUL = true;
					updatePhotos();
			}
			else{
				FBINIT.IMAGE_DETAILS_AVAILABLE = false;
				FBINIT.IMAGE_DETAILS_TASK_SUCCESSFUL = false;
			}
			
			FBINIT.IMAGE_DETAILS_TASK_DONE = true;
			
		}
}
	
	private void updatePhotos(){
		if (FBINIT.IMAGE_DETAILS.size() > 0){
			if (nameOfAlbum.equalsIgnoreCase("Cover Photos")){
				Log.d(TAG, "cover photo picture stored in internal");
				Global.getInstance().downloadSaveToInternal(
						FBINIT.IMAGE_DETAILS.get(0).getImage(), Util.FB_COVER_GRID_IMAGE);
			}
			
			if (nameOfAlbum.equalsIgnoreCase("Profile Pictures")){
				Log.d(TAG, "profile picture stored in internal");
				Global.getInstance().downloadSaveToInternal(
						FBINIT.IMAGE_DETAILS.get(0).getImage(), Util.FB_DRAWER_SMALL_IMAGE);
			}
			
			if (albumGridPosition == num && !FBINIT.updatedAlbumGridPhoto){
				FBINIT.updatedAlbumGridPhoto = true;
				Log.d(TAG, "generic album image stored in internal");
				Global.getInstance().downloadSaveToInternal(
						FBINIT.IMAGE_DETAILS.get(num).getImage(), Util.FB_ALBUM_GRID_IMAGE);
			}
		}
	}


	
}
