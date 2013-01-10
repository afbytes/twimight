/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/

package ch.ethz.twimight.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.SDCardHelper;

/**
 * The activity to write a new tweet.
 * @author thossmann
 * @author pcarta
 */
public class NewTweetActivity extends TwimightBaseActivity{

	private static final String TAG = "TweetActivity";
	private static Context CONTEXT; 
	private boolean useLocation;
	private EditText text;
	private TextView characters;
	private Button cancelButton;
	private Button sendButton;
	
	private long isReplyTo;
	
	// the following are all to deal with location
	private ToggleButton locationButton;
	private Location loc;
	private LocationManager lm;
	private LocationListener locationListener;
	
	private TextWatcher textWatcher;
	
	//uploading photos
	private static final int PICK_FROM_CAMERA = 1;
	private static final int PICK_FROM_FILE = 2;
	private String tmpPhotoPath; //path storing photos on SDcard
	private String finalPhotoPath; //path storing photos on SDcard
	private String finalPhotoName; //file name of uploaded photo
	public static final String PHOTO_PATH = "twimight_photos";
	private Uri tmpPhotoUri; //uri storing temp photos
	private Uri photoUri; //uri storing photos
	private ImageView mImageView; //to display the photo to be uploaded

	private boolean hasMedia = false;
	private ImageButton uploadFromGallery;
	private ImageButton uploadFromCamera;
	private ImageButton deletePhoto;
	private ImageButton previewPhoto;
	private ImageButton photoButton;
	private Bitmap photo = null;
	private LinearLayout photoLayout;
	
	//SDcard helper
	private SDCardHelper sdCardHelper;
	
	//LOGS
		LocationHelper locHelper ;
		long timestamp;		
		ConnectivityManager cm;
		StatisticsDBHelper locDBHelper;	
	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tweet);
		CONTEXT = this;
		//Statistics
		locDBHelper = new StatisticsDBHelper(this);
		locDBHelper.open();
		cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);		
		locHelper = new LocationHelper(this);
		
		//SDCard helper
		sdCardHelper = new SDCardHelper(CONTEXT);
		
		cancelButton = (Button) findViewById(R.id.tweet_cancel);
		cancelButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				
				finish();		
			}
			
		});
		
		sendButton = (Button) findViewById(R.id.tweet_send);
		sendButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				new SendTweetTask().execute();				
			}
			
		});
		
		characters = (TextView) findViewById(R.id.tweet_characters);
		characters.setText(Integer.toString(Constants.TWEET_LENGTH));
		
		text = (EditText) findViewById(R.id.tweetText);
		
		
		// Did we get some extras in the intent?
		Intent i = getIntent();
		if(i.hasExtra("text")){
			text.setText(Html.fromHtml("<i>"+i.getStringExtra("text")+"</i>"));
		}
		if(text.getText().length()==0){
			sendButton.setEnabled(false);
		}
		
		if(text.getText().length()>Constants.TWEET_LENGTH){
			text.setText(text.getText().subSequence(0, Constants.TWEET_LENGTH));
			text.setSelection(text.getText().length());
    		characters.setTextColor(Color.RED);
		}
		
		characters.setText(Integer.toString(Constants.TWEET_LENGTH-text.getText().length()));

		if(i.hasExtra("isReplyTo")){
			isReplyTo = i.getLongExtra("isReplyTo", 0);
		}
		
		// This makes sure we do not enter more than 140 characters	
		textWatcher = new TextWatcher(){
		    public void afterTextChanged(Editable s){
		    	int nrCharacters = Constants.TWEET_LENGTH-text.getText().length();
		    	
		    	if(nrCharacters < 0){
		    		text.setText(text.getText().subSequence(0, Constants.TWEET_LENGTH));
		    		text.setSelection(text.getText().length());
		    		nrCharacters = Constants.TWEET_LENGTH-text.getText().length();
		    	}
		    	
		    	if(nrCharacters <= 0){
		    		characters.setTextColor(Color.RED);
		    	} else {
		    		characters.setTextColor(Color.BLACK);
		    	}
		    	
		    	if(nrCharacters == Constants.TWEET_LENGTH){
		    		sendButton.setEnabled(false);
		    	} else {
		    		sendButton.setEnabled(true);
		    	}
		    	
		    	characters.setText(Integer.toString(nrCharacters));
		    	
		    }
		    public void  beforeTextChanged(CharSequence s, int start, int count, int after){}
		    public void  onTextChanged (CharSequence s, int start, int before,int count) {} 
		};
		text.addTextChangedListener(textWatcher);
		text.setSelection(text.getText().length());
		

		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {
				
				if(loc == null || !loc.hasAccuracy()){
					loc = location;
				} else if(location.hasAccuracy() && location.getAccuracy() < loc.getAccuracy()){
					loc = location;
				}
			}

			public void onProviderDisabled(String provider) {}
			public void onProviderEnabled(String provider) {}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
		};
		
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		// User settings: do we use location or not?
		useLocation = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefUseLocation", Constants.TWEET_DEFAULT_LOCATION);
		locationButton = (ToggleButton) findViewById(R.id.tweet_location);
		locationButton.setChecked(useLocation);		
		locationButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				useLocation = locationButton.isChecked();
				if(useLocation){
					registerLocationListener();
				} else {
					unRegisterLocationListener();
				}
			}
		});
		
		
		//uploading photos
		tmpPhotoPath = PHOTO_PATH + "/" + "tmp";
		finalPhotoPath = PHOTO_PATH + "/" + LoginActivity.getTwitterId(this);
		mImageView = new ImageView(this);
		
		photoLayout = (LinearLayout) findViewById(R.id.linearLayout_photo_view);
		photoLayout.setVisibility(View.GONE);
		
		uploadFromGallery = (ImageButton) findViewById(R.id.upload_from_gallery);
		uploadFromGallery.setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				uploadFromGallery();
			}
		});
		
		uploadFromCamera = (ImageButton) findViewById(R.id.upload_from_camera);
		uploadFromCamera.setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				uploadFromCamera();
			}
		});
		
		previewPhoto = (ImageButton) findViewById(R.id.preview_photo);
		previewPhoto.setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				
				mImageView = new ImageView(CONTEXT);
				mImageView.setImageBitmap(photo);
				AlertDialog.Builder photoPreviewDialog = new AlertDialog.Builder(CONTEXT);
				photoPreviewDialog.setView(mImageView);
				photoPreviewDialog.setNegativeButton("close",null);
				photoPreviewDialog.show();
				
			}
		});
		
		deletePhoto = (ImageButton) findViewById(R.id.delete_photo);
		deletePhoto.setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				
				sdCardHelper.deleteFile(tmpPhotoUri.getPath());
				hasMedia = false;
				setButtonStatus(true,false);
			}		
		});
		
		photoButton = (ImageButton) findViewById(R.id.tweet_photo);		
		photoButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				if(photoLayout.getVisibility() == View.GONE){
					photoLayout.setVisibility(View.VISIBLE);
				}
				else{
					photoLayout.setVisibility(View.GONE);
				}
			}
		});
		
		String[] filePaths = {tmpPhotoPath, finalPhotoPath};
		if(sdCardHelper.checkSDStuff(filePaths)){
			
			sdCardHelper.clearTempDirectory(tmpPhotoPath);
			setButtonStatus(true,false);
		}
		else setButtonStatus(false,false);

		Log.v(TAG, "onCreated");
	}

	/**
	 * set button status with different operations
	 * 
	 * @param statusUpload
	 * @param statusDelete
	 */
	private void setButtonStatus(boolean statusUpload, boolean statusDelete){
		uploadFromGallery.setEnabled(statusUpload);
		uploadFromCamera.setEnabled(statusUpload);
		deletePhoto.setEnabled(statusDelete);
		previewPhoto.setEnabled(statusDelete);
	}
	
	/**
	 * onResume
	 */
	@Override
	public void onResume(){
		super.onResume();
		if(useLocation){
			registerLocationListener();
		}
	}
	
	/**
	 * onPause
	 */
	@Override
	public void onPause(){
		super.onPause();
		unRegisterLocationListener();
	}
	
	/**
	 * On Destroy
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if(hasMedia){
			sdCardHelper.deleteFile(tmpPhotoUri.getPath());
			hasMedia = false;
		}
		if (locHelper!= null) 
			locHelper.unRegisterLocationListener();	
		
		locationButton.setOnClickListener(null);
		locationButton = null;
		locationListener = null;
		lm = null;
		
		cancelButton.setOnClickListener(null);
		cancelButton = null;
		
		sendButton.setOnClickListener(null);
		sendButton = null;
		
		text.removeTextChangedListener(textWatcher);
		text = null;
		textWatcher = null;
		
		unbindDrawables(findViewById(R.id.showNewTweetRoot));
	}
	
	/**	
	 * Checks whether we are in disaster mode and inserts the content values into the content provider.
	 *
	 * @author pcarta
	 *
	 */
	private class SendTweetTask extends AsyncTask<Void, Void, Boolean>{
		
		Uri insertUri = null;
		
		@Override
		protected Boolean doInBackground(Void... params) {
			boolean result=false;
			
			timestamp = System.currentTimeMillis();
			/*if (locHelper != null && locHelper.count > 0 && locDBHelper != null) {	
				Log.i(TAG,"writing log");
				locDBHelper.insertRow(locHelper.loc, cm.getActiveNetworkInfo().getTypeName(), ShowTweetListActivity.TWEET_WRITTEN, null, timestamp);
				locHelper.unRegisterLocationListener();
				Log.i(TAG, String.valueOf(hasMedia));
			}*/
			if(hasMedia){
				try {
					finalPhotoName = "twimight" + String.valueOf(timestamp) + ".jpg";
					photoUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(finalPhotoPath, finalPhotoName));//photoFileParent, photoFilename));
					String fromFile = tmpPhotoUri.getPath();
					String toFile = photoUri.getPath();
					Log.i(TAG, fromFile);
					Log.i(TAG, toFile);
					if(sdCardHelper.copyFile(fromFile, toFile)){

						Log.i(TAG, "file copy successful");

					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					Log.d("photo", "exception!!!!!");
					e.printStackTrace();
				}
			}
			// if no connectivity, notify user that the tweet will be send later		
				
				ContentValues cv = createContentValues(); 
				
				if(PreferenceManager.getDefaultSharedPreferences(NewTweetActivity.this).getBoolean("prefDisasterMode", false) == true){				

					// our own tweets go into the my disaster tweets buffer
					cv.put(Tweets.COL_BUFFER, Tweets.BUFFER_TIMELINE|Tweets.BUFFER_MYDISASTER);

					insertUri = getContentResolver().insert(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
																+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER), cv);
					getContentResolver().notifyChange(Tweets.CONTENT_URI, null);
				} else {				
					
					// our own tweets go into the timeline buffer
					cv.put(Tweets.COL_BUFFER, Tweets.BUFFER_TIMELINE);

					insertUri = getContentResolver().insert(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + 
																Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_NORMAL), cv);
					getContentResolver().notifyChange(Tweets.CONTENT_URI, null);
					//getContentResolver().notifyChange(insertUri, null);
					ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
					if(cm.getActiveNetworkInfo()==null || !cm.getActiveNetworkInfo().isConnected()){
						result=true;
					}
				}

					
				return result;
			
		}

		@Override
		protected void onPostExecute(Boolean result){
			if (result)
				Toast.makeText(NewTweetActivity.this, "No connectivity, your Tweet will be uploaded to Twitter once we have a connection!", Toast.LENGTH_SHORT).show();
			
			if(insertUri != null){
				// schedule the tweet for uploading to twitter
				Intent i = new Intent(NewTweetActivity.this, TwitterService.class);
				i.putExtra("synch_request", TwitterService.SYNCH_TWEET);
				i.putExtra("rowId", new Long(insertUri.getLastPathSegment()));
				startService(i);
			}
			finish();
		}
	}
	
	
	
	/**
	 * Prepares the content values of the tweet for insertion into the DB.
	 * @return
	 */
	private ContentValues createContentValues() {
		ContentValues tweetContentValues = new ContentValues();
		
		tweetContentValues.put(Tweets.COL_TEXT, text.getText().toString());
		tweetContentValues.put(Tweets.COL_USER, LoginActivity.getTwitterId(this));
		tweetContentValues.put(Tweets.COL_SCREENNAME, LoginActivity.getTwitterScreenname(this));
		if (isReplyTo > 0) {
			tweetContentValues.put(Tweets.COL_REPLYTO, isReplyTo);
		}
		
		// we mark the tweet for posting to twitter
		tweetContentValues.put(Tweets.COL_FLAGS, Tweets.FLAG_TO_INSERT);
		
		if(useLocation){
			Location loc = getLocation();
			if(loc!=null){
				tweetContentValues.put(Tweets.COL_LAT, loc.getLatitude());
				tweetContentValues.put(Tweets.COL_LNG, loc.getLongitude());
			}
		}
		//if there is a photo, put the path of photo in the cv
		if (hasMedia){
			tweetContentValues.put(Tweets.COL_MEDIA, finalPhotoName);
			Log.i(TAG, Tweets.COL_MEDIA + ":" + finalPhotoName);
		}
		
		return tweetContentValues;
	}
	
	/**
	 * Starts listening to location updates
	 */
	private void registerLocationListener(){
		try{
			if ((lm != null) && (locationListener != null)) {
				lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 40, locationListener);
				lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 200, locationListener);
			}
		} catch(Exception e) {
			Log.i(TAG,"Can't request location Updates: " + e.toString());
			return;
		}
	}
	
	/**
	 * Stops listening to location updates
	 */
	private void unRegisterLocationListener(){
		try{
			if ((lm != null) && (locationListener != null)) {
		        lm.removeUpdates(locationListener);
		        Log.i(TAG, "unregistered updates");
		    }
		} catch(Exception e) {
			Log.i(TAG,"Can't unregister location listener: " + e.toString());
			return;
		}
	}
	
	/**
	 * Tries to get a location from the listener if that was successful or the last known location otherwise.
	 * @return
	 */
	private Location getLocation(){
		if(loc!=null){
			return loc;
		}else{
			if ((lm != null)) {
				return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
			}
		}
		return null;
	}
	
	
	
	
	
	//methods photo uploading
	
	/**
	 * upload photo from camera
	 */
	private void uploadFromCamera() {
		
		if((tmpPhotoUri = sdCardHelper.createTmpPhotoStoragePath(tmpPhotoPath)) != null){
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			
			intent.putExtra(MediaStore.EXTRA_OUTPUT, tmpPhotoUri);
			
			try {
				intent.putExtra("return-data", true);
				startActivityForResult(intent, PICK_FROM_CAMERA);
			} 
			catch (ActivityNotFoundException e) {
				e.printStackTrace();
			}
		}
		else{
			Log.i(TAG, "path for storing photos cannot be created!");
			setButtonStatus(false, false);
		}
		
	}
	
	/**
	 * upload photo by taking a picture
	 */
	private void uploadFromGallery(){
		if((tmpPhotoUri = sdCardHelper.createTmpPhotoStoragePath(tmpPhotoPath)) != null){
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(intent, "Complete action using"), PICK_FROM_FILE);
		}
		else{
			Log.i(TAG, "path for storing photos cannot be created!");
			setButtonStatus(false, false);
		}
		
	}
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (resultCode != RESULT_OK) return;
	    setButtonStatus(false,true);
	    hasMedia = true;
	    switch (requestCode) {
		    case PICK_FROM_CAMERA:
		    	
		    	//display the picture		    	
		    	photo = sdCardHelper.decodeBitmapFile(tmpPhotoUri.getPath());
		    	mImageView.setImageBitmap(photo);

		    	break;

		    case PICK_FROM_FILE: 
		    	
		    	//display the photo
		    	Uri mImageGalleryUri = data.getData();
		    	
		    	//get the real path for chosen photo
		    	mImageGalleryUri = Uri.parse(sdCardHelper.getRealPathFromUri( (Activity) NewTweetActivity.this, mImageGalleryUri));
		    	
		    	//copy the photo from gallery to tmp directory

		    	String fromFile = mImageGalleryUri.getPath();
		    	String toFile = tmpPhotoUri.getPath();
				if(sdCardHelper.copyFile(fromFile, toFile)){
			    	photo = sdCardHelper.decodeBitmapFile(toFile);
			    	mImageView.setImageBitmap(photo);
				}
		    	break;    	
	    }
	}
	
}
