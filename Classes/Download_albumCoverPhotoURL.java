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
import org.json.JSONException;
import org.json.JSONObject;

import prince.app.sphotos.tools.FBINIT;
import prince.app.sphotos.tools.Global;
import prince.app.sphotos.tools.Util;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

/** class that handles the download of image URL for the front page photo of each ALBUM */
public class Download_albumCoverPhotoURL {
	
	public static String TAG = "Download_albumCoverPhotoURL";
	
	private int index = 0;
	private HttpURLConnection connection;
	private Download task;
	private boolean taskStarted;
	protected boolean taskPaused;
	protected final Object pauseWorkLock = new Object();
	private boolean taskFinished;
	private Context context;
	private boolean jsonError;
	private boolean downloadError;
	private boolean connectError;
	private int taskRetry = 4;
	private int connectRetry = 4;
	
	/** start asynchronous download of image URL */
	public void startTask(){
		initVar();
		task = new Download();
		task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
		FBINIT.COVERPHOTO_TASK_DONE = false;
		taskStarted = false;
		taskFinished = false;
		taskPaused = false;
	}
	
	/** Establish http connection and retrieve response*/
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private InputStream connect(String URL){
		try {
			if (URL != null){
				URL newURL = new URL(URL);
				connection = (HttpURLConnection)newURL.openConnection();
				if (connection.getResponseCode() == HttpURLConnection.HTTP_OK){
					InputStream input = new BufferedInputStream(connection.getInputStream());
					return input;
				}else{
					return null;
				}
			}
			return null;
			
		}catch (FileNotFoundException e) {
			Log.d(TAG, "FileNotFoundException error in connection");
			e.printStackTrace();
			connectError = true;
		}catch (IOException e) {
			Log.d(TAG, "IOException error in connection");
		//	e.printStackTrace();
			
			if (Global.getInstance().isNetworkAvailable()){
				if (connectRetry > 0){
					connectRetry -= 1;
					connect(URL);
					Log.d(TAG, "- retrying connection - " + connectRetry);
				}
				else{
					connectRetry = 4;
					FBINIT.COVERPHOTO_TASK_ERROR = true;
					FBINIT.COVERPHOTO_TASK_SUCCESSFUL = false;
					FBINIT.COVERPHOTO_TASK_DONE = true;
				}
			}
			
			else{
				FBINIT.COVERPHOTO_TASK_ERROR = true;
				FBINIT.COVERPHOTO_TASK_SUCCESSFUL = false;
				FBINIT.COVERPHOTO_TASK_DONE = true;
				cancelWork();
			}
			connectError = true;
		}
		return null;
	}
	

	class Download extends AsyncTask<Integer, Void, Void>{
		String id;
		
		@Override
		protected Void doInBackground(Integer...params){
			taskStarted = true;

			try{
				int size = Global.getInstance().getIntPref(Util.FB_NUMBER_OF_ALBUMS);
			
				Log.d(TAG, "- cover image URL download started at - " + Global.time());
				
				 synchronized (FBINIT.ALL_ALBUM_LOCK) {
		                while (!FBINIT.ALL_ALBUM_AVAILABLE) {
		                    try {
		                        FBINIT.ALL_ALBUM_LOCK.wait();
		                    } catch (InterruptedException e) {}
		                }
		            }
				 
				 Log.d(TAG, "- User albums now available -");
			
				for (int i = 0; i<size; i++){
					
					// wait here if the task has been paused
					synchronized(pauseWorkLock){
						while (taskPaused && !isCancelled()){
							try {
								pauseWorkLock.wait();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}
			
					synchronized (FBINIT.ALL_FACEBOOK_ALBUM) {
						id = FBINIT.ALL_FACEBOOK_ALBUM.get(i).getCoverphotoID();
					}
				
					Log.d(TAG, FBINIT.ALL_FACEBOOK_ALBUM.get(i).getName() + " at grid position: " + i + " has id: " + id);
					String URL = "https://graph.facebook.com/v2.0/?id=" + id + "&access_token=" + FBINIT.FB_ACCESS_TOKEN + "&fields=picture";
				
					// make http call and retrieve result
					InputStream input = connect(URL);
			
					if (input != null){
						BufferedReader reader = new BufferedReader(new InputStreamReader(input));
						StringBuilder queryAlbums = new StringBuilder();
						String line;
						
						while ((line = reader.readLine()) != null) {
							queryAlbums.append(line);
						}
						
					//	Log.d(TAG, "Result: " + queryAlbums.toString());
			        
						JSONObject Album = new JSONObject(queryAlbums.toString());
						if (Album.has(FBINIT.PICTURE)){
							synchronized (FBINIT.ALBUM_COVER_URL) {
								FBINIT.ALBUM_COVER_URL.append(index, Album.getString(FBINIT.PICTURE));
								index = index + 1;
							}					 
						}
						connection.disconnect();
					}
				
					else{
						synchronized (FBINIT.ALBUM_COVER_URL){
							FBINIT.ALBUM_COVER_URL.append(index, null);
							index = index + 1;
						}
				
						Log.d(TAG, "unsuccessful for position: " + i + " with id: " + id);
					}
				}
			
			FBINIT.COVERPHOTO_TASK_DONE = true;
			FBINIT.COVERPHOTO_TASK_ERROR = false;
			FBINIT.COVERPHOTO_TASK_SUCCESSFUL = true;
			taskFinished = true;
		}catch (FileNotFoundException e) {
			Log.d(TAG, "FileNotFoundException error in download");
			e.printStackTrace();
		}catch (ParseException e) {
			Log.d(TAG, "ParseException error in download");
			e.printStackTrace();
		} catch (IOException e) {
			Log.d(TAG, "IOException error in download");
		//	e.printStackTrace();
			
			// if network is available, try downloading 4 times
			if (Global.getInstance().isNetworkAvailable()){
				if (taskRetry > 0){
					taskRetry -= 1;
					startTask();
					Log.d(TAG, "- retrying download - " + taskRetry);
				}
				else{
					taskRetry = 4;
					FBINIT.COVERPHOTO_TASK_ERROR = true;
					FBINIT.COVERPHOTO_TASK_SUCCESSFUL = false;
					FBINIT.COVERPHOTO_TASK_DONE = true;
					taskFinished = true;
				}
			}
			
			else{
				FBINIT.COVERPHOTO_TASK_ERROR = true;
				FBINIT.COVERPHOTO_TASK_SUCCESSFUL = false;
				FBINIT.COVERPHOTO_TASK_DONE = true;
				taskFinished = true;
			}
		} catch (JSONException e) {
			Log.d(TAG, "JSONException error in download");
			e.printStackTrace();
		}
		return null; 
	}
}
	
	
}