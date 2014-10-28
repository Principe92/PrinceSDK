package prince.app.sphotos.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

import prince.app.sphotos.R;
import prince.app.sphotos.SettingsActivity;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;


public abstract class ActivityGeneric extends FragmentActivity implements 
OnItemClickListener, OnMenuItemClickListener {
	
	
	private DrawerLayout mDrawerLayout;
    protected ListView mDrawerList;
    private List<String> albumNames;
    private ActionBarDrawerToggle mDrawerToggle;
    protected static Timer noConnection;
    protected static boolean fragAdded;
    

	protected CameraX camera;
	private PopupMenu popup;
	private static final  String TAG = "GENERIC";
	
	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(getActivityLayout());
		
	    if (initDrawer()){
	    	InitializeDrawer();
	    }
	    
	    camera = new CameraX(this, getCamID(), Global.getInstance().createPath(Util.SOCIAL_PHOTOS_PATH));
	}
	
	/**
	 * Get the layout of the Activity
	 * @return {@code R.layout.example} - an xml file containing views to be drawn for the activity
	 */
	public abstract int getActivityLayout();
	
	/**
	 * Get the menu layout
	 * @return {@code R.menu.example} - an xml file containing items to be displayed in the action bar
	 */
	public abstract int getMenuLayout();
	
	/**
	 * Indicate if we want to initialize the navigation drawer
	 * @return	true - if we want the drawer to be drawn
	 */
	public abstract boolean initDrawer();
	
	/**
	 * A function called when the drawer list items are pressed
	 * @param position - The position of the currently pressed item
	 */
	protected abstract void SwitchAccount(int position);
	
	/**
	 * Get the title of the action bar
	 * @return the text to be placed on the action bar
	 */
	protected abstract String getBarTitle();
	
	/** get the camera request id */
	protected abstract int getCamID();
	
	/** get the imageView id of drawer big photo */
    protected abstract int getBigPhotoID();
    
    /** get the imageView id of drawer small photo */
    protected abstract int getSmallPhotoID();
    
    /** get the drawer big image */
    protected abstract Bitmap getBigPhoto();
    
    /** get the drawer small image */
    protected abstract Bitmap getSmallPhoto();
    
    /** get the layout id of the drawer */
    protected abstract int getDrawerLayoutID();
    
    /** get the id of the listView of the drawer */
    protected abstract int getDrawerListID();
    
    /** get id of the frameLayout */
    protected abstract int getFrameID();
    
    /** refresh the activity data */
    protected abstract void refreshGrid();
    
    protected abstract void clearCache();
	
    
    public  boolean isNetworkAvailable(){
	    ConnectivityManager connectivityManager 
	          = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
	    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}
    
    @Override
    protected void onStop(){
		super.onStop();
		if (initDrawer())
			getmDrawerLayout().closeDrawers();
	}
	
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	protected void InitializeDrawer(){
		mDrawerLayout = (DrawerLayout) findViewById(getDrawerLayoutID());
        mDrawerList = (ListView) findViewById(getDrawerListID());
        mDrawerList.setFastScrollEnabled(true);
        
        //set a custom shadow that overlays the main content when the drawer opens
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
      
        ImageView bigPhoto = (ImageView)findViewById(getBigPhotoID());
        ImageView smallPhoto = (ImageView)findViewById(getSmallPhotoID());
      /*  bigPhoto.setLayoutParams(new FrameLayout.LayoutParams(
        			getResources().getDimensionPixelSize(R.dimen.drawer_photo_width),
        			getResources().getDimensionPixelSize(R.dimen.drawer_photo_width))); */
        bigPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        
    /*    smallPhoto.setLayoutParams(new FrameLayout.LayoutParams(
    			getResources().getDimensionPixelSize(R.dimen.user_drawer_photo_width),
    			getResources().getDimensionPixelSize(R.dimen.user_drawer_photo_width))); */
        smallPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        
        bigPhoto.setImageBitmap(getBigPhoto()); 
        smallPhoto.setImageBitmap(getSmallPhoto());
        
        if (!FBINIT.isAlbumEmpty()){
        
        	//Set the adapter for the list view
        	albumNames = new ArrayList<String>();
        	int albumSize = Global.getInstance().getIntPref(Util.FB_NUMBER_OF_ALBUMS);
        	for (int i = 0; i < albumSize; i++) {
        		albumNames.add(FBINIT.sAlbumsArray.get(i).mAlbumName);
        	}
        
        	// Set the adapter for the list view
        	mDrawerList.setAdapter(new ArrayAdapter<String>(this, R.layout.fb_drawer_album_list, albumNames)); 
        }

        //Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());
        
        //enable ActionBar app icon to behave as action to toggle nav drawer

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                getmDrawerLayout(),         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer image to replace 'Up' caret */
                R.string.drawer_open,  /* "open drawer" description for accessibility */
                R.string.drawer_close  /* "close drawer" description for accessibility */
                ){
        	 public void onDrawerOpened(View view){
         		getActionBar().setTitle("Albums");
         	}
         	 
         	 public void onDrawerClosed(View view){
         		 getActionBar().setTitle(getBarTitle());
         	 }
        };
        getmDrawerLayout().setDrawerListener(mDrawerToggle);
        
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);
	}
	
	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
	    super.onPostCreate(savedInstanceState);
	    // Sync the toggle state after onRestoreInstanceState has occurred.
	    if (initDrawer())
	    	mDrawerToggle.syncState();
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	    super.onConfigurationChanged(newConfig);
	    // Pass any configuration change to the drawer toggle
	    if (initDrawer())
	    	mDrawerToggle.onConfigurationChanged(newConfig);
	}
	
	private class DrawerItemClickListener implements ListView.OnItemClickListener {
	    @Override
	    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
	        //change accounts.
	    	SwitchAccount(position);
	    	
	    	mDrawerLayout.closeDrawers();
	    }
	}
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(getMenuLayout(), menu);
		return true;
	}
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == getCamID() && resultCode == RESULT_OK) {
        	Bitmap imageBitmap = camera.getResult();
        	if (imageBitmap != null){
        		camera.galleryAddPic();
        		Toast.makeText(this,getResources().getString(R.string.text_image_saved), Toast.LENGTH_SHORT).show();
        	}
        }
        
        Log.e(TAG, "!!! Over here !!! ");
	}
	
	@Override
	public boolean onMenuItemClick(MenuItem item) {
	    int itemId = item.getItemId();
	    AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
	    
		if (itemId == R.id.action_settings) {
			// Open the settings menu
			Intent intent = new Intent();
	        intent.setClass(this, SettingsActivity.class);
	        startActivityForResult(intent, 0); 
	        Log.d(TAG, "settings called");
			return true;
		} else if (itemId == R.id.action_sync) {
			// Refresh the grid data
			refreshGrid();
			return true;
		} else if (itemId == R.id.action_details) {
			// Refresh the grid data
			refreshGrid();
			return true;
		} else if (itemId == R.id.action_clear_cache) {
			// Clear the image cache
			clearCache();
			return true;
		} else {
			return false;
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (initDrawer()){
			if (mDrawerToggle.onOptionsItemSelected(item)) {
				return true;
			}
		}
		
		int itemId = item.getItemId();
		if (itemId == R.id.action_main_camera) {
			camera.TakePicture();
			return true;
		} else if (itemId == R.id.action_main_options) {
			openSettings(); 
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}
	
	public void openSettings(){
			View view = findViewById(R.id.action_main_options);
			popup = new PopupMenu(this, view);
			popup.setOnMenuItemClickListener(this);
			MenuInflater inflater = popup.getMenuInflater();
			inflater.inflate(R.menu.fb_main_overflow, popup.getMenu());
			
			// hide logOut
			popup.getMenu().findItem(R.id.action_logout)
			.setVisible(false)
			.setEnabled(false);
			
			// show details
			popup.getMenu().findItem(R.id.action_details)
			.setVisible(true)
			.setEnabled(true);
			
			popup.show();
		}

	public DrawerLayout getmDrawerLayout() {
		return mDrawerLayout;
	}

	public void setmDrawerLayout(DrawerLayout mDrawerLayout) {
		this.mDrawerLayout = mDrawerLayout;
	}
}

