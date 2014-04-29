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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Date;
import java.util.Random;

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONException;
import org.json.JSONObject;

import com.nostra13.universalimageloader.core.ImageLoader;

import android.app.AlarmManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.TwimightBaseActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.twitter.DirectMessages;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TweetsContentProvider;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.InternalStorageHelper;
import ch.ethz.twimight.util.SDCardHelper;

/**
 * This is the thread for scanning for Bluetooth peers.
 * 
 * @author theus
 * @author pcarta
 */

public class ScanningService extends Service implements DevicesReceiver.ScanningFinished,
		StateChangedReceiver.BtSwitchingFinished {

	private static final String T = "btdebug";
	private static final String TAG = ScanningService.class.getSimpleName();
	/** For Debugging */
	private static final String WAKE_LOCK = "ScanningServiceWakeLock";

	private static final int MAX_IMAGE_DIMENSIONS_PX = 500;

	public Handler handler;
	/** Handler for delayed execution of the thread */

	// manage bluetooth communication
	public BluetoothComms bluetoothHelper = null;

	// private Date lastScan;

	private MacsDBHelper dbHelper;
	StateChangedReceiver stateReceiver;
	private Cursor cursor;

	ConnectionAttemptTimeout connTimeout;
	EstablishedConnectionTimeout connectionTimeout;
	WakeLock wakeLock;
	public boolean closing_request_sent = false;

	public static final int STATE_SCANNING = 1;
	public static final int STATE_IDLE = 0;
	private static final long CONNECTING_TIMEOUT = 8000L;
	private static final long CONNECTION_TIMEOUT = 10000L;

	private static final String TYPE = "message_type";
	public static final int MESSAGE_TYPE_TWEET = 0;
	public static final int MESSAGE_TYPE_DM = 1;
	public static final int MESSAGE_TYPE_PHOTO = 2;
	public static final int MESSAGE_TYPE_HTML = 3;

	public static final String FORCED_BLUE_SCAN = "forced_bluetooth_scan";

	// photo
	private String photoPath;
	private static final String PHOTO_PATH = "twimight_photos";

	// html
	private HtmlPagesDbHelper htmlDbHelper;

	// SDcard helper
	private SDCardHelper sdCardHelper;
	// SDcard checking var
	boolean isSDAvail = false;
	boolean isSDWritable = false;
	File SDcardPath = null;

	DevicesReceiver receiver;
	BluetoothAdapter mBtAdapter;
	volatile boolean restartingBlue = false;

	// has a scan been skipped because the adapter was restarting?
	private boolean mScanPending = false;

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		handler = new Handler();
		// set up Bluetooth

		bluetoothHelper = new BluetoothComms(this, mHandler);
		bluetoothHelper.start();
		dbHelper = new MacsDBHelper(getApplicationContext());
		dbHelper.open();

		// sdCard helper
		sdCardHelper = new SDCardHelper();
		// htmldb helper
		htmlDbHelper = new HtmlPagesDbHelper(getApplicationContext());
		htmlDbHelper.open();

		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	private void registerDevicesReceiver() {
		unregisterDevReceiver();
		receiver = new DevicesReceiver(getApplicationContext());
		receiver.setListener(this);
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		registerReceiver(receiver, filter);
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(receiver, filter);

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		super.onStartCommand(intent, flags, startId);
		Log.d(T, "onStartCommand()");
		// Thread.setDefaultUncaughtExceptionHandler(new
		// CustomExceptionHandler());
		ScanningAlarm.releaseWakeLock();
		getWakeLock(this);
		// Register for broadcasts when discovery has finished
		registerDevicesReceiver();

		float probability;

		if (intent != null && intent.getBooleanExtra(FORCED_BLUE_SCAN, true))
			probability = 0;
		else {
			// get a random number
			Random r = new Random(System.currentTimeMillis());
			probability = r.nextFloat();
		}
		initiateScanningRound(probability);

		return START_STICKY;
	}

	private void initiateScanningRound(float probability) {
		if (mBtAdapter != null && !restartingBlue) {
			if (probability <= 1) {
				// If we're already discovering, stop it
				if (mBtAdapter.isDiscovering()) {
					mBtAdapter.cancelDiscovery();
				}
				// Request discover from BluetoothAdapter
				dbHelper.updateMacsDeActive();
				bluetoothHelper.stop();
				boolean ret = mBtAdapter.startDiscovery();
				BluetoothStatus.getInstance().setStatusDescription(getString(R.string.btstatus_searching));
				Log.d(T, "started discovery (ret=" + ret + ")");
				Log.d(T, "discovery running: " + mBtAdapter.isDiscovering());
			}
			mScanPending = false;
		} else {
			Log.d(T, "skipping scan (mBtAdapter=" + mBtAdapter + ", restartingBlue=" + restartingBlue + ")");
			mScanPending = true;
			stopSelf();
		}
	}

	public class CustomExceptionHandler implements UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread t, Throwable e) {
			Log.e(TAG, "error ", e);
			ScanningService.this.stopSelf();
			AlarmManager mgr = (AlarmManager) LoginActivity.getInstance().getSystemService(Context.ALARM_SERVICE);
			mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), LoginActivity.getRestartIntent());
			System.exit(2);
		}
	}

	/**
	 * Acquire the Wake Lock
	 * 
	 * @param context
	 */
	void getWakeLock(Context context) {

		releaseWakeLock();

		PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
		wakeLock.acquire();
	}

	/**
	 * We have to make sure to release the wake lock after the TDSThread is
	 * done!
	 * 
	 * @param context
	 */
	void releaseWakeLock() {
		if (wakeLock != null)
			if (wakeLock.isHeld())
				wakeLock.release();
	}

	@Override
	public void onDestroy() {

		Log.i(TAG, "inside onDestroy");
		mHandler.removeMessages(Constants.MESSAGE_CONNECTION_FAILED);
		mHandler.removeMessages(Constants.MESSAGE_CONNECTION_LOST);
		mHandler.removeMessages(Constants.MESSAGE_CONNECTION_SUCCEEDED);
		mHandler.removeMessages(Constants.BLUETOOTH_RESTART);
		releaseWakeLock();
		bluetoothHelper.stop();
		// Make sure we're not doing discovery anymore
		if (mBtAdapter != null) {
			mBtAdapter.cancelDiscovery();
		}
		if (receiver != null)
			Log.i(TAG, "receiver not null");
		unregisterDevReceiver();
		unregisterStateReceiver();
		super.onDestroy();
	}

	/**
	 * Start the scanning.
	 * 
	 * @return true if the connection with the TDS was successful, false
	 *         otherwise.
	 */
	private boolean startScanning() {

		// Get a cursor over all "active" MACs in the DB
		cursor = dbHelper.fetchActiveMacs();
		Log.i(T, "active macs: " + cursor.getCount());

		if (cursor.moveToFirst()) {
			// Get the field values
			String mac = cursor.getString(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
			Log.i(T,
					"Connection Attempt to: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/"
							+ dbHelper.fetchMacAttempts(mac) + ")");

			// if (bluetoothHelper.getState() == bluetoothHelper.STATE_LISTEN) {

			// if ( (System.currentTimeMillis() -
			// dbHelper.getLastSuccessful(mac) ) >
			// Constants.MEETINGS_INTERVAL) {
			// If we're already discovering, stop it
			if (mBtAdapter.isDiscovering()) {
				mBtAdapter.cancelDiscovery();
			}
			bluetoothHelper.connect(mac);
			connTimeout = new ConnectionAttemptTimeout();
			handler.postDelayed(connTimeout, CONNECTING_TIMEOUT); // timeout
																	// for
																	// the
																	// conn
																	// attempt
			// } else {
			// Log.i(TAG,"skipping connection, last meeting was too recent");
			// nextScanning();
			// }
			// } else if (bluetoothHelper.getState() !=
			// bluetoothHelper.STATE_CONNECTED) {
			// bluetoothHelper.start();
			//
			// }

		} else
			stopScanning();

		return false;
	}

	private class ConnectionAttemptTimeout implements Runnable {
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

	private class EstablishedConnectionTimeout implements Runnable {
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
		if (cursor == null || bluetoothHelper.getState() == BluetoothComms.STATE_CONNECTED)
			stopScanning();
		else {
			// do we have another MAC in the cursor?
			if (cursor.moveToNext()) {

				Log.i(TAG, "scanning for the next peer");
				String mac = cursor.getString(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
				Log.i(T,
						"Connection Attempt to: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/"
								+ dbHelper.fetchMacAttempts(mac) + ")");
				// if ( (System.currentTimeMillis() -
				// dbHelper.getLastSuccessful(mac) ) >
				// Constants.MEETINGS_INTERVAL) {

				Log.i(TAG,
						"Connection attempt to: " + mac + " (" + dbHelper.fetchMacSuccessful(mac) + "/"
								+ dbHelper.fetchMacAttempts(mac) + ")");
				// If we're already discovering, stop it
				if (mBtAdapter.isDiscovering()) {
					mBtAdapter.cancelDiscovery();
				}
				bluetoothHelper.connect(mac);
				connTimeout = new ConnectionAttemptTimeout();
				handler.postDelayed(connTimeout, CONNECTING_TIMEOUT); // timeout
																		// for
																		// the
																		// conn
																		// attempt
				// } else {
				// Log.i(TAG,"skipping connection, last meeting was too recent");
				// nextScanning();
				// }
			} else
				stopScanning();

		}

	}

	/**
	 * Terminates one round of scanning: cleans up and reschedules next scan
	 */
	private void stopScanning() {

		if (cursor != null) {
			cursor.close();
			cursor = null;
		}
		removeConnectionAttemptTimeout();

		// restart bluetooth because it MIGHT help to keep in it a good state
		Message msg = mHandler.obtainMessage(Constants.BLUETOOTH_RESTART, -1, -1, null);
		mHandler.sendMessage(msg);

	}

	private void removeConnectionAttemptTimeout() {
		if (connTimeout != null) { // I need to remove the timeout started at
									// the beginning
			handler.removeCallbacks(connTimeout);
			connTimeout = null;
		}

	}

	private void removeEstablishedConnectionTimeout() {
		if (connectionTimeout != null) { // I need to remove the timeout started
											// at the beginning
			handler.removeCallbacks(connectionTimeout);
			connectionTimeout = null;
		}

	}

	/**
	 * The Handler that gets information back from the BluetoothService
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case Constants.MESSAGE_READ:
				if (msg.obj.toString().equals("<closing_request>")) {
					bluetoothHelper.write("<ack_closing_request>");

				} else if (msg.obj.toString().equals("<ack_closing_request>")) {
					if (TwimightBaseActivity.D)
						Log.i(TAG, "ack closing request received, connection shutdown");
					bluetoothHelper.start();
				} else
					new ProcessDataReceived().execute(msg.obj.toString()); // not
																			// String,
																			// object
																			// instead

				break;

			case Constants.MESSAGE_CONNECTION_SUCCEEDED:
				if (TwimightBaseActivity.D)
					Log.d(TAG, "connection succeeded");

				removeConnectionAttemptTimeout();
				connectionTimeout = new EstablishedConnectionTimeout();
				handler.postDelayed(connectionTimeout, CONNECTION_TIMEOUT); // timeout
																			// for
																			// the
																			// conn
																			// attempt

				// Insert successful connection into DB
				dbHelper.updateMacSuccessful(msg.obj.toString(), 1);

				// Here starts the protocol for Tweet exchange.
				Long last = dbHelper.getLastSuccessful(msg.obj.toString());
				// new SendDisasterData(msg.obj.toString()).execute(last);
				sendDisasterTweets(last);
				sendDisasterDM(last);
				if (bluetoothHelper != null) {
					bluetoothHelper.write("<closing_request>");
					dbHelper.setLastSuccessful(msg.obj.toString(), new Date());
				}

				break;
			case Constants.MESSAGE_CONNECTION_FAILED:
				if (TwimightBaseActivity.D)
					Log.i(TAG, "connection failed");

				// Insert failed connection into DB
				dbHelper.updateMacAttempts(msg.obj.toString(), 1);
				removeConnectionAttemptTimeout();
				// Next scan
				if (bluetoothHelper != null)
					nextScanning();
				break;

			case Constants.MESSAGE_CONNECTION_LOST:
				if (TwimightBaseActivity.D)
					Log.i(TAG, "connection lost");
				// Next scan
				removeEstablishedConnectionTimeout();
				if (bluetoothHelper != null)
					nextScanning();
				break;

			case Constants.BLUETOOTH_RESTART:
				if (TwimightBaseActivity.D)
					Log.i(T, "restarting Bluetooth");
				unregisterStateReceiver();
				stateReceiver = new StateChangedReceiver();
				IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
				stateReceiver.setListener(ScanningService.this);
				registerReceiver(stateReceiver, filter);

				if (mBtAdapter != null) {
					if (mBtAdapter.isEnabled()) {
						Log.d(T, "disbling bt");
						mBtAdapter.disable();
					} else {
						Log.d(T, "bt disabled. enabling now...");
						mBtAdapter.enable();
					}
				}
				BluetoothStatus.getInstance().setStatusDescription(getString(R.string.btstatus_resetting));
				restartingBlue = true;
				break;

			}
		}
	};

	/**
	 * process all the data received via bluetooth
	 * 
	 * @author pcarta
	 */
	private class ProcessDataReceived extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String... s) {
			JSONObject o;
			try {
				// if input parameter is String, then cast it to String
				o = new JSONObject(s[0]);
				if (o.getInt(TYPE) == MESSAGE_TYPE_TWEET) {
					Log.d("disaster", "receive a tweet");
					processTweet(o);
				} else if (o.getInt(TYPE) == MESSAGE_TYPE_PHOTO) {
					Log.d("disaster", "receive a photo");
					processPhoto(o);
				} else if (o.getInt(TYPE) == MESSAGE_TYPE_HTML) {
					Log.d("disaster", "receive xml");
					processHtml(o);
				} else {
					Log.d("disaster", "receive a dm");
					processDM(o);
				}
				getContentResolver().notifyChange(Tweets.TABLE_TIMELINE_URI, null);
				// if input parameter is a photo, then extract the photo and
				// save it locally

			} catch (JSONException e) {
				Log.e(TAG, "error", e);
			}
			return null;
		}
	}

	private void processDM(JSONObject o) {
		Log.i(TAG, "processing DM");
		try {

			ContentValues dmValues = getDmContentValues(o);
			if (!dmValues.getAsLong(DirectMessages.COL_SENDER).toString()
					.equals(LoginActivity.getTwitterId(getApplicationContext()))) {

				ContentValues cvUser = getUserCV(o);
				// insert the tweet
				Uri insertUri = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
						+ DirectMessages.DMS_LIST + "/" + DirectMessages.DMS_SOURCE_DISASTER);
				getContentResolver().insert(insertUri, dmValues);

				// insert the user
				Uri insertUserUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
						+ TwitterUsers.TWITTERUSERS);
				getContentResolver().insert(insertUserUri, cvUser);

			}

		} catch (JSONException e1) {
			Log.e(TAG, "Exception while receiving disaster dm ", e1);
		}

	}

	private void processTweet(JSONObject o) {
		try {
			Log.i(TAG, "processTweet");
			ContentValues cvTweet = getTweetCV(o);
			cvTweet.put(Tweets.COL_BUFFER, Tweets.BUFFER_DISASTER);

			// we don't enter our own tweets into the DB.
			if (!cvTweet.getAsLong(Tweets.COL_USER_TID).toString()
					.equals(LoginActivity.getTwitterId(getApplicationContext()))) {

				ContentValues cvUser = getUserCV(o);

				// insert the tweet
				Uri insertUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
						+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);
				getContentResolver().insert(insertUri, cvTweet);

				// insert the user
				Uri insertUserUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
						+ TwitterUsers.TWITTERUSERS);
				getContentResolver().insert(insertUserUri, cvUser);
			}

		} catch (JSONException e1) {
			Log.e(TAG, "Exception while receiving disaster tweet ", e1);
		}

	}

	private void processPhoto(JSONObject o) {
		try {
			String jsonString = o.getString("image");
			String userID = o.getString("userID");
			String photoFileName = o.getString("photoName");

			Log.d(TAG, "processPhoto " + jsonString);
			// locate the directory where the photos are stored
			photoPath = PHOTO_PATH + "/" + userID;
			String[] filePath = { photoPath };
			if (sdCardHelper.checkSDState(filePath)) {
				File targetFile = sdCardHelper.getFileFromSDCard(photoPath, photoFileName);// photoFileParent,
																							// photoFilename));
				saveFile(targetFile, jsonString);
			}

		} catch (JSONException e1) {
			Log.e(TAG, "Exception while receiving disaster tweet photo", e1);
		}
	}

	private void processHtml(JSONObject o) {
		try {
			Log.i(TAG, "process HTML");
			String xmlContent = o.getString(HtmlPage.COL_HTML);
			String filename = o.getString(HtmlPage.COL_FILENAME);
			Long tweetId = o.getLong(HtmlPage.COL_DISASTERID);
			String htmlUrl = o.getString(HtmlPage.COL_URL);

			String[] filePath = { HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(getApplicationContext()) };
			if (sdCardHelper.checkSDState(filePath)) {
				File targetFile = sdCardHelper.getFileFromSDCard(filePath[0], filename);// photoFileParent,
																						// photoFilename));
				if (saveFile(targetFile, xmlContent)) {
					// downloaded = 1;
				}
			}
			htmlDbHelper.insertPage(htmlUrl, filename, tweetId, 0);

		} catch (JSONException e1) {
			Log.e(TAG, "Exception while receiving disaster tweet photo", e1);
		}
	}

	private boolean saveFile(File file, String fileContent) {

		try {
			FileOutputStream fOut = new FileOutputStream(file);
			byte[] decodedString = Base64.decode(fileContent, Base64.DEFAULT);
			fOut.write(decodedString);
			fOut.close();
			return true;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	private void sendDisasterDM(Long last) {

		Uri uriQuery = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
				+ DirectMessages.DMS_LIST + "/" + DirectMessages.DMS_SOURCE_DISASTER);
		Cursor c = getContentResolver().query(uriQuery, null, null, null, null);
		Log.i(TAG, "c.getCount: " + c.getCount());
		if (c.getCount() > 0) {
			c.moveToFirst();

			while (!c.isAfterLast()) {
				if (c.getLong(c.getColumnIndex(DirectMessages.COL_RECEIVED)) > (last - 1 * 30 * 1000L)) {
					JSONObject dmToSend;

					try {
						dmToSend = getDmJSON(c);
						if (dmToSend != null) {
							Log.i(TAG, "sending dm");

							bluetoothHelper.write(dmToSend.toString());
						}

					} catch (JSONException ex) {
					}
				}
				c.moveToNext();
			}
		}
		c.close();

	}

	private void sendDisasterTweets(Long last) {
		// get disaster tweets

		Uri queryUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
				+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER);

		Cursor c = getContentResolver().query(queryUri, null, null, null, null);
		Log.d(TAG, "count:" + String.valueOf(c.getCount()));
		boolean prefWebShare = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefWebShare", false);
		Log.d(TAG, "web share:" + String.valueOf(prefWebShare));
		if (c.getCount() > 0) {
			c.moveToFirst();
			while (!c.isAfterLast()) {

				try {
					if (prefWebShare) {
						if (c.getInt(c.getColumnIndex(Tweets.COL_HTML_PAGES)) == 1) {

							if (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED)) > (last - 10 * 60 * 1000L)) {
								JSONObject toSend;

								toSend = getJSON(c);
								if (toSend != null) {
									Log.i(TAG, "sending tweet");
									Log.d(TAG, toSend.toString(5));
									bluetoothHelper.write(toSend.toString());
									// if there is a photo related to this
									// tweet, send it
									if (c.getString(c.getColumnIndex(Tweets.COL_MEDIA_URIS)) != null) {
										sendDisasterPhoto(c);
									}
								}
								sendDisasterHtmls(c);
							}
						}

					} else {
						if (c.getString(c.getColumnIndex(Tweets.COL_MEDIA_URIS)) != null) {
							if (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED)) > (last - 5 * 60 * 1000L)) {
								JSONObject toSend;

								toSend = getJSON(c);
								if (toSend != null) {
									Log.i(TAG, "sending tweet");
									Log.d(TAG, toSend.toString(5));
									bluetoothHelper.write(toSend.toString());
									// if there is a photo related to this
									// tweet, send it
									if (c.getString(c.getColumnIndex(Tweets.COL_MEDIA_URIS)) != null) {
										sendDisasterPhoto(c);
									}
								}
							}
						} else if (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED)) > (last - 1 * 30 * 1000L)) {
							JSONObject toSend;

							toSend = getJSON(c);
							if (toSend != null) {
								Log.i(TAG, "sending tweet");
								Log.d(TAG, toSend.toString(5));
								bluetoothHelper.write(toSend.toString());
							}
						}
					}

				} catch (JSONException e) {
					Log.e(TAG, "exception ", e);
				}

				c.moveToNext();
			}
		}
		// else
		// bluetoothHelper.write("####CLOSING_REQUEST####");
		c.close();
	}

	private boolean sendDisasterPhoto(Cursor c) throws JSONException {
		String photoFileUri = c.getString(c.getColumnIndex(Tweets.COL_MEDIA_URIS));
		SDCardHelper sdCardHelper = new SDCardHelper();
		String encodedPhoto = sdCardHelper.getImageAsBas64Jpeg(photoFileUri, MAX_IMAGE_DIMENSIONS_PX);
		JSONObject toSendPhoto = new JSONObject("{\"image\":\"" + encodedPhoto + "\"}");
		toSendPhoto.put(TYPE, MESSAGE_TYPE_PHOTO);
		String userID = String.valueOf(c.getLong(c.getColumnIndex(TwitterUsers.COL_TWITTER_USER_ID)));
		toSendPhoto.put("userID", userID);
		Uri uri = Uri.parse(photoFileUri);
		String photoFileName = uri.getLastPathSegment();
		Log.d(TAG, "photoFileName:+ " + photoFileName);
		toSendPhoto.put("photoName", photoFileName);
		bluetoothHelper.write(toSendPhoto.toString());
		return true;
	}

	private String getProcessedImage(String photoFileUri) {
		Bitmap bitmap = ImageLoader.getInstance().loadImageSync(photoFileUri);
		String encodedImage = null;
		if (bitmap != null) {
			// scale down large images
			if (bitmap.getHeight() > MAX_IMAGE_DIMENSIONS_PX || bitmap.getWidth() > MAX_IMAGE_DIMENSIONS_PX) {
				double shrinkFactor = ((double) MAX_IMAGE_DIMENSIONS_PX)
						/ Math.max(bitmap.getWidth(), bitmap.getHeight());
				bitmap = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * shrinkFactor),
						(int) (bitmap.getHeight() * shrinkFactor), false);
			}
			ByteArrayOutputStream byteArrayBitmapStream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayBitmapStream);
			byte[] bytes = byteArrayBitmapStream.toByteArray();
			encodedImage = Base64.encodeToString(bytes, Base64.DEFAULT);
		}
		return encodedImage;
	}

	private void sendDisasterHtmls(Cursor c) throws JSONException {

		JSONObject toSendXml;

		// String userId = String.valueOf(c.getLong(c
		// .getColumnIndex(Tweets.COL_TWITTERUSER)));

		String substr = Html.fromHtml(c.getString(c.getColumnIndex(Tweets.COL_TEXT))).toString();

		String[] strarr = substr.split(" ");

		// check the urls of the tweet
		for (String subStrarr : strarr) {

			if (subStrarr.indexOf("http://") >= 0 || subStrarr.indexOf("https://") >= 0) {
				String subUrl = null;
				if (subStrarr.indexOf("http://") >= 0) {
					subUrl = subStrarr.substring(subStrarr.indexOf("http://"));
				} else if (subStrarr.indexOf("https://") >= 0) {
					subUrl = subStrarr.substring(subStrarr.indexOf("https://"));
				}
				Cursor cursorHtml = htmlDbHelper.getPageInfo(subUrl);

				if (cursorHtml != null) {

					if (!cursorHtml.isNull(cursorHtml.getColumnIndex(HtmlPage.COL_FILENAME))) {

						String[] filePath = { HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(this) };
						String filename = cursorHtml.getString(cursorHtml.getColumnIndex(HtmlPage.COL_FILENAME));
						Long tweetId = cursorHtml.getLong(cursorHtml.getColumnIndex(HtmlPage.COL_DISASTERID));
						if (sdCardHelper.checkSDState(filePath)) {

							File xmlFile = sdCardHelper.getFileFromSDCard(filePath[0], filename);
							if (xmlFile.exists()) {
								toSendXml = getJSONFromXml(xmlFile);
								toSendXml.put(HtmlPage.COL_URL, subUrl);
								toSendXml.put(HtmlPage.COL_FILENAME, filename);
								toSendXml.put(HtmlPage.COL_DISASTERID, tweetId);
								Log.d(TAG, "sending htmls");
								Log.d(TAG, toSendXml.toString(5));
								bluetoothHelper.write(toSendXml.toString());

							}

						}
					}
				}
			}
		}
	}

	private JSONObject getJSONFromXml(File xml) {
		try {

			JSONObject jsonObj = new JSONObject();
			FileInputStream xmlStream = null;
			try {
				xmlStream = new FileInputStream(xml);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();

				byte[] buffer = new byte[1024];
				int length;
				while ((length = xmlStream.read(buffer)) != -1) {
					bos.write(buffer, 0, length);
				}
				byte[] b = bos.toByteArray();
				String xmlString = Base64.encodeToString(b, Base64.DEFAULT);
				jsonObj.put(HtmlPage.COL_HTML, xmlString);

			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				if (xmlStream != null) {
					try {
						xmlStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			jsonObj.put(TYPE, MESSAGE_TYPE_HTML);
			return jsonObj;
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			Log.d(TAG, "exception:" + e.getMessage());
			return null;
		}
	}

	/**
	 * Creates a JSON Object from a direct message
	 * 
	 * @param c
	 * @return
	 * @throws JSONException
	 */
	private JSONObject getDmJSON(Cursor c) throws JSONException {
		JSONObject o = new JSONObject();

		if (c.getColumnIndex(DirectMessages.COL_RECEIVER) < 0 || c.getColumnIndex(DirectMessages.COL_SENDER) < 0
				|| c.isNull(c.getColumnIndex(DirectMessages.COL_CRYPTEXT))) {
			Log.i(TAG, "missing users data");
			return null;

		} else {
			o.put(TYPE, MESSAGE_TYPE_DM);
			o.put(DirectMessages.COL_DISASTERID, c.getLong(c.getColumnIndex(DirectMessages.COL_DISASTERID)));
			o.put(DirectMessages.COL_CRYPTEXT, c.getString(c.getColumnIndex(DirectMessages.COL_CRYPTEXT)));
			o.put(DirectMessages.COL_SENDER, c.getString(c.getColumnIndex(DirectMessages.COL_SENDER)));
			if (c.getColumnIndex(DirectMessages.COL_CREATED) >= 0)
				o.put(DirectMessages.COL_CREATED, c.getLong(c.getColumnIndex(DirectMessages.COL_CREATED)));
			o.put(DirectMessages.COL_RECEIVER, c.getLong(c.getColumnIndex(DirectMessages.COL_RECEIVER)));
			o.put(DirectMessages.COL_RECEIVER_SCREENNAME,
					c.getString(c.getColumnIndex(DirectMessages.COL_RECEIVER_SCREENNAME)));
			o.put(DirectMessages.COL_DISASTERID, c.getLong(c.getColumnIndex(DirectMessages.COL_DISASTERID)));
			o.put(DirectMessages.COL_SIGNATURE, c.getString(c.getColumnIndex(DirectMessages.COL_SIGNATURE)));
			o.put(DirectMessages.COL_CERTIFICATE, c.getString(c.getColumnIndex(DirectMessages.COL_CERTIFICATE)));
			return o;
		}

	}

	/**
	 * Creates a JSON Object from a Tweet TODO: Move this where it belongs!
	 * 
	 * @param c
	 * @return
	 * @throws JSONException
	 */
	protected JSONObject getJSON(Cursor c) throws JSONException {
		JSONObject o = new JSONObject();
		if (c.getColumnIndex(Tweets.COL_USER_TID) < 0 || c.getColumnIndex(TwitterUsers.COL_SCREEN_NAME) < 0) {
			Log.i(TAG, "missing user data");
			return null;
		}

		else {

			o.put(Tweets.COL_USER_TID, c.getLong(c.getColumnIndex(Tweets.COL_USER_TID)));
			o.put(TYPE, MESSAGE_TYPE_TWEET);
			o.put(TwitterUsers.COL_SCREEN_NAME, c.getString(c.getColumnIndex(TwitterUsers.COL_SCREEN_NAME)));
			if (c.getColumnIndex(Tweets.COL_CREATED_AT) >= 0)
				o.put(Tweets.COL_CREATED_AT, c.getLong(c.getColumnIndex(Tweets.COL_CREATED_AT)));
			if (c.getColumnIndex(Tweets.COL_CERTIFICATE) >= 0)
				o.put(Tweets.COL_CERTIFICATE, c.getString(c.getColumnIndex(Tweets.COL_CERTIFICATE)));
			if (c.getColumnIndex(Tweets.COL_SIGNATURE) >= 0)
				o.put(Tweets.COL_SIGNATURE, c.getString(c.getColumnIndex(Tweets.COL_SIGNATURE)));

			if (c.getColumnIndex(Tweets.COL_TEXT) >= 0)
				o.put(Tweets.COL_TEXT, c.getString(c.getColumnIndex(Tweets.COL_TEXT)));
			if (c.getColumnIndex(Tweets.COL_REPLY_TO_TWEET_TID) >= 0)
				o.put(Tweets.COL_REPLY_TO_TWEET_TID, c.getLong(c.getColumnIndex(Tweets.COL_REPLY_TO_TWEET_TID)));
			if (c.getColumnIndex(Tweets.COL_LAT) >= 0)
				o.put(Tweets.COL_LAT, c.getDouble(c.getColumnIndex(Tweets.COL_LAT)));
			if (c.getColumnIndex(Tweets.COL_LNG) >= 0)
				o.put(Tweets.COL_LNG, c.getDouble(c.getColumnIndex(Tweets.COL_LNG)));
			if (!c.isNull(c.getColumnIndex(Tweets.COL_MEDIA_URIS))) {
				String photoUri = c.getString(c.getColumnIndex(Tweets.COL_MEDIA_URIS));
				Uri uri = Uri.parse(photoUri);
				o.put(Tweets.COL_MEDIA_URIS, uri.getLastPathSegment());
			}
			if (c.getColumnIndex(Tweets.COL_HTML_PAGES) >= 0)
				o.put(Tweets.COL_HTML_PAGES, c.getString(c.getColumnIndex(Tweets.COL_HTML_PAGES)));
			if (c.getColumnIndex(Tweets.COL_SOURCE) >= 0)
				o.put(Tweets.COL_SOURCE, c.getString(c.getColumnIndex(Tweets.COL_SOURCE)));

			if (c.getColumnIndex(Tweets.COL_TID) >= 0 && !c.isNull(c.getColumnIndex(Tweets.COL_TID)))
				o.put(Tweets.COL_TID, c.getLong(c.getColumnIndex(Tweets.COL_TID)));

			if (c.getColumnIndex(TwitterUsers.COL_PROFILE_IMAGE_URI) >= 0
					&& c.getColumnIndex(TweetsContentProvider.COL_USER_ROW_ID) >= 0) {

				String imageUri = c.getString(c.getColumnIndex(TwitterUsers.COL_PROFILE_IMAGE_URI));
				Bitmap profileImage = ImageLoader.getInstance().loadImageSync(imageUri);
				if (profileImage != null) {
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					profileImage.compress(Bitmap.CompressFormat.PNG, 100, stream);
					byte[] byteArray = stream.toByteArray();
					String profileImageBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);
					o.put(TwitterUsers.JSON_FIELD_PROFILE_IMAGE, profileImageBase64);
				}
			}

			return o;
		}
	}

	public static byte[] toByteArray(InputStream in) throws IOException {

		BufferedInputStream bis = new BufferedInputStream(in);
		ByteArrayBuffer baf = new ByteArrayBuffer(2048);
		// get the bytes one by one
		int current = 0;
		while ((current = bis.read()) != -1) {
			baf.append((byte) current);
		}
		return baf.toByteArray();

	}

	/**
	 * Creates content values for a Tweet from a JSON object TODO: Move this to
	 * where it belongs
	 * 
	 * @param o
	 * @return
	 * @throws JSONException
	 */
	protected ContentValues getTweetCV(JSONObject o) throws JSONException {

		ContentValues cv = new ContentValues();

		if (o.has(Tweets.COL_CERTIFICATE))
			cv.put(Tweets.COL_CERTIFICATE, o.getString(Tweets.COL_CERTIFICATE));

		if (o.has(Tweets.COL_SIGNATURE))
			cv.put(Tweets.COL_SIGNATURE, o.getString(Tweets.COL_SIGNATURE));

		if (o.has(Tweets.COL_CREATED_AT))
			cv.put(Tweets.COL_CREATED_AT, o.getLong(Tweets.COL_CREATED_AT));

		if (o.has(Tweets.COL_TEXT)) {
			cv.put(Tweets.COL_TEXT, o.getString(Tweets.COL_TEXT));
			cv.put(Tweets.COL_TEXT_PLAIN, Html.fromHtml(o.getString(Tweets.COL_TEXT)).toString());
		}

		if (o.has(Tweets.COL_USER_TID)) {
			cv.put(Tweets.COL_USER_TID, o.getLong(Tweets.COL_USER_TID));
		}

		if (o.has(Tweets.COL_TID)) {
			cv.put(Tweets.COL_TID, o.getLong(Tweets.COL_TID));
		}

		if (o.has(Tweets.COL_REPLY_TO_TWEET_TID))
			cv.put(Tweets.COL_REPLY_TO_TWEET_TID, o.getLong(Tweets.COL_REPLY_TO_TWEET_TID));

		if (o.has(Tweets.COL_LAT))
			cv.put(Tweets.COL_LAT, o.getDouble(Tweets.COL_LAT));

		if (o.has(Tweets.COL_LNG))
			cv.put(Tweets.COL_LNG, o.getDouble(Tweets.COL_LNG));

		if (o.has(Tweets.COL_SOURCE))
			cv.put(Tweets.COL_SOURCE, o.getString(Tweets.COL_SOURCE));

		if (o.has(Tweets.COL_MEDIA_URIS)) {
			String userID = cv.getAsString(Tweets.COL_USER_TID);
			photoPath = PHOTO_PATH + "/" + userID;
			String photoFileName = o.getString(Tweets.COL_MEDIA_URIS);
			File targetFile = sdCardHelper.getFileFromSDCard(photoPath, photoFileName);

			cv.put(Tweets.COL_MEDIA_URIS, Uri.fromFile(targetFile).toString());

		}

		if (o.has(Tweets.COL_HTML_PAGES))
			cv.put(Tweets.COL_HTML_PAGES, o.getString(Tweets.COL_HTML_PAGES));

		if (o.has(TwitterUsers.COL_SCREEN_NAME)) {
			cv.put(Tweets.COL_SCREEN_NAME, o.getString(TwitterUsers.COL_SCREEN_NAME));
		}

		return cv;
	}

	/**
	 * Creates content values for a DM from a JSON object
	 * 
	 * @param o
	 * @return
	 * @throws JSONException
	 */
	private ContentValues getDmContentValues(JSONObject o) throws JSONException {

		ContentValues cv = new ContentValues();

		if (o.has(DirectMessages.COL_CERTIFICATE))
			cv.put(DirectMessages.COL_CERTIFICATE, o.getString(DirectMessages.COL_CERTIFICATE));

		if (o.has(DirectMessages.COL_SIGNATURE))
			cv.put(DirectMessages.COL_SIGNATURE, o.getString(DirectMessages.COL_SIGNATURE));

		if (o.has(DirectMessages.COL_CREATED))
			cv.put(DirectMessages.COL_CREATED, o.getLong(DirectMessages.COL_CREATED));

		if (o.has(DirectMessages.COL_CRYPTEXT))
			cv.put(DirectMessages.COL_CRYPTEXT, o.getString(DirectMessages.COL_CRYPTEXT));

		if (o.has(DirectMessages.COL_DISASTERID))
			cv.put(DirectMessages.COL_DISASTERID, o.getLong(DirectMessages.COL_DISASTERID));

		if (o.has(DirectMessages.COL_SENDER))
			cv.put(DirectMessages.COL_SENDER, o.getLong(DirectMessages.COL_SENDER));

		if (o.has(DirectMessages.COL_RECEIVER))
			cv.put(DirectMessages.COL_RECEIVER, o.getLong(DirectMessages.COL_RECEIVER));

		return cv;
	}

	/**
	 * Creates content values for a User from a JSON object TODO: Move this to
	 * where it belongs
	 * 
	 * @param o
	 * @return
	 * @throws JSONException
	 */
	protected ContentValues getUserCV(JSONObject o) throws JSONException {

		// create the content values for the user
		ContentValues cv = new ContentValues();
		String screenName = null;

		if (o.has(TwitterUsers.COL_SCREEN_NAME)) {
			screenName = o.getString(TwitterUsers.COL_SCREEN_NAME);
			cv.put(TwitterUsers.COL_SCREEN_NAME, o.getString(TwitterUsers.COL_SCREEN_NAME));
		}

		if (o.has(TwitterUsers.JSON_FIELD_PROFILE_IMAGE) && screenName != null) {
			InternalStorageHelper helper = new InternalStorageHelper(getBaseContext());
			byte[] image = Base64.decode(o.getString(TwitterUsers.JSON_FIELD_PROFILE_IMAGE), Base64.DEFAULT);
			helper.writeImage(image, screenName);
			String profileImageUri = Uri.fromFile(new File(getFilesDir(), screenName)).toString();
			Log.d(TAG, "storing profile image at: " + profileImageUri);
			cv.put(TwitterUsers.COL_PROFILE_IMAGE_URI, profileImageUri);
		}

		if (o.has(Tweets.COL_USER_TID)) {
			cv.put(TwitterUsers.COL_TWITTER_USER_ID, o.getLong(Tweets.COL_USER_TID));

		}
		cv.put(TwitterUsers.COL_IS_DISASTER_PEER, 1);

		return cv;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onScanningFinished() {
		Log.i(TAG, "onScanningFinished");
		unregisterDevReceiver();
		receiver = null;
		startScanning();
	}

	private void unregisterDevReceiver() {
		if (receiver != null) {
			receiver.setListener(null);
			try {
				unregisterReceiver(receiver);
				receiver = null;
			} catch (IllegalArgumentException ex) {
			}

		}
	}

	private void unregisterStateReceiver() {
		if (stateReceiver != null) {
			stateReceiver.setListener(null);
			try {
				unregisterReceiver(stateReceiver);
				stateReceiver = null;
			} catch (IllegalArgumentException ex) {
			}

		}
	}

	@Override
	public void onSwitchingFinished() {
		if (bluetoothHelper != null) {
			unregisterStateReceiver();
			restartingBlue = false;
			Log.i(T, "switching finished");
			// if a scan was postponed due to the adapter being restarted, do it
			// now, otherwise start listening
			if (mScanPending) {
				Log.d(T, "executing pending scan");
				initiateScanningRound(1);
			} else {
				Log.d(T, "no pending scan -> listen");
				bluetoothHelper.start();
			}
		}

	}

};
