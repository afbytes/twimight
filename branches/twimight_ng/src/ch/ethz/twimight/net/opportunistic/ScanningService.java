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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Date;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Base64;

import android.app.AlarmManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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
	private static final String WAKE_LOCK = "ScanningServiceWakeLock"; 
	
	public Handler handler; /** Handler for delayed execution of the thread */
	
	// manage bluetooth communication
	public static BluetoothComms bluetoothHelper = null;

	//private Date lastScan;
			
	private MacsDBHelper dbHelper;
	
	private static Context context = null;
	
	private Cursor cursor;
	
	private Date scanStartTime;
	ConnectingTimeout connTimeout;
	ConnectionTimeout connectionTimeout;
	private static int state;
	WakeLock wakeLock;
	
	public static final int STATE_SCANNING = 1;
	public static final int STATE_IDLE=0;
	private static final long CONNECTING_TIMEOUT = 8000L;
	private static final long CONNECTION_TIMEOUT = 4000L;
	
	
	
	
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		super.onStartCommand(intent, flags, startId);
		ScanningAlarm.releaseWakeLock();
		getWakeLock(this);
		if (context == null) {
			context = this;
			handler = new Handler();		
	        // set up Bluetooth
			
	        bluetoothHelper = new BluetoothComms(mHandler);
	        bluetoothHelper.start();
			dbHelper = new MacsDBHelper(this);
			dbHelper.open();
			
			BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
			// Get a set of currently paired devices
	        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();	        
	    	
	        if (pairedDevices != null) {
	        	// If there are paired devices, add each one to the ArrayAdapter
		        if (pairedDevices.size() > 0) {
		        	
		            for (BluetoothDevice device : pairedDevices) {
		            	if (device.getBluetoothClass() != null) {
		            		if (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_SMART)
		            			dbHelper.createMac(device.getAddress().toString(), 1); 
		            	} else
		            		dbHelper.createMac(device.getAddress().toString(), 1); 
		            }
		        } 
	        }
	        
		}
		
		startScanning();
		Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler()); 		
		return START_STICKY; 
		
	}

	public static int getState() {
		return state;
	}
	public class CustomExceptionHandler implements UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread t, Throwable e) {		
			 Log.e(TAG, "error ", e);
			 
			ScanningService.this.stopSelf();
			AlarmManager mgr = (AlarmManager) LoginActivity.getInstance().getSystemService(Context.ALARM_SERVICE);
			mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000, LoginActivity.getRestartIntent());
			System.exit(2);
		}
	}
	

	/**
	 * Acquire the Wake Lock
	 * @param context
	 */
	 void getWakeLock(Context context){
		
		releaseWakeLock();
		
		PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK , WAKE_LOCK); 
		wakeLock.acquire();
	}
	
	/**
	 * We have to make sure to release the wake lock after the TDSThread is done!
	 * @param context
	 */
	 void releaseWakeLock(){
		if(wakeLock != null)
			if(wakeLock.isHeld())
				wakeLock.release();
	}

	
	/**
	 * Start the scanning.
	 * @return true if the connection with the TDS was successful, false otherwise.
	 */
	private boolean startScanning(){
		
		// Get a cursor over all "active" MACs in the DB
		cursor = dbHelper.fetchActiveMacs();
		Log.d(TAG,"active macs: " + cursor.getCount());
		
		state = STATE_SCANNING;		
		// Log the date for later rescheduling of the next scanning
		scanStartTime = new Date();
		
		if (cursor.moveToFirst()) {
            // Get the field values
            String mac = cursor.getString(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));			
            Log.i(TAG, "Connection Attempt to: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
            
            if (bluetoothHelper.getState() == bluetoothHelper.STATE_LISTEN) {
            	
            	if (dbHelper.getLastSuccessful(mac) != null) {            		
            		//if ( (System.currentTimeMillis() - dbHelper.getLastSuccessful(mac) ) > Constants.MEETINGS_INTERVAL) {
                		bluetoothHelper.connect(mac);                	
                    	connTimeout = new ConnectingTimeout();
                    	handler.postDelayed(connTimeout, CONNECTING_TIMEOUT); //timeout for the conn attempt	 	
                	//} else {
                	//	Log.i(TAG,"skipping connection, last meeting was too recent");
                	//	nextScanning();
                //	}
            	} else {
            		bluetoothHelper.connect(mac);                	
                	connTimeout = new ConnectingTimeout();
                	handler.postDelayed(connTimeout, CONNECTING_TIMEOUT); //timeout for the conn attempt	
            	}
            		
            } else if (bluetoothHelper.getState() != bluetoothHelper.STATE_CONNECTED) {
            	cursor.close();
            	bluetoothHelper.start();
            	
            }
            
            
        } else 
        	stopScanning();
        
		
		return false;
	}
	
	private class ConnectingTimeout implements Runnable {
		@Override
		public void run() {
			if (bluetoothHelper != null) {		 
				if (bluetoothHelper.getState() == BluetoothComms.STATE_CONNECTING) {				
					bluetoothHelper.start();
				}
				connTimeout = null;
			}
		}
	}
	
	private class ConnectionTimeout implements Runnable {
		@Override
		public void run() {
			if (bluetoothHelper != null) {		 
				if (bluetoothHelper.getState() == BluetoothComms.STATE_CONNECTED) {				
					bluetoothHelper.start();
				}
				connectionTimeout = null;
			}
		}
	}
	
	/**
	 * Proceed to the next MAC address
	 */
	private void nextScanning() {		
		if(cursor == null || bluetoothHelper.getState()==BluetoothComms.STATE_CONNECTED)
			stopScanning();
		else {
			// do we have another MAC in the cursor?
			if(cursor.moveToNext()){
				Log.d(TAG, "scanning for the next peer");
	            String mac = cursor.getString(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
	           // if ( (System.currentTimeMillis() - dbHelper.getLastSuccessful(mac) ) > Constants.MEETINGS_INTERVAL) { 
	            	
	            	Log.i(TAG, "Connection attempt to: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
		            bluetoothHelper.connect(mac);
		            connTimeout = new ConnectingTimeout();
	            	handler.postDelayed(connTimeout, CONNECTING_TIMEOUT); //timeout for the conn attempt	
	          //  } else {
	            	//Log.i(TAG,"skipping connection, last meeting was too recent");
	            	//nextScanning();
	           // }
			} else 
				stopScanning();
			
		}
		
	}
	
	/**
	 * Terminates one round of scanning: cleans up and reschedules next scan
	 */
	private void stopScanning() {
		cursor = null;
		state = STATE_IDLE;		
		if(isDisasterMode()){
			//long delay = Math.round(Math.random()*Constants.RANDOMIZATION_INTERVAL) - Math.round(Math.random()*Constants.RANDOMIZATION_INTERVAL);			
			// reschedule next scan (randomized)			
		   // ScanningAlarm.scheduleScanning(this,scanStartTime.getTime() + delay + Constants.SCANNING_INTERVAL);		 			
			releaseWakeLock();
			removeConnectingTimer()	;	    
			// start listening mode
			bluetoothHelper.start();
			Log.i(TAG, "Listening...");
		}
	 }
	
	
	private void removeConnectingTimer() {
		if (connTimeout != null) { // I need to remove the timeout started at the beginning
			handler.removeCallbacks(connTimeout);
			connTimeout = null;
		}
		
	}
	
	private void removeConnectionTimeout() {
		if (connectionTimeout != null) { // I need to remove the timeout started at the beginning
			handler.removeCallbacks(connectionTimeout);
			connectionTimeout = null;
		}
		
	}
	
	


	/**
	 *  The Handler that gets information back from the BluetoothService
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {          

			case Constants.MESSAGE_READ:  
				if(msg.obj.toString().equals("####CLOSING_REQUEST####")) {
					//state = CLOSING_REQ_RECEIVED; 
					Log.i(TAG,"closing request received, connection shutdown");
					bluetoothHelper.start();
					break;
				}
				
				Log.i(TAG, "tweet received");					
				
				processMessage(msg);
				getContentResolver().notifyChange(Tweets.CONTENT_URI, null);					
				break;             
				
			case Constants.MESSAGE_CONNECTION_SUCCEEDED:
				Log.i(TAG, "connection succeeded");   			
				
				removeConnectingTimer();
				connectionTimeout = new ConnectionTimeout();
            	handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT); //timeout for the conn attempt	
            	
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
				removeConnectingTimer();
				// Next scan
				nextScanning();
				break;
				
			case Constants.MESSAGE_CONNECTION_LOST:         	 
				Log.i(TAG, "connection lost");  				
				// Next scan
				removeConnectionTimeout();
				nextScanning();				
				break;
			}
			
		}		 

	};



	
	private void processMessage(Message msg) {
		
		Log.i(TAG, "inside Process Message");
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
			Log.i(TAG,"inside sending disaster tweets");
		// get disaster tweets
		Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);				
		
		if(c.getCount()>0){
			Log.i(TAG,"c.getCount():" + c.getCount());			
			c.moveToFirst();
			while(!c.isAfterLast()){
				
				if(last != null && (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED))>last)){
					JSONObject toSend;
					try {								
						toSend = getJSON(c);
						if (toSend != null) {
							Log.i(TAG,"sending...");
							Log.i(TAG, toSend.toString(5));
							bluetoothHelper.write(toSend.toString());
						}
						
						
					} catch (JSONException e) {								
						Log.e(TAG,"exception ", e);
					}					
				}
				c.moveToNext();				
			}
			bluetoothHelper.write("####CLOSING_REQUEST####");
			
		}
		else
			bluetoothHelper.write("####CLOSING_REQUEST####");
		c.close();
		
	}
	
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
		if(c.getColumnIndex(Tweets.COL_USER) < 0 || c.getColumnIndex(TwitterUsers.COL_SCREENNAME) < 0 ) {
			Log.i(TAG,"missing user data");
			return null;
		}
		
		else {
			
			o.put(Tweets.COL_USER, c.getLong(c.getColumnIndex(Tweets.COL_USER)));	
			Log.i(TAG,c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
			o.put(TwitterUsers.COL_SCREENNAME, c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
			if(c.getColumnIndex(Tweets.COL_CREATED) >=0)
				o.put(Tweets.COL_CREATED, c.getLong(c.getColumnIndex(Tweets.COL_CREATED)));
			if(c.getColumnIndex(Tweets.COL_CERTIFICATE) >=0)
				o.put(Tweets.COL_CERTIFICATE, c.getString(c.getColumnIndex(Tweets.COL_CERTIFICATE)));
			if(c.getColumnIndex(Tweets.COL_SIGNATURE) >=0)
				o.put(Tweets.COL_SIGNATURE, c.getString(c.getColumnIndex(Tweets.COL_SIGNATURE)));
		
			if(c.getColumnIndex(Tweets.COL_TEXT) >=0)
				o.put(Tweets.COL_TEXT, c.getString(c.getColumnIndex(Tweets.COL_TEXT)));		
			if(c.getColumnIndex(Tweets.COL_REPLYTO) >=0)
				o.put(Tweets.COL_REPLYTO, c.getLong(c.getColumnIndex(Tweets.COL_REPLYTO)));
			if(c.getColumnIndex(Tweets.COL_LAT) >=0)
				o.put(Tweets.COL_LAT, c.getDouble(c.getColumnIndex(Tweets.COL_LAT)));
			if(c.getColumnIndex(Tweets.COL_LNG) >=0)
				o.put(Tweets.COL_LNG, c.getDouble(c.getColumnIndex(Tweets.COL_LNG)));
			if(c.getColumnIndex(Tweets.COL_SOURCE) >=0)
				o.put(Tweets.COL_SOURCE, c.getString(c.getColumnIndex(Tweets.COL_SOURCE)));		
			if(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE) >=0)
				o.put(TwitterUsers.COL_PROFILEIMAGE, new String(Base64.encode(c.getBlob(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE)))));
			return o;
		}
			
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
		if(o.has(Tweets.COL_USER)) {
			
			cv.put(Tweets.COL_USER, o.getLong(Tweets.COL_USER));
		}
		if(o.has(Tweets.COL_REPLYTO))
			cv.put(Tweets.COL_REPLYTO, o.getLong(Tweets.COL_REPLYTO));
		if(o.has(Tweets.COL_LAT))
			cv.put(Tweets.COL_LAT, o.getDouble(Tweets.COL_LAT));
		if(o.has(Tweets.COL_LNG))
			cv.put(Tweets.COL_LNG, o.getDouble(Tweets.COL_LNG));
		if(o.has(Tweets.COL_SOURCE))
			cv.put(Tweets.COL_SOURCE, o.getString(Tweets.COL_SOURCE));
		if(o.has(TwitterUsers.COL_SCREENNAME)) {
			
			cv.put(Tweets.COL_SCREENNAME, o.getString(TwitterUsers.COL_SCREENNAME));
		}

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
		if(o.has(TwitterUsers.COL_SCREENNAME)) {
			cv.put(TwitterUsers.COL_SCREENNAME, o.getString(TwitterUsers.COL_SCREENNAME));
			
		}
		if(o.has(TwitterUsers.COL_PROFILEIMAGE))
			cv.put(TwitterUsers.COL_PROFILEIMAGE, Base64.decode(o.getString(TwitterUsers.COL_PROFILEIMAGE)));
		if(o.has(Tweets.COL_USER)) {
			cv.put(TwitterUsers.COL_ID, o.getLong(Tweets.COL_USER));
			
		}
		cv.put(TwitterUsers.COL_ISDISASTER_PEER, 1);

		return cv;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
};
