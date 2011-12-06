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

import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.util.Constants;
import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * The activity to write a new tweet.
 * @author thossmann
 *
 */
public class NewTweetActivity extends Activity implements OnClickListener{

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
	private LocationListener locationListener = new LocationListener() {
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
	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tweet);

		cancelButton = (Button) findViewById(R.id.tweet_cancel);
		cancelButton.setOnClickListener(this);
		
		sendButton = (Button) findViewById(R.id.tweet_send);
		sendButton.setOnClickListener(this);
		
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
		text.addTextChangedListener(new TextWatcher() {
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
			});
		
		// User settings: do we use location or not?
		useLocation = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefUseLocation", Constants.TWEET_DEFAULT_LOCATION);
		locationButton = (ToggleButton) findViewById(R.id.tweet_location);
		locationButton.setChecked(useLocation);
		locationButton.setBackgroundDrawable(null);
		locationButton.setOnClickListener(this);
		
		 lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		
		text.setSelection(text.getText().length());
		
		Log.i(TAG, "onCreated");
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
	 * Reacts to clicks in the UI.
	 */
	@Override
	public void onClick(View view) {
		Log.i(TAG, "onClick");
		switch (view.getId()) {		
		case R.id.tweet_cancel:
			finish();
			break;
		case R.id.tweet_send:
			sendTweet();
			break;
		case R.id.tweet_location:
			Log.i(TAG, "toggling");
			useLocation = locationButton.isChecked();
			if(useLocation){
				registerLocationListener();
			} else {
				unRegisterLocationListener();
			}
			break;
		}
	}
	
	/**
	 * Checks whether we are in disaster mode and inserts the content values into the content provider.
	 */
	private void sendTweet(){
		Log.i(TAG, "send tweet!");
		// if no connectivity, notify user that the tweet will be send later
		try{
			if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true){
				Log.i(TAG, "DISASTER TWEEEEET!!!");
				getContentResolver().insert(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER), createContentValues());
			} else {
				Log.i(TAG, "NORMAL TWEEEEET!!!");
				getContentResolver().insert(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_NORMAL), createContentValues());
				ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
				if(cm.getActiveNetworkInfo()==null || !cm.getActiveNetworkInfo().isConnected()){
					Toast.makeText(this, "No connectivity, your Tweet will be uploaded to Twitter once we have a connection!", Toast.LENGTH_LONG);
				}				
			}
			finish();
			
		} catch (Exception e) {
			Log.e(TAG, "Exception while inserting tweet into DB: " + e.toString());
			Toast.makeText(this, "There was an error inserting your tweet into the local database! Please try again.", Toast.LENGTH_LONG);
			return;
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
		tweetContentValues.put(Tweets.COL_REPLYTO, isReplyTo);
		tweetContentValues.put(Tweets.COL_FLAGS, Tweets.FLAG_TO_INSERT);

		if(useLocation){
			Location loc = getLocation();
			if(loc!=null){
				tweetContentValues.put(Tweets.COL_LAT, loc.getLatitude());
				tweetContentValues.put(Tweets.COL_LNG, loc.getLongitude());
			}
		}
		return tweetContentValues;
	}
	
	/**
	 * Starts listening to location updates
	 */
	private void registerLocationListener(){
		Log.i(TAG, "registerLocationListener");
		try{
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 50, locationListener);
			lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 50, locationListener);
		} catch(Exception e) {
			Log.i(TAG,"Can't request location Updates: " + e.toString());
			return;
		}
	}
	
	/**
	 * Stops listening to location updates
	 */
	private void unRegisterLocationListener(){
		Log.i(TAG, "UNregisterLocationListener");
		try{
			lm.removeUpdates(locationListener);
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
		if(loc!=null)
			return loc;
		else
			return lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
	}
}
