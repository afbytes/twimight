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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
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
import ch.ethz.twimight.net.opportunistic.OppComms.Neighbor;
import ch.ethz.twimight.net.twitter.DirectMessages;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.InternalStorageHelper;

/**
 * This is the thread for scanning for Bluetooth peers.
 * @author theus
 * @author pcarta
 */
public class ScanningService extends Service{

	
	private static final String TAG = "ScanningService"; /** For Debugging */
	private static final String WAKE_LOCK = "ScanningServiceWakeLock"; 
	
	
	// manage bluetooth communication
	public static OppComms wlanHelper = null;

	
	private static Context context = null;
	Handler handler;
	UpdateTimeout updateTimeout;
	private Cursor cursor;	
	private MacsDBHelper dbHelper;

	WakeLock wakeLock;
	public boolean closing_request_sent = false;
	
	long lastDataExchange;
		

	private static final String TYPE = "message_type";
	public static final int TWEET=0;
	public static final int DM=1;
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		super.onStartCommand(intent, flags, startId);
		//Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler());		
		
		getWakeLock(this);
			
		if (context == null) {
			context = this;
			handler = new Handler();
			updateTimeout = new UpdateTimeout();
			handler.postDelayed(updateTimeout, OppComms.MAX_UPDATE_INTERVAL);
			dbHelper = MacsDBHelper.getInstance(this);
			dbHelper.open();			
	        // set up wlan opp helper			
	        wlanHelper = new WlanOppCommsUdp(this,mHandler);						
	        
		}			
		return START_STICKY; 
		
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
	
	private class UpdateTimeout implements Runnable {

		@Override
		public void run() {			
			if (System.currentTimeMillis() > lastDataExchange + OppComms.MAX_UPDATE_INTERVAL) {
				wlanHelper.forceNeighborUpdate();
				Log.i(TAG,"update timeout went off");
			}
			handler.postDelayed(updateTimeout, OppComms.MAX_UPDATE_INTERVAL);
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
		
		context=null;
		releaseWakeLock();
		handler.removeCallbacks(updateTimeout);
		wlanHelper.stop();
		super.onDestroy();
	}

		



	/**
	 *  The Handler that gets information back from the BluetoothService
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {          

			case Constants.MESSAGE_READ:  				
				new ProcessDataReceived().execute(msg.obj.toString());								
				break; 			
				
			case Constants.MESSAGE_NEW_NEIGHBORS:         	 
				List<Neighbor> neighbors = (List<Neighbor>)msg.obj;	
				Log.i(TAG, "got neighbor list, size = " + neighbors.size() );  				

				for (Neighbor n : neighbors) {
					Log.i(TAG, "sending data to Neighbor: "+n.ipAddress + " " + n.id);
					lastDataExchange = System.currentTimeMillis();
					// Here starts the protocol for Tweet exchange.
					//Long last = dbHelper.getLastSuccessful(n.ipAddress);
					long last = 0;
					sendDisasterTweets(last,n);
					sendDisasterDM(last,n);			
					
					
				}
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
			JSONArray jarray;

			try {
				jarray = new JSONArray(s[0]);				
				for (int i = 0; i < jarray.length(); i++) {
					
					o = jarray.getJSONObject(i);
					if (o.getInt(TYPE) == TWEET) {
						
						processTweet(o);
					} else 
						processDM(o);				
					getContentResolver().notifyChange(Tweets.CONTENT_URI, null);
					
				}			
				
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

	private void sendDisasterDM(Long last, Neighbor n) {
		
		Uri uriQuery = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/" + 
									DirectMessages.DMS_LIST + "/" + DirectMessages.DMS_SOURCE_DISASTER );
		Cursor c = getContentResolver().query(uriQuery, null, null, null, null);
		
		if (c.getCount() >0){
			c.moveToFirst();
			JSONArray jarray = new JSONArray();
			
			while (!c.isAfterLast()){
				if (c.getLong(c.getColumnIndex(DirectMessages.COL_RECEIVED)) > (last - 5000) ) {
						JSONObject dmToSend;
						
						try {
							dmToSend = getDmJSON(c);
							if (dmToSend != null) {	
								jarray.put(dmToSend);
							}
							
						} catch (JSONException ex){							
						}
				}
				c.moveToNext();
			}
			//send data here
			wlanHelper.write(jarray.toString(), n.ipAddress);
		}
		c.close();

	}
	

	private void sendDisasterTweets(Long last, Neighbor n) {			
		// get disaster tweets
		
		Log.i(TAG,"inside sendDisasterTweets");
		Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);				
		
		if(c.getCount()>0){			
			c.moveToFirst();
			JSONArray jarray = new JSONArray();
			while(!c.isAfterLast()){
				
				if (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED))> (last - 5000)){
					JSONObject toSend;
					try {								
						toSend = getJSON(c);
						if (toSend != null) {
							jarray.put(toSend);					
						}
						
					} catch (JSONException e) {								
						Log.e(TAG,"exception ", e);
					}					
				}
				c.moveToNext();				
			}	
			//send data here			
			wlanHelper.write(jarray.toString(), n.ipAddress);
		}
		
		c.close();		
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
			if(c.getColumnIndex(Tweets.COL_SOURCE) >=0)
				o.put(Tweets.COL_SOURCE, c.getString(c.getColumnIndex(Tweets.COL_SOURCE)));		
			if( c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH) >=0 && c.getColumnIndex("userRowId") >= 0 ) {
				Log.i(TAG,"adding picture");
				int userId = c.getInt(c.getColumnIndex("userRowId"));
				Uri imageUri = Uri.parse("content://" +TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + userId);
				try {
					InputStream is = getContentResolver().openInputStream(imageUri);	
					byte[] image = toByteArray(is);
					o.put(TwitterUsers.COL_PROFILEIMAGE, Base64.encodeToString(image, Base64.DEFAULT) );

				} catch (Exception e) {
					Log.e(TAG,"error",e);
					
				};
			}
			return o;
		}
	}

	public static byte[] toByteArray(InputStream in) throws IOException {

		BufferedInputStream bis = new BufferedInputStream(in);
		ByteArrayBuffer baf = new ByteArrayBuffer(2048);	
		//get the bytes one by one			
		int current = 0;			
		while ((current = bis.read()) != -1) {			
			baf.append((byte) current);			
		}	
		return baf.toByteArray();

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
		String screenName = null;
		
		if(o.has(TwitterUsers.COL_SCREENNAME)) {
			screenName = o.getString(TwitterUsers.COL_SCREENNAME);
			cv.put(TwitterUsers.COL_SCREENNAME, o.getString(TwitterUsers.COL_SCREENNAME));

		}
		if(o.has(TwitterUsers.COL_PROFILEIMAGE) && screenName != null) {

			InternalStorageHelper helper = new InternalStorageHelper(getBaseContext());			
			byte[] image = Base64.decode(o.getString(TwitterUsers.COL_PROFILEIMAGE), Base64.DEFAULT);
			helper.writeImage(image, screenName);
			cv.put(TwitterUsers.COL_PROFILEIMAGE_PATH, new File(getFilesDir(),screenName).getPath());

		}

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
