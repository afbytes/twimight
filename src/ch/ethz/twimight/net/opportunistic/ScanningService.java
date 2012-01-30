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
package ch.ethz.twimight.net.opportunistic;

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;

/**
 * This is the thread for scanning for Bluetooth peers.
 * @author theus
 * @author pcarta
 */
public class ScanningService extends Service{

	
	private static final String TAG = "ScanningService"; /** For Debugging */
	
	
	public Handler handler; /** Handler for delayed execution of the thread */
	
	// manage bluetooth communication
	static BluetoothComms bluetoothHelper = null;

	//private Date lastScan;
			
	private MacsDBHelper dbHelper;
	
	private static Context context = null;
	
	private Cursor cursor;
	
	private Date scanStartTime;
	ConnectionAttemptTimeout connTimeout;
	
	
	
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		super.onStartCommand(intent, flags, startId);
		ScanningAlarm.releaseWakeLock();
		
		if (context == null) {
			context = this;
			handler = new Handler();		
	        // set up Bluetooth
	        bluetoothHelper = new BluetoothComms(mHandler);
	        bluetoothHelper.start();
			dbHelper = new MacsDBHelper(this);
			dbHelper.open();
		}
		startScanning();
		
		return START_STICKY; 
		
	}

	
	
	/**
	 * Start the scanning.
	 * @return true if the connection with the TDS was successful, false otherwise.
	 */
	private boolean startScanning(){
		
		// Get a cursor over all "active" MACs in the DB
		cursor = dbHelper.fetchActiveMacs();
		//TODO: obtain also paired peers around
				
		// Log the date for later rescheduling of the next scanning
		scanStartTime = new Date();
		
		if (cursor.moveToFirst()) {
            // Get the field values
            String mac = cursor.getString(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));			
            Log.i(TAG, "Scanning for: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
            bluetoothHelper.connect(mac);
            connTimeout = new ConnectionAttemptTimeout();
			handler.postDelayed(connTimeout, 10000); //timeout for the conn attempt	 	
            
        } else {
        	stopScanning();
        }
		
		return false;
	}
	
	class ConnectionAttemptTimeout implements Runnable {
		@Override
		public void run() {
			if (bluetoothHelper != null) {		 
				if (bluetoothHelper.getState() == BluetoothComms.STATE_CONNECTING) {				
					bluetoothHelper.cancelConnectionAttempt();
				}
				connTimeout = null;
			}
		}
	}
	
	/**
	 * Proceed to the next MAC address
	 */
	private void nextScanning() {
		if(cursor == null || bluetoothHelper.getState()==BluetoothComms.STATE_CONNECTED){
			stopScanning();
		} else {
			// do we have another MAC in the cursor?
			if(cursor.moveToNext()){
	            String mac = cursor.getString(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
	            Log.i(TAG, "Scanning for: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
	            bluetoothHelper.connect(mac);
			} else {
				stopScanning();
			}
		}
		
	}
	
	/**
	 * Terminates one round of scanning: cleans up and reschedules next scan
	 */
	private void stopScanning() {
		cursor = null;
		
		if(isDisasterMode()){
			//bluetoothHelper.stop();
			long delay = Math.round(Math.random()*Constants.RANDOMIZATION_INTERVAL) - Math.round(Math.random()*Constants.RANDOMIZATION_INTERVAL);
			
			// reschedule next scan (randomized)			
		    ScanningAlarm.scheduleScanning(this,scanStartTime.getTime() + delay + Constants.SCANNING_INTERVAL);		 			
			
		    if (connTimeout != null) {
				handler.removeCallbacks(connTimeout);
				connTimeout = null;
			}
		    
			// start listening mode
			bluetoothHelper.start();
			Log.i(TAG, "Listening...");
		}
	 }
	
	
	/**
	 * Cancel all Bluetooth actions
	 */
	public void stopOperation(){
		bluetoothHelper.stop();
	}

	/**
	 *  The Handler that gets information back from the BluetoothService
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {          

			case Constants.MESSAGE_READ:  
				
				try {
					Log.i(TAG, new JSONObject(msg.obj.toString()).toString());
				} catch (JSONException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
				}				 
				
				processMessage(msg);
				
				break;             
				
			case Constants.MESSAGE_CONNECTION_SUCCEEDED:
				Log.i(TAG, "connection succeeded");   
				
				if (connTimeout != null) { // I need to remove the timeout started at the beginning
					handler.removeCallbacks(connTimeout);
					connTimeout = null;
				}
				
				// Insert successful connection into DB
				dbHelper.updateMacSuccessful(msg.obj.toString(), 1);
				
				// Here starts the protocol for Tweet exchange.
				Long last = dbHelper.getLastSuccessful(msg.obj.toString());
				sendDisasterTweets(last);				
				dbHelper.setLastSuccessful(msg.obj.toString(), new Date());				
									
				
				break;   
			case Constants.MESSAGE_CONNECTION_FAILED:             
				Log.i(TAG, "connection failed");
				
				// Insert failed connection into DB
				dbHelper.updateMacAttempts(msg.obj.toString(), 1);
				
				// Next scan
				nextScanning();
				break;
				
			case Constants.MESSAGE_CONNECTION_LOST:         	 
				Log.i(TAG, "connection lost");  				
				// Next scan
				nextScanning();
				
				break;
			}
			
		}

		private void processMessage(Message msg) {
			try {
				ContentValues cvTweet = getTweetCV(msg.obj.toString());
				cvTweet.put(Tweets.COL_BUFFER, Tweets.BUFFER_DISASTER);
				
				// we don't enter our own tweets into the DB.
				if(cvTweet.getAsLong(Tweets.COL_USER).toString().equals(LoginActivity.getTwitterId(context))){
					Log.i(TAG, "we received our own tweet");
				} else {
					ContentValues cvUser = getUserCV(msg.obj.toString());
					
					// insert the tweet
					Uri insertUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);
					getContentResolver().insert(insertUri, cvTweet);
					
					// insert the user
					Uri insertUserUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS);
					getContentResolver().insert(insertUserUri, cvUser);
				}
				
			} catch (JSONException e1) {
				Log.e(TAG, "Exception while receiving disaster tweet " , e1);
			}
			
		}

		private void sendDisasterTweets(Long last) {
			// get disaster tweets
			Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);				
			
			if(c.getCount()>0){
				c.moveToFirst();
				while(!c.isAfterLast()){
					
					if(last != null && (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED))>last)){
						JSONObject toSend;
						try {								
							toSend = getJSON(c);
							Log.i(TAG,"sending...");
							Log.i(TAG, toSend.toString(5));
							bluetoothHelper.write(toSend.toString());
							
						} catch (JSONException e) {								
							Log.e(TAG,"exception ", e);
						}
						
					}
					c.moveToNext();
				}
				
			}
			c.close();
			
		}

	};
	
	/**
	 * True if the disaster mode is on
	 */
	private boolean isDisasterMode(){
		return (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true);
	}

	/**
	 * Creates a JSON Object from a Tweet
	 * TODO: Move this where it belongs!
	 * @param c
	 * @return
	 * @throws JSONException 
	 */
	protected JSONObject getJSON(Cursor c) throws JSONException {
		JSONObject o = new JSONObject();
		if(c.getColumnIndex(Tweets.COL_CERTIFICATE) >=0)
			o.put(Tweets.COL_CERTIFICATE, c.getString(c.getColumnIndex(Tweets.COL_CERTIFICATE)));
		if(c.getColumnIndex(Tweets.COL_SIGNATURE) >=0)
			o.put(Tweets.COL_SIGNATURE, c.getString(c.getColumnIndex(Tweets.COL_SIGNATURE)));
		if(c.getColumnIndex(Tweets.COL_CREATED) >=0)
			o.put(Tweets.COL_CREATED, c.getLong(c.getColumnIndex(Tweets.COL_CREATED)));
		if(c.getColumnIndex(Tweets.COL_TEXT) >=0)
			o.put(Tweets.COL_TEXT, c.getString(c.getColumnIndex(Tweets.COL_TEXT)));
		if(c.getColumnIndex(Tweets.COL_USER) >=0)
			o.put(Tweets.COL_USER, c.getLong(c.getColumnIndex(Tweets.COL_USER)));
		if(c.getColumnIndex(Tweets.COL_REPLYTO) >=0)
			o.put(Tweets.COL_REPLYTO, c.getLong(c.getColumnIndex(Tweets.COL_REPLYTO)));
		if(c.getColumnIndex(Tweets.COL_LAT) >=0)
			o.put(Tweets.COL_LAT, c.getDouble(c.getColumnIndex(Tweets.COL_LAT)));
		if(c.getColumnIndex(Tweets.COL_LNG) >=0)
			o.put(Tweets.COL_LNG, c.getDouble(c.getColumnIndex(Tweets.COL_LNG)));
		if(c.getColumnIndex(Tweets.COL_SOURCE) >=0)
			o.put(Tweets.COL_SOURCE, c.getString(c.getColumnIndex(Tweets.COL_SOURCE)));
		if(c.getColumnIndex(TwitterUsers.COL_SCREENNAME) >=0)
			o.put(TwitterUsers.COL_SCREENNAME, c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
		if(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE) >=0)
			o.put(TwitterUsers.COL_PROFILEIMAGE, new String(Base64.encode(c.getBlob(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE)))));
		return o;
	}
	
	/**
	 * Creates content values for a Tweet from a JSON object
	 * TODO: Move this to where it belongs
	 * @param o
	 * @return
	 * @throws JSONException
	 */
	protected ContentValues getTweetCV(String msgString) throws JSONException{
		JSONObject o = new JSONObject(msgString);
		ContentValues cv = new ContentValues();
		if(o.has(Tweets.COL_CERTIFICATE))
			cv.put(Tweets.COL_CERTIFICATE, o.getString(Tweets.COL_CERTIFICATE));
		if(o.has(Tweets.COL_SIGNATURE))
			cv.put(Tweets.COL_SIGNATURE, o.getString(Tweets.COL_SIGNATURE));
		if(o.has(Tweets.COL_CREATED))
			cv.put(Tweets.COL_CREATED, o.getLong(Tweets.COL_CREATED));
		if(o.has(Tweets.COL_TEXT))
			cv.put(Tweets.COL_TEXT, o.getString(Tweets.COL_TEXT));
		if(o.has(Tweets.COL_USER))
			cv.put(Tweets.COL_USER, o.getLong(Tweets.COL_USER));
		if(o.has(Tweets.COL_REPLYTO))
			cv.put(Tweets.COL_REPLYTO, o.getLong(Tweets.COL_REPLYTO));
		if(o.has(Tweets.COL_LAT))
			cv.put(Tweets.COL_LAT, o.getDouble(Tweets.COL_LAT));
		if(o.has(Tweets.COL_LNG))
			cv.put(Tweets.COL_LNG, o.getDouble(Tweets.COL_LNG));
		if(o.has(Tweets.COL_SOURCE))
			cv.put(Tweets.COL_SOURCE, o.getString(Tweets.COL_SOURCE));

		return cv;
	}
	
	/**
	 * Creates content values for a User from a JSON object
	 * TODO: Move this to where it belongs
	 * @param o
	 * @return
	 * @throws JSONException
	 */
	protected ContentValues getUserCV(String msgString) throws JSONException{
		JSONObject o = new JSONObject(msgString);

		// create the content values for the user
		ContentValues cv = new ContentValues();
		if(o.has(TwitterUsers.COL_SCREENNAME))
			cv.put(TwitterUsers.COL_SCREENNAME, o.getString(TwitterUsers.COL_SCREENNAME));
		if(o.has(TwitterUsers.COL_PROFILEIMAGE))
			cv.put(TwitterUsers.COL_PROFILEIMAGE, Base64.decode(o.getString(TwitterUsers.COL_PROFILEIMAGE)));
		if(o.has(Tweets.COL_USER))
			cv.put(TwitterUsers.COL_ID, o.getLong(Tweets.COL_USER));

		return cv;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
};
