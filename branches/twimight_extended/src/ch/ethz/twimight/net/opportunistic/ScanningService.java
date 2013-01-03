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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Date;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.net.twitter.DirectMessages;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.SDCardHelper;

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
	
	
	ConnectingTimeout connTimeout;
	ConnectionTimeout connectionTimeout;
	private static int state;
	WakeLock wakeLock;
	public boolean closing_request_sent = false;
		
	public static final int STATE_SCANNING = 1;
	public static final int STATE_IDLE=0;
	private static final long CONNECTING_TIMEOUT = 8000L;
	private static final long CONNECTION_TIMEOUT = 4000L;
	
	private static final String TYPE = "message_type";
	public static final int TWEET=0;
	public static final int DM=1;
	public static final int PHOTO=2;
	
	//photo
	private String photoPath;
	private final String PHOTO_PATH = "twimight_photos";
	//SDcard helper
	private SDCardHelper sdCardHelper;
	//SDcard checking var
	boolean isSDAvail = false;
	boolean isSDWritable = false;	
	File SDcardPath = null;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		super.onStartCommand(intent, flags, startId);
		Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler()); 	
		
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
	        
		}
		
		//sdCard helper
		sdCardHelper = new SDCardHelper(context);
		
		BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		// Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();	      
    	
        if (pairedDevices != null) {
        	// If there are paired devices, add each one to the ArrayAdapter
	        if (pairedDevices.size() > 0) {	        	
	            for (BluetoothDevice device : pairedDevices) {	            	
	            		dbHelper.createMac(device.getAddress().toString(), 1); 
	            }
	        } 
        }	    	
		startScanning();			
		return START_STICKY; 
		
	}

	public static int getState() {
		return state;
	}
	public class CustomExceptionHandler implements UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread t, Throwable e) {		
			 Log.e(TAG, "error ", e);
			context= null; 
			ScanningService.this.stopSelf();
			AlarmManager mgr = (AlarmManager) LoginActivity.getInstance().getSystemService(Context.ALARM_SERVICE);
			mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() , LoginActivity.getRestartIntent());
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

	
	@Override
	public void onDestroy() {
		Log.i(TAG,"on Destroy");
		context=null;
		releaseWakeLock();
		super.onDestroy();
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
		
		if (cursor.moveToFirst()) {
            // Get the field values
            String mac = cursor.getString(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));			
            Log.i(TAG, "Connection Attempt to: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
            
            if (bluetoothHelper.getState() == bluetoothHelper.STATE_LISTEN) {            	

            	//if ( (System.currentTimeMillis() - dbHelper.getLastSuccessful(mac) ) > Constants.MEETINGS_INTERVAL) {
            		bluetoothHelper.connect(mac);                	
            		connTimeout = new ConnectingTimeout();
            		handler.postDelayed(connTimeout, CONNECTING_TIMEOUT); //timeout for the conn attempt	 	
            	//} else {
            		//Log.i(TAG,"skipping connection, last meeting was too recent");
            	//	nextScanning();
            	//}
            } else if (bluetoothHelper.getState() != bluetoothHelper.STATE_CONNECTED) {            	
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
					
					//synchronized(this){
						//if (closing_request_sent) {
							
							Log.d(TAG,"closing request received, connection shutdown");
							bluetoothHelper.start();
							//closing_request_sent = false;
						//}
					//}				
					break;
					
				} else {
					new ProcessDataReceived().execute(msg.obj.toString());	//not String, object instead							
					break; 
				}	            
				
			case Constants.MESSAGE_CONNECTION_SUCCEEDED:
				Log.i(TAG, "connection succeeded");   			
				
				removeConnectingTimer();
				connectionTimeout = new ConnectionTimeout();
            	handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT); //timeout for the conn attempt	
            	
				// Insert successful connection into DB
				dbHelper.updateMacSuccessful(msg.obj.toString(), 1);
				
				// Here starts the protocol for Tweet exchange.
				Long last = dbHelper.getLastSuccessful(msg.obj.toString());
				//new SendDisasterData(msg.obj.toString()).execute(last);				
				sendDisasterTweets(last);
				sendDisasterDM(last);			
				dbHelper.setLastSuccessful(msg.obj.toString(), new Date());
				
				Log.i(TAG, "sending closing request");
				bluetoothHelper.write("####CLOSING_REQUEST####");		
				
				
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


	
	/**
	 * process all the data received via bluetooth
	 * @author pcarta
	 */
	private class ProcessDataReceived extends AsyncTask<String, Void, Void> {		

		@Override
		protected Void doInBackground(String... s) {								
			JSONObject o;
			try {
				//if input parameter is String, then cast it to String
				o = new JSONObject(s[0]);
				if (o.getInt(TYPE) == TWEET) {
					Log.d("disaster", "receive a tweet");
					processTweet(o);
				} else if(o.getInt(TYPE) == PHOTO){
					Log.d("disaster", "receive a photo");
					processPhoto(o);
				} else{
					Log.d("disaster", "receive a dm");
					processDM(o);				
				}
				getContentResolver().notifyChange(Tweets.CONTENT_URI, null);
				//if input parameter is a photo, then extract the photo and save it locally
				
			} catch (JSONException e) {
				Log.e(TAG, "error",e);
			}			
			return null;
		}
	}
	
		
	private void processDM(JSONObject o) {
		Log.i(TAG,"processing DM");
		try {		
			
			ContentValues dmValues = getDmContentValues(o);
			if (!dmValues.getAsLong(DirectMessages.COL_SENDER).toString().equals(LoginActivity.getTwitterId(context))) {
				
				ContentValues cvUser = getUserCV(o);
				// insert the tweet
				Uri insertUri = Uri.parse("content://"+ DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/" + DirectMessages.DMS_LIST +
											"/" + DirectMessages.DMS_SOURCE_DISASTER);
				getContentResolver().insert(insertUri, dmValues);

				// insert the user
				Uri insertUserUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS);
				getContentResolver().insert(insertUserUri, cvUser);
				
			}
			
		} catch (JSONException e1) {
			Log.e(TAG, "Exception while receiving disaster dm " , e1);
		}
		
		
	}

	

	private void processTweet(JSONObject o) {
		try {
			Log.i(TAG, "processTweet");
			ContentValues cvTweet = getTweetCV(o);
			cvTweet.put(Tweets.COL_BUFFER, Tweets.BUFFER_DISASTER);			

			// we don't enter our own tweets into the DB.
			if(!cvTweet.getAsLong(Tweets.COL_USER).toString().equals(LoginActivity.getTwitterId(context))){				

				ContentValues cvUser = getUserCV(o);

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
	private void processPhoto(JSONObject o) {
		try {
			Log.i(TAG, "processPhoto");
			String jsonString = o.getString("image");
			String userID = o.getString("userID");
			String photoFileName =  o.getString("photoName");
			byte[] decodedString = Base64.decode(jsonString, Base64.DEFAULT);
			Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
			//locate the directory where the photos are stored
			photoPath = PHOTO_PATH + "/" + userID;
			String[] filePath = {photoPath};
			sdCardHelper.checkSDStuff(filePath);
			File targetFile = sdCardHelper.getFileFromSDCard(photoPath, photoFileName);//photoFileParent, photoFilename));
			saveMyBitmap(targetFile, decodedByte);
		} catch (JSONException e1) {
			Log.e(TAG, "Exception while receiving disaster tweet photo" , e1);
		}

	}
	//save bitmap to local file
	public void saveMyBitmap(File targetFile, Bitmap mBitmap){
		FileOutputStream fOut = null;
		try {
			fOut = new FileOutputStream(targetFile);
			mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
			fOut.flush();
			fOut.close();
		  } catch (IOException e) {
		   e.printStackTrace();
		}
	}

	private void sendDisasterDM(Long last) {
		
		Uri uriQuery = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/" + 
									DirectMessages.DMS_LIST + "/" + DirectMessages.DMS_SOURCE_DISASTER );
		Cursor c = getContentResolver().query(uriQuery, null, null, null, null);
		Log.i(TAG, "c.getCount: "+ c.getCount());
		if (c.getCount() >0){
			c.moveToFirst();
			
			while (!c.isAfterLast()){
				if (c.getLong(c.getColumnIndex(DirectMessages.COL_RECEIVED)) > (last - 5000) ) {
						JSONObject dmToSend;
						
						try {
							dmToSend = getDmJSON(c);
							if (dmToSend != null) {	
								Log.i(TAG, "sending dm");

								bluetoothHelper.write(dmToSend.toString());
							}
							
						} catch (JSONException ex){							
						}
				}
				c.moveToNext();
			}
		}
		c.close();

	}
	

	private void sendDisasterTweets(Long last) {			
		// get disaster tweets
			
		Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);				
		
		if(c.getCount()>0){			
			c.moveToFirst();
			while(!c.isAfterLast()){
				
				if (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED))> (last - 5000)){
					JSONObject toSend;
					try {								
						toSend = getJSON(c);
						if (toSend != null) {
							Log.i(TAG,"sending tweet");
							Log.d(TAG, toSend.toString(5));
							bluetoothHelper.write(toSend.toString());
							//if there is a photo related to this tweet, send it
							if(c.getString(c.getColumnIndex(Tweets.COL_MEDIA)) != null)sendDisasterPhotos(c);
						}
						
					} catch (JSONException e) {								
						Log.e(TAG,"exception ", e);
					}					
				}
				c.moveToNext();				
			}			
		}
		//else
			//bluetoothHelper.write("####CLOSING_REQUEST####");
		c.close();		
	}
	
	private boolean sendDisasterPhotos(Cursor c) throws JSONException{
		JSONObject toSendPhoto;
		String photoFileName =  c.getString(c.getColumnIndex(Tweets.COL_MEDIA));
		Log.d("photo", "photo name:"+ photoFileName);
		String userID = String.valueOf(c.getLong(c.getColumnIndex(TwitterUsers.COL_ID)));
		//locate the directory where the photos are stored
		photoPath = PHOTO_PATH + "/" + userID;
		String[] filePath = {photoPath};
		sdCardHelper.checkSDStuff(filePath);
		Uri photoUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(photoPath, photoFileName));//photoFileParent, photoFilename));
		Log.d(TAG, "photo path:"+ photoUri.getPath());
		Bitmap photoBitmap = sdCardHelper.decodeBitmapFile(photoUri.getPath());
		Log.d("photo", "photo ready");
		if(photoBitmap != null){
			Log.d("photo", "photo ready to be sent");
			toSendPhoto = getJSONFromBitmap(photoBitmap);
			toSendPhoto.put("userID", userID);
			toSendPhoto.put("photoName", photoFileName);
			Log.i(TAG,"sending photo");
			Log.d(TAG, toSendPhoto.toString(5));
			bluetoothHelper.write(toSendPhoto.toString());
			return true;
		}
		return false;
	}
	
	/**
	 * convert photo attached to this tweet to JSONobject
	 * @param bitmapPicture
	 * @return
	 */
	private JSONObject getJSONFromBitmap(Bitmap bitmapPicture) {
		
		ByteArrayOutputStream byteArrayBitmapStream = new ByteArrayOutputStream();
		try {
			bitmapPicture.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayBitmapStream);
			Log.d("photo", "bitmap array size:" + String.valueOf(byteArrayBitmapStream.size()));
			byte[] b = byteArrayBitmapStream.toByteArray();
			String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
			JSONObject jsonObj;
		
			jsonObj = new JSONObject("{\"image\":\"" + encodedImage + "\"}");
			jsonObj.put(TYPE, PHOTO);
			return jsonObj;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "exception:" + e.getMessage());
			return null;
		}

	}
	
	/**
	 * True if the disaster mode is on
	 */
	private boolean isDisasterMode(){
		
		boolean result = false;
		try {
			result = (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true);
			return result;
		} catch (Exception ex) {
			return false;
		}
		
	}

	/**
	 * Creates a JSON Object from a direct message
	 * @param c
	 * @return
	 * @throws JSONException 
	 */
	private JSONObject getDmJSON(Cursor c) throws JSONException {
		JSONObject o= new JSONObject();
		
		if(c.getColumnIndex(DirectMessages.COL_RECEIVER) < 0 || c.getColumnIndex(DirectMessages.COL_SENDER) < 0 
				|| c.isNull(c.getColumnIndex(DirectMessages.COL_CRYPTEXT))) {
			Log.i(TAG,"missing users data");
			return null;
			
		} else {
			o.put(TYPE, DM);
			o.put(DirectMessages.COL_DISASTERID, c.getLong(c.getColumnIndex(DirectMessages.COL_DISASTERID)));
			o.put(DirectMessages.COL_CRYPTEXT, c.getString(c.getColumnIndex(DirectMessages.COL_CRYPTEXT)));			
			o.put(DirectMessages.COL_SENDER, c.getString(c.getColumnIndex(DirectMessages.COL_SENDER)));
			if(c.getColumnIndex(DirectMessages.COL_CREATED) >=0)
				o.put(DirectMessages.COL_CREATED, c.getLong(c.getColumnIndex(DirectMessages.COL_CREATED)));
			o.put(DirectMessages.COL_RECEIVER, c.getLong(c.getColumnIndex(DirectMessages.COL_RECEIVER)));
			o.put(DirectMessages.COL_RECEIVER_SCREENNAME, c.getString(c.getColumnIndex(DirectMessages.COL_RECEIVER_SCREENNAME)));
			o.put(DirectMessages.COL_DISASTERID, c.getLong(c.getColumnIndex(DirectMessages.COL_DISASTERID)));
			o.put(DirectMessages.COL_SIGNATURE, c.getString(c.getColumnIndex(DirectMessages.COL_SIGNATURE)));
			o.put(DirectMessages.COL_CERTIFICATE, c.getString(c.getColumnIndex(DirectMessages.COL_CERTIFICATE)));
			return o;
		}
		
		
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
			o.put(TYPE, TWEET);
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
			if(c.getColumnIndex(Tweets.COL_MEDIA) >=0)
				o.put(Tweets.COL_MEDIA, c.getString(c.getColumnIndex(Tweets.COL_MEDIA)));
			if(c.getColumnIndex(Tweets.COL_SOURCE) >=0)
				o.put(Tweets.COL_SOURCE, c.getString(c.getColumnIndex(Tweets.COL_SOURCE)));		
			if(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE) >=0)
				o.put(TwitterUsers.COL_PROFILEIMAGE, new String(Base64.encode(c.getBlob(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE)), 0)));
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
	protected ContentValues getTweetCV(JSONObject o) throws JSONException{		
		
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
		
		if(o.has(Tweets.COL_MEDIA))
			cv.put(Tweets.COL_MEDIA, o.getString(Tweets.COL_MEDIA));
		
		if(o.has(TwitterUsers.COL_SCREENNAME)) {			
			cv.put(Tweets.COL_SCREENNAME, o.getString(TwitterUsers.COL_SCREENNAME));
		}

		return cv;
	}
	
	/**
	 * Creates content values for a DM from a JSON object
	 * @param o
	 * @return
	 * @throws JSONException
	 */
	private ContentValues getDmContentValues(JSONObject o) throws JSONException {
		
		ContentValues cv = new ContentValues();
		
		if(o.has(DirectMessages.COL_CERTIFICATE))
			cv.put(DirectMessages.COL_CERTIFICATE, o.getString(DirectMessages.COL_CERTIFICATE));
		
		if(o.has(DirectMessages.COL_SIGNATURE))
			cv.put(DirectMessages.COL_SIGNATURE, o.getString(DirectMessages.COL_SIGNATURE));
		
		if(o.has(DirectMessages.COL_CREATED))
			cv.put(DirectMessages.COL_CREATED, o.getLong(DirectMessages.COL_CREATED));
		
		if(o.has(DirectMessages.COL_CRYPTEXT))
			cv.put(DirectMessages.COL_CRYPTEXT, o.getString(DirectMessages.COL_CRYPTEXT));
		
		if(o.has(DirectMessages.COL_DISASTERID))
			cv.put(DirectMessages.COL_DISASTERID, o.getLong(DirectMessages.COL_DISASTERID));
		
		if(o.has(DirectMessages.COL_SENDER))
			cv.put(DirectMessages.COL_SENDER, o.getLong(DirectMessages.COL_SENDER));
		
		if(o.has(DirectMessages.COL_RECEIVER))
			cv.put(DirectMessages.COL_RECEIVER, o.getLong(DirectMessages.COL_RECEIVER));
		
		return cv;
	}
	
	/**
	 * Creates content values for a User from a JSON object
	 * TODO: Move this to where it belongs
	 * @param o
	 * @return
	 * @throws JSONException
	 */
	protected ContentValues getUserCV(JSONObject o) throws JSONException{		 

		// create the content values for the user
		ContentValues cv = new ContentValues();
		if(o.has(TwitterUsers.COL_SCREENNAME)) {
			cv.put(TwitterUsers.COL_SCREENNAME, o.getString(TwitterUsers.COL_SCREENNAME));
			
		}
		if(o.has(TwitterUsers.COL_PROFILEIMAGE))
			cv.put(TwitterUsers.COL_PROFILEIMAGE, Base64.decode(o.getString(TwitterUsers.COL_PROFILEIMAGE), 0));
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
