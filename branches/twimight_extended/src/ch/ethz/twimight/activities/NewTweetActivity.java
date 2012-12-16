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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.util.Constants;

/**
 * The activity to write a new tweet.
 * @author thossmann
 * @author pcarta
 */
public class NewTweetActivity extends TwimightBaseActivity{

	private static final String TAG = "TweetActivity";
	
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
	private String photoPath = "twimight_photos"; //path for storing photos on SDcard
	private Uri mImageCaptureUri; //uri for storing photos taken from camera
	private TextView infoTextView; //showing information
	private ImageView mImageView; //to display the photo to be uploaded
	static Context CONTEXT; 
	private boolean hasMedia = false;
	File SDcardPath = null;
	
	//SDcard checking var
	boolean isSDAvail = false;
	boolean isSDWritable = false;
	
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

		//
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
		
		//components for uploading photos
		infoTextView = (TextView) findViewById(R.id.description);
		mImageView = (ImageView) findViewById(R.id.image_view);
		
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
		photoPath = photoPath + "/" + LoginActivity.getTwitterId(this);
		infoTextView = (TextView) findViewById(R.id.description);
		mImageView = (ImageView) findViewById(R.id.image_view);
		Button uploadFromGallery = (Button) findViewById(R.id.upload_from_gallery);
		uploadFromGallery.setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				uploadFromGallery();
			}
		});
		Button uploadFromCamera = (Button) findViewById(R.id.upload_from_camera);
		uploadFromCamera.setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				uploadFromCamera();
			}		
		});
		checkSDStuff();
		
		Log.v(TAG, "onCreated");
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
			if (locHelper != null && locHelper.count > 0 && locDBHelper != null) {	
				Log.i(TAG,"writing log");
				locDBHelper.insertRow(locHelper.loc, cm.getActiveNetworkInfo().getTypeName(), ShowTweetListActivity.TWEET_WRITTEN, null, timestamp);
				locHelper.unRegisterLocationListener();
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
			tweetContentValues.put(Tweets.COL_MEDIA, mImageCaptureUri.getPath());//extra?tweetContentValues.put(Tweets.COL_MEDIA, ); the url of the media?
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
	
	//methods handling photo uploading
	
	//check SDcard availability
	private void checkSDStuff(){
		String state = Environment.getExternalStorageState();
		if(Environment.MEDIA_MOUNTED.equals(state)){
			isSDAvail = true;
			isSDWritable = true;
			SDcardPath = Environment.getExternalStoragePublicDirectory(photoPath);
			SDcardPath.mkdirs();
			//uri where the photo will be
			mImageCaptureUri = Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory(photoPath), "twimight" + String.valueOf(System.currentTimeMillis()) + ".jpg"));//photoFileParent, photoFilename));

		}
		else if(Environment.MEDIA_MOUNTED_READ_ONLY.endsWith(state)){
			isSDAvail = true;
			isSDWritable = false;
		}
		else{
			isSDAvail = false;
			isSDWritable = false;
		}
		Log.d("check", "check success");
	}
	
	//upload picture by taking a picture using camera
	private void uploadFromCamera() {
			
		Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
							
		intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, mImageCaptureUri);
		
		try {
			intent.putExtra("return-data", true);
			startActivityForResult(intent, PICK_FROM_CAMERA);
		} 
		catch (ActivityNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	//upload from local gallery
	private void uploadFromGallery(){
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(Intent.createChooser(intent, "Complete action using"), PICK_FROM_FILE);
	}
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	    if (resultCode != RESULT_OK) return;
	    Bitmap photo = null;
	    switch (requestCode) {
		    case PICK_FROM_CAMERA:
		    	
		    	//display the picture
		    	
		    	photo = decodeFile(mImageCaptureUri.getPath());
		    	infoTextView.setText(mImageCaptureUri.getPath());
		    	mImageView.setImageBitmap(photo);

		        //File f = new File(mImageCaptureUri.getPath());            
		        //if (f.exists()) f.delete();
		        break;

		    case PICK_FROM_FILE: 
		    	
		    	Uri mImageGalleryUri = data.getData();
		    	//display the picture
		    	//Uri filePathFromActivity = (Uri) extras.get(Intent.EXTRA_STREAM);
		    	mImageGalleryUri = Uri.parse(getRealPathFromUri( (Activity) NewTweetActivity.this, mImageGalleryUri));
		    	
		    	//copy the photo from gallery to SDcard

		    	String fromFile = mImageGalleryUri.getPath();
		    	String toFile = mImageCaptureUri.getPath();
				try {
					InputStream fosfrom = new FileInputStream(fromFile);
					OutputStream fosto = new FileOutputStream(toFile);
					byte bt[] = new byte[1024];
					int count;
					while ((count = fosfrom.read(bt)) > 0) {
						fosto.write(bt, 0, count);
					}
					fosfrom.close();
					fosto.close();
				} catch (Exception e) {
					// TODO: handle exception
					Log.d("copy", "file io error");
				}
				infoTextView.setText(toFile);
		    	photo = decodeFile(toFile);
		    	mImageView.setImageBitmap(photo);
		    	
		    	break;    	
	    }
	    //upload the new Tweet with the picture and refresh the TweetList to show the new Tweet
	}

	//resize the picture to fit the size of imageView
	private Bitmap decodeFile(String path){
		//Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
	    BitmapFactory.decodeFile(path,o);

	    //The new size we want to scale to
	    final int REQUIRED_SIZE=mImageView.getWidth();

	    //Find the correct scale value. It should be the power of 2.
	    int scale=1;
	    while(o.outWidth/scale/2>=REQUIRED_SIZE && o.outHeight/scale/2>=REQUIRED_SIZE)
	    	scale*=2;

	    //Decode with inSampleSize
	    BitmapFactory.Options o2 = new BitmapFactory.Options();
	    o2.inSampleSize=scale;
	    return BitmapFactory.decodeFile(path, o2);
	}
	
	//get real file path from uri for picking a picture from the gallery
	public static String getRealPathFromUri(Activity activity, Uri contentUri) {
	    String[] proj = { MediaStore.Images.Media.DATA };
	    //Cursor cursor = (new CursorLoader(CONTEXT, contentUri, proj, null, null, null)).loadInBackground();
		@SuppressWarnings("deprecation")
		Cursor cursor = activity.managedQuery(contentUri, proj, null, null, null);
	    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	    cursor.moveToFirst();
	    return cursor.getString(column_index);
	}
	

	
}
