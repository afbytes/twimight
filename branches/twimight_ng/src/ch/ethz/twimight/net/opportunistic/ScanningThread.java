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

import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * This is the thread for scanning for Bluetooth peers.
 * @author theus
 *
 */
public class ScanningThread implements Runnable{

	
	private static final String TAG = "ScanningThread"; /** For Debugging */
	
	
	public Handler handler; /** Handler for delayed execution of the thread */
	
	// manage bluetooth communication
	static BluetoothComms bluetoothHelper = null;

	//private Date lastScan;
			
	private MacsDBHelper dbHelper;
	
	private Context context;
	
	private Cursor cursor;
	
	private Date scanStartTime;
	
	private static ScanningThread instance;
	
	/**
	 * Constructor
	 */
	public ScanningThread(Context context){
		this.context = context;
		handler = new Handler();
		
        // set up Bluetooth
        bluetoothHelper = new BluetoothComms(this.context, mHandler);
		
		dbHelper = new MacsDBHelper(this.context);
		dbHelper.open();
		
	}
	
	public static ScanningThread getInstance(Context context){

		if(instance == null){
			instance = new ScanningThread(context);
		}
		return instance;
	}
	
	/**
	 * The run function of the thread. Triggers and reschedules Bluetooth connection attempts.
	 */
	@Override
	public void run() {

		ScanningService.stopScanning();
		
		startScanning();
		
	}
	
	/**
	 * Start the scanning.
	 * @return true if the connection with the TDS was successful, false otherwise.
	 */
	private boolean startScanning(){
		
		// Get a cursor over all "active" MACs in the DB
		cursor = dbHelper.fetchActiveMacs();
		
		
		// Stop listening mode
		bluetoothHelper.stop();
		
		// Log the date for later rescheduling of the next scanning
		scanStartTime = new Date();
		
		if (cursor.moveToFirst()) {
            // Get the field values
            long mac = cursor.getLong(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
            Log.i(TAG, "Scanning for: " + dbHelper.long2mac(mac) + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
            bluetoothHelper.connect(dbHelper.long2mac(mac));
            
        } else {
        	stopScanning();
        }
		
		return false;
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
	            long mac = cursor.getLong(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
	            Log.i(TAG, "Scanning for: " + dbHelper.long2mac(mac) + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
	            bluetoothHelper.connect(dbHelper.long2mac(mac));
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
			bluetoothHelper.stop();
			
			// reschedule next scan (randomized)
			if(scanStartTime == null || scanStartTime.getTime() + Constants.SCANNING_INTERVAL - System.currentTimeMillis() < Constants.MIN_LISTEN_TIME){
				ScanningService.scheduleScanning(Constants.MIN_LISTEN_TIME);
			} else {
				long delay = Math.round(Math.random()*Constants.RANDOMIZATION_INTERVAL) - Math.round(0.5*Constants.RANDOMIZATION_INTERVAL);
				ScanningService.scheduleScanning(scanStartTime.getTime() + Constants.SCANNING_INTERVAL - System.currentTimeMillis() + delay);
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
				Log.i(TAG, msg.obj.toString());
				
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
						context.getContentResolver().insert(insertUri, cvTweet);
						
						// insert the user
						Uri insertUserUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS);
						context.getContentResolver().insert(insertUserUri, cvUser);
					}
					
				} catch (JSONException e1) {
					Log.i(TAG, "Exception while receiving disaster tweet " + e1);
				}
				break;             
				
			case Constants.MESSAGE_CONNECTION_SUCCEEDED:
				Log.i(TAG, "connection succeeded");   
				
				// Cancel future scans
				ScanningService.stopScanning();
				
				// Insert successful connection into DB
				dbHelper.updateMacSuccessful(dbHelper.mac2long(msg.obj.toString()), 1);
				
				// Here starts the protocol for Tweet exchange.
				Long last = dbHelper.getLastSuccessful(dbHelper.mac2long(msg.obj.toString()));
				
				// get disaster tweets
				Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);
				Cursor c = context.getContentResolver().query(queryUri, null, null, null, null);
				
				try{
					if(c.getCount()>0){
						c.moveToFirst();
						while(!c.isAfterLast()){
							if(last != null && (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED))>last)){
								JSONObject toSend = getJSON(c);
								Log.i(TAG, toSend.toString(5));
								bluetoothHelper.write(toSend.toString());
							}
							c.moveToNext();
						}
					}
					dbHelper.setLastSuccessful(dbHelper.mac2long(msg.obj.toString()), new Date());
				} catch(Exception e){
					Log.i(TAG, "Exception while sending disaster tweets " + e);
				} finally {
					c.close();
				}
				
				
				if(isDisasterMode()){
					ScanningService.scheduleScanning(Math.round(Math.random()*2*Constants.SCANNING_INTERVAL));
				}
				break;   
			case Constants.MESSAGE_CONNECTION_FAILED:             
				Log.i(TAG, "connection failed");
				
				// Insert failed connection into DB
				dbHelper.updateMacAttempts(dbHelper.mac2long(msg.obj.toString()), 1);
				
				// Next scan
				nextScanning();
				break;
				
			case Constants.MESSAGE_CONNECTION_LOST:         	 
				Log.i(TAG, "connection lost");   
				if(isDisasterMode()){
					ScanningService.scheduleScanning(Math.round(Math.random()*2*Constants.SCANNING_INTERVAL));
					bluetoothHelper.start();
				}
				
				break;
			}
			
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
	
};
