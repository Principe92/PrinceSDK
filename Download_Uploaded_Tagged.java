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

import prince.app.sphotos.FB_Tag_Activity;
import prince.app.sphotos.tools.FBINIT;
import prince.app.sphotos.tools.Global;
import prince.app.sphotos.tools.ImageProperty;
import prince.app.sphotos.tools.Util;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

public class Download_Uploaded_Tagged {
	
	private static final String TAG = "Download_Uploaded_Tagged";
	
	private String nextDataURL = null;
	private Boolean moreDataAvailable;
	private String next;
	private String initAddress;
	private Context context;
	
    private HttpURLConnection connection;
    
	private int index = 0;
	private int launchCode;
	private int maxRetry = 4;
	private int maxJRetry = 4;
	private Download task;
	private boolean taskStarted;
	protected boolean taskPaused;
	private boolean error = false;
	protected Object pauseWorkLock = new Object();
	private boolean taskFinished;
	
	/**
	 * 
	 * @param pause - set to true to pause task and false to restart a paused task
	 */
	public  void pauseWork(boolean pause){
		synchronized(pauseWorkLock){
			taskPaused = pause;
			Log.d(TAG, "task paused? - " + pause);
			if (!pause){
				pauseWorkLock.notifyAll();
				Log.d(TAG, "- task restarted -");
			}
		}
	}
	
	public void cancelWork(){
		if (taskStarted && !taskFinished && task != null){
			task.cancel(true);
			Log.d(TAG, "- task cancelled -");
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
	
	public Download_Uploaded_Tagged(int launchCode, Context context) {
		this.launchCode = launchCode;
		this.context = context;
	}
	
	
	public void loadImage(String address, boolean more) {
		initVar();
		task = new Download(more);
		task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, address);
		initAddress = address;
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
		//	e.printStackTrace();
			return null;
		}catch (IOException e) {
			Log.d(TAG, "IOException in connect");
		//	e.printStackTrace();
			return retryConnection(URL);
		}
	}
	 
	 private boolean buildJSON(JSONArray aAlbum){
			try{
				if (aAlbum != null){
					for (int i=0; i<aAlbum.length(); i++){
						ImageProperty User = new ImageProperty();
						
						if (error){
							i = index;
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
				Log.d(TAG, "JSONException in JSON");
			//	e.printStackTrace();
				return rebuild(aAlbum);
			} catch (ParseException e) {
				Log.d(TAG, "ParseException in JSON");
			//	e.printStackTrace();
				return rebuild(aAlbum);
			}
		}
		
	 private InputStream retryConnection(String URL){
			if (Global.getInstance().isNetworkAvailable() && maxRetry > 0){
				maxRetry -= 1;
				return connect(URL);
			}
			else{
				cancelWork();
				FB_Tag_Activity.noInternet(context);
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
		private boolean more;
		

		public Download(boolean more) {
			this.more = more;
		}

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
					Log.d(TAG, "- image details download task started -");
					Log.d(TAG, " connection URL: " + params[0]);
					
					if (more)
						Log.d(TAG, "- more download in progress -");
					else
						Log.d(TAG, "- first download in progress -");
					
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
								nextDataURL = page.getString(FBINIT.NEXT);
								Log.d(TAG, "- next data URL - " + nextDataURL);
								moreDataAvailable = true;
							}
						
							if (!page.has(FBINIT.PREVIOUS)){
								if (page.has(FBINIT.CURSORS)){
									JSONObject cursor = page.getJSONObject(FBINIT.CURSORS);
									next = cursor.getString(FBINIT.AFTER);
									moreDataAvailable = true;
								}
							}
						}
						
					
						if (Album.has(FBINIT.DATA)){
							JSONArray aAlbum = Album.getJSONArray(FBINIT.DATA);
							
							 successful = buildJSON(aAlbum);
							
							 if (!successful)
								 Log.d(TAG, "Image JSON Build not successful");
							
							 return successful;
						}else{
							Log.d(TAG, "Request not successful ");
							return false;
						}
					 }else{
						return false;
					}
				}else{
					return false;
				}
			}catch (JSONException e){
				Log.d(TAG, "JSONException in download");
				e.printStackTrace();
			} catch (ParseException e) {
				Log.d(TAG, "ParseException in download");
				e.printStackTrace();
			} catch (IOException e) {
				Log.d(TAG, "IOException in download");
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
				
				if (moreDataAvailable){
					if (nextDataURL != null){
						loadImage(nextDataURL, true);
						Log.d(TAG, "- using next for more download - ");
					}
					else{
						loadImage(initAddress+"&limit=25&after="+next, true);
						Log.d(TAG, "- using after for more download -");
					}
				}
				else{
					updatePreference();
					FBINIT.IMAGE_DETAILS_TASK_SUCCESSFUL = true;
					Log.d(TAG, "- Preference updated -");
				}
			}
			
			else{
				FBINIT.IMAGE_DETAILS_TASK_SUCCESSFUL = false;
				FBINIT.IMAGE_DETAILS_AVAILABLE = false;
			}
			
			
			FBINIT.IMAGE_DETAILS_TASK_DONE = true;
			Log.d(TAG, "- image details download task done -");
		}
		
}


	private void updatePreference(){
		if (launchCode == Util.TAG_PHOTO_LAUNCH_CODE){
			Global.getInstance().downloadSaveToInternal(
					FBINIT.IMAGE_DETAILS.get(1).getImage(), Util.FB_TAG_GRID_IMAGE);
			
			if (Global.getInstance().prefExist(Util.FB_NUMBER_OF_TAGGED)){
				int oldTaggedPhotoSize = Global.getInstance().getIntPref(Util.FB_NUMBER_OF_TAGGED);
				if (oldTaggedPhotoSize != index && index > 0)
					Global.getInstance().modPref(Util.FB_NUMBER_OF_TAGGED, index);
			}

			else {
				Global.getInstance().modPref(Util.FB_NUMBER_OF_TAGGED, index);
			}
			Log.d(TAG, "Number of tagged photos modified");
		}
		
		if (launchCode == Util.UPLOADED_PHOTO_LAUNCH_CODE){
			Global.getInstance().downloadSaveToInternal(
					FBINIT.IMAGE_DETAILS.get(1).getImage(), Util.FB_UPLOADED_GRID_IMAGE);
		
			if (Global.getInstance().prefExist(Util.FB_NUMBER_OF_UPLOADED)){
				int oldPhotoSize = Global.getInstance().getIntPref(Util.FB_NUMBER_OF_UPLOADED);
				if (oldPhotoSize != index && index > 0)
					Global.getInstance().modPref(Util.FB_NUMBER_OF_UPLOADED, index);
			}

			else {
				Global.getInstance().modPref(Util.FB_NUMBER_OF_UPLOADED, index);
			}
			Log.d(TAG, "Number of uploaded photos modified");
		}
	}

	
}
