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

import org.apache.http.util.ByteArrayBuffer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlarmManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
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
import android.os.RemoteException;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.twitter.DirectMessages;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.InternalStorageHelper;
import ch.ethz.twimight.util.SDCardHelper;
import fi.tkk.netlab.dtn.scampi.android.SCAMPIApplication;
import fi.tkk.netlab.dtn.scampi.android.SCAMPIServiceListener;
import fi.tkk.netlab.dtn.scampi.applib.AppLib;
import fi.tkk.netlab.dtn.scampi.applib.AppLibListener;
import fi.tkk.netlab.dtn.scampi.applib.HostDiscoveryCallback;
import fi.tkk.netlab.dtn.scampi.applib.SCAMPIMessage;

/**
 * This is the thread for scanning for Bluetooth peers.
 * 
 * @author theus
 * @author pcarta
 */

public class ScanningService extends Service implements AppLibListener,
		SCAMPIServiceListener, HostDiscoveryCallback {

	private static ScanningService instance;
	private static final String TAG = "ScanningService";
	/** For Debugging */
	private static final String WAKE_LOCK = "ScanningServiceWakeLock";

	public Handler handler;
	/** Handler for delayed execution of the thread */

	// private Date lastScan;

	private MacsDBHelper dbHelper;
	StateChangedReceiver stateReceiver;
	private Cursor cursor;

	WakeLock wakeLock;
	public boolean closing_request_sent = false;

	public static final int STATE_SCANNING = 1;
	public static final int STATE_IDLE = 0;
	private static final long CONNECTING_TIMEOUT = 8000L;
	private static final long CONNECTION_TIMEOUT = 10000L;

	private static final String TYPE = "message_type";
	public static final int TWEET = 0;
	public static final int DM = 1;
	public static final int PHOTO = 2;
	public static final int HTML = 3;

	public static final String FORCED_BLUE_SCAN = "forced_bluetooth_scan";

	// photo
	private String photoPath;
	private static final String PHOTO_PATH = "twimight_photos";
	private static final String MSG_KEY_TWEETS = "tweets";

	// html
	private HtmlPagesDbHelper htmlDbHelper;

	// SDcard helper
	private SDCardHelper sdCardHelper;
	// SDcard checking var
	boolean isSDAvail = false;
	boolean isSDWritable = false;
	File SDcardPath = null;

	volatile boolean restartingBlue = false;

	// has a scan been skipped because the adapter was restarting?
	private boolean mScanPending = false;

	// SCAMPI
	private SCAMPIApplication scampiApp = null;
	private static final int APPLIB_RETRY_MS = 500;
	private static final String TWIMIGHT_SERVICE_NAME = "twimight";
	private AppLib scampiAppLib = null;

	@Override
	public void onCreate() {
		// set up scampi
		if (scampiApp == null) {
			scampiApp = (SCAMPIApplication) getApplication();
		}
		scampiApp.startSCAMPIService();
		scampiApp.startAppLib(this);
		Log.d(TAG, "onCreate() scampiApp.startSCAMPIService()");
		// TODO Auto-generated method stub
		super.onCreate();
		instance = this;
		handler = new Handler();

		// sdCard helper
		sdCardHelper = new SDCardHelper();
		// htmldb helper
		htmlDbHelper = new HtmlPagesDbHelper(getApplicationContext());
		htmlDbHelper.open();

	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		super.onStartCommand(intent, flags, startId);
		Log.d(TAG, "onStartCommand()");
		// Thread.setDefaultUncaughtExceptionHandler(new

		getWakeLock(this);

		if (intent != null && intent.getBooleanExtra(FORCED_BLUE_SCAN, true)) {
			Log.d(TAG, "force send disaster tweets");
			sendDisasterTweets(0L);
		}

		return START_STICKY;
	}

	public class CustomExceptionHandler implements UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread t, Throwable e) {
			Log.e(TAG, "error ", e);
			ScanningService.this.stopSelf();
			AlarmManager mgr = (AlarmManager) LoginActivity.getInstance()
					.getSystemService(Context.ALARM_SERVICE);
			mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
					LoginActivity.getRestartIntent());
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

		PowerManager mgr = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);
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
		// Make sure we're not doing discovery anymore
		scampiApp.stopAppLib();
		scampiApp.stopSCAMPIService();
		super.onDestroy();
	}

	/**
	 * The Handler that gets information back from the BluetoothService
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {

			case Constants.MESSAGE_READ:

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
//			JSONArray jarray;
			JSONObject o;
			try {
				// if input parameter is String, then cast it to String
				// jarray = new JSONArray(s[0]);
				// for (int i = 0; i < jarray.length(); i++) {
				o = new JSONObject(s[0]);
				// o = jarray.getJSONObject(i);
				if (o.getInt(TYPE) == TWEET) {
					Log.d("disaster", "receive a tweet");
					processTweet(o);
				} else if (o.getInt(TYPE) == PHOTO) {
					Log.d("disaster", "receive a photo");
					processPhoto(o);
				} else if (o.getInt(TYPE) == HTML) {
					Log.d("disaster", "receive xml");
					processHtml(o);
				} else {
					Log.d("disaster", "receive a dm");
					processDM(o);
				}

				getContentResolver().notifyChange(Tweets.TABLE_TIMELINE_URI,
						null);
				// if input parameter is a photo, then extract the photo and
				// save it locally
				// }

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
			if (!dmValues
					.getAsLong(DirectMessages.COL_SENDER)
					.toString()
					.equals(LoginActivity.getTwitterId(getApplicationContext()))) {

				ContentValues cvUser = getUserCV(o);
				// insert the tweet
				Uri insertUri = Uri.parse("content://"
						+ DirectMessages.DM_AUTHORITY + "/"
						+ DirectMessages.DMS + "/" + DirectMessages.DMS_LIST
						+ "/" + DirectMessages.DMS_SOURCE_DISASTER);
				getContentResolver().insert(insertUri, dmValues);

				// insert the user
				Uri insertUserUri = Uri.parse("content://"
						+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
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
			if (!cvTweet
					.getAsLong(Tweets.COL_TWITTERUSER)
					.toString()
					.equals(LoginActivity.getTwitterId(getApplicationContext()))) {

				ContentValues cvUser = getUserCV(o);

				// insert the tweet
				Uri insertUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY
						+ "/" + Tweets.TWEETS + "/"
						+ Tweets.TWEETS_TABLE_TIMELINE + "/"
						+ Tweets.TWEETS_SOURCE_DISASTER);
				getContentResolver().insert(insertUri, cvTweet);

				// insert the user
				Uri insertUserUri = Uri.parse("content://"
						+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
						+ TwitterUsers.TWITTERUSERS);
				getContentResolver().insert(insertUserUri, cvUser);
			}

		} catch (JSONException e1) {
			Log.e(TAG, "Exception while receiving disaster tweet ", e1);
		}

	}

	private void processPhoto(JSONObject o) {
		try {
			Log.i(TAG, "processPhoto");
			String jsonString = o.getString("image");
			String userID = o.getString("userID");
			String photoFileName = o.getString("photoName");
			// locate the directory where the photos are stored
			photoPath = PHOTO_PATH + "/" + userID;
			String[] filePath = { photoPath };
			if (sdCardHelper.checkSDState(filePath)) {
				File targetFile = sdCardHelper.getFileFromSDCard(photoPath,
						photoFileName);// photoFileParent, photoFilename));
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

			String[] filePath = { HtmlPage.HTML_PATH + "/"
					+ LoginActivity.getTwitterId(getApplicationContext()) };
			if (sdCardHelper.checkSDState(filePath)) {
				File targetFile = sdCardHelper.getFileFromSDCard(filePath[0],
						filename);// photoFileParent, photoFilename));
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

		Uri uriQuery = Uri.parse("content://" + DirectMessages.DM_AUTHORITY
				+ "/" + DirectMessages.DMS + "/" + DirectMessages.DMS_LIST
				+ "/" + DirectMessages.DMS_SOURCE_DISASTER);
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
		Log.i(TAG, "inside sendDisasterTweets");
		Uri queryUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/"
				+ Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/"
				+ Tweets.TWEETS_SOURCE_DISASTER);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);
		Log.d(TAG, "sendDisasterTweets: " + c.getCount() + " tweets");
		if (c.getCount() > 0) {
			c.moveToFirst();
			while (!c.isAfterLast()) {

				if (c.getLong(c.getColumnIndex(Tweets.COL_RECEIVED)) > (last - 5000)) {
					JSONObject toSend;
					try {
						toSend = getJSON(c);
						if (toSend != null) {
							if (scampiAppLib != null) {
								SCAMPIMessage msg = new SCAMPIMessage();
								msg.put(MSG_KEY_TWEETS, toSend.toString());
								scampiAppLib
										.publish(msg, TWIMIGHT_SERVICE_NAME);
								Log.d(TAG, "Publish msg");
							}
						}

					} catch (JSONException e) {
						Log.e(TAG, "exception ", e);
					}
				}
				c.moveToNext();
			}
			// send data here

		}

		c.close();
	}

	private boolean sendDisasterPhoto(Cursor c) throws JSONException {
		JSONObject toSendPhoto;
		String photoFileName = c.getString(c.getColumnIndex(Tweets.COL_MEDIA));
		Log.d("photo", "photo name:" + photoFileName);
		String userID = String.valueOf(c.getLong(c
				.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID)));
		// locate the directory where the photos are stored
		photoPath = Tweets.PHOTO_PATH + "/" + userID;

		try {
			String base64Photo = sdCardHelper.getAsBas64Jpeg(photoPath,
					photoFileName, 500);
			toSendPhoto = new JSONObject("{\"image\":\"" + base64Photo + "\"}");
			toSendPhoto.put(TYPE, PHOTO);
			toSendPhoto.put("userID", userID);
			toSendPhoto.put("photoName", photoFileName);
			// bluetoothHelper.write(toSendPhoto.toString());
			return true;
		} catch (FileNotFoundException e) {
			Log.d(TAG, "Can't open file. Not sending photo.", e);
		}

		return false;
	}

	private void sendDisasterHtmls(Cursor c) throws JSONException {

		JSONObject toSendXml;

		String userId = String.valueOf(c.getLong(c
				.getColumnIndex(Tweets.COL_TWITTERUSER)));

		String substr = Html.fromHtml(
				c.getString(c.getColumnIndex(Tweets.COL_TEXT))).toString();

		String[] strarr = substr.split(" ");

		// check the urls of the tweet
		for (String subStrarr : strarr) {

			if (subStrarr.indexOf("http://") >= 0
					|| subStrarr.indexOf("https://") >= 0) {
				String subUrl = null;
				if (subStrarr.indexOf("http://") >= 0) {
					subUrl = subStrarr.substring(subStrarr.indexOf("http://"));
				} else if (subStrarr.indexOf("https://") >= 0) {
					subUrl = subStrarr.substring(subStrarr.indexOf("https://"));
				}
				Cursor cursorHtml = htmlDbHelper.getPageInfo(subUrl);

				if (cursorHtml != null) {

					if (!cursorHtml.isNull(cursorHtml
							.getColumnIndex(HtmlPage.COL_FILENAME))) {

						String[] filePath = { HtmlPage.HTML_PATH + "/"
								+ LoginActivity.getTwitterId(this) };
						String filename = cursorHtml.getString(cursorHtml
								.getColumnIndex(HtmlPage.COL_FILENAME));
						Long tweetId = cursorHtml.getLong(cursorHtml
								.getColumnIndex(HtmlPage.COL_DISASTERID));
						if (sdCardHelper.checkSDState(filePath)) {

							File xmlFile = sdCardHelper.getFileFromSDCard(
									filePath[0], filename);
							if (xmlFile.exists()) {
								toSendXml = getJSONFromXml(xmlFile);
								toSendXml.put(HtmlPage.COL_URL, subUrl);
								toSendXml.put(HtmlPage.COL_FILENAME, filename);
								toSendXml.put(HtmlPage.COL_DISASTERID, tweetId);
								Log.d(TAG, "sending htmls");
								Log.d(TAG, toSendXml.toString(5));
								// bluetoothHelper.write(toSendXml.toString());

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

			try {
				FileInputStream xmlStream = new FileInputStream(xml);
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
			}
			jsonObj.put(TYPE, HTML);
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

		if (c.getColumnIndex(DirectMessages.COL_RECEIVER) < 0
				|| c.getColumnIndex(DirectMessages.COL_SENDER) < 0
				|| c.isNull(c.getColumnIndex(DirectMessages.COL_CRYPTEXT))) {
			Log.i(TAG, "missing users data");
			return null;

		} else {
			o.put(TYPE, DM);
			o.put(DirectMessages.COL_DISASTERID,
					c.getLong(c.getColumnIndex(DirectMessages.COL_DISASTERID)));
			o.put(DirectMessages.COL_CRYPTEXT,
					c.getString(c.getColumnIndex(DirectMessages.COL_CRYPTEXT)));
			o.put(DirectMessages.COL_SENDER,
					c.getString(c.getColumnIndex(DirectMessages.COL_SENDER)));
			if (c.getColumnIndex(DirectMessages.COL_CREATED) >= 0)
				o.put(DirectMessages.COL_CREATED,
						c.getLong(c.getColumnIndex(DirectMessages.COL_CREATED)));
			o.put(DirectMessages.COL_RECEIVER,
					c.getLong(c.getColumnIndex(DirectMessages.COL_RECEIVER)));
			o.put(DirectMessages.COL_RECEIVER_SCREENNAME, c.getString(c
					.getColumnIndex(DirectMessages.COL_RECEIVER_SCREENNAME)));
			o.put(DirectMessages.COL_DISASTERID,
					c.getLong(c.getColumnIndex(DirectMessages.COL_DISASTERID)));
			o.put(DirectMessages.COL_SIGNATURE,
					c.getString(c.getColumnIndex(DirectMessages.COL_SIGNATURE)));
			o.put(DirectMessages.COL_CERTIFICATE, c.getString(c
					.getColumnIndex(DirectMessages.COL_CERTIFICATE)));
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
		if (c.getColumnIndex(Tweets.COL_TWITTERUSER) < 0
				|| c.getColumnIndex(TwitterUsers.COL_SCREENNAME) < 0) {
			Log.i(TAG, "missing user data");
			return null;
		}

		else {

			o.put(Tweets.COL_TWITTERUSER,
					c.getLong(c.getColumnIndex(Tweets.COL_TWITTERUSER)));
			o.put(TYPE, TWEET);
			o.put(TwitterUsers.COL_SCREENNAME,
					c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
			if (c.getColumnIndex(Tweets.COL_CREATED) >= 0)
				o.put(Tweets.COL_CREATED,
						c.getLong(c.getColumnIndex(Tweets.COL_CREATED)));
			if (c.getColumnIndex(Tweets.COL_CERTIFICATE) >= 0)
				o.put(Tweets.COL_CERTIFICATE,
						c.getString(c.getColumnIndex(Tweets.COL_CERTIFICATE)));
			if (c.getColumnIndex(Tweets.COL_SIGNATURE) >= 0)
				o.put(Tweets.COL_SIGNATURE,
						c.getString(c.getColumnIndex(Tweets.COL_SIGNATURE)));

			if (c.getColumnIndex(Tweets.COL_TEXT) >= 0)
				o.put(Tweets.COL_TEXT,
						c.getString(c.getColumnIndex(Tweets.COL_TEXT)));
			if (c.getColumnIndex(Tweets.COL_REPLYTO) >= 0)
				o.put(Tweets.COL_REPLYTO,
						c.getLong(c.getColumnIndex(Tweets.COL_REPLYTO)));
			if (c.getColumnIndex(Tweets.COL_LAT) >= 0)
				o.put(Tweets.COL_LAT,
						c.getDouble(c.getColumnIndex(Tweets.COL_LAT)));
			if (c.getColumnIndex(Tweets.COL_LNG) >= 0)
				o.put(Tweets.COL_LNG,
						c.getDouble(c.getColumnIndex(Tweets.COL_LNG)));
			if (c.getColumnIndex(Tweets.COL_MEDIA) >= 0)
				o.put(Tweets.COL_MEDIA,
						c.getString(c.getColumnIndex(Tweets.COL_MEDIA)));
			if (c.getColumnIndex(Tweets.COL_HTML_PAGES) >= 0)
				o.put(Tweets.COL_HTML_PAGES,
						c.getString(c.getColumnIndex(Tweets.COL_HTML_PAGES)));
			if (c.getColumnIndex(Tweets.COL_SOURCE) >= 0)
				o.put(Tweets.COL_SOURCE,
						c.getString(c.getColumnIndex(Tweets.COL_SOURCE)));

			if (c.getColumnIndex(Tweets.COL_TID) >= 0
					&& !c.isNull(c.getColumnIndex(Tweets.COL_TID)))
				o.put(Tweets.COL_TID,
						c.getLong(c.getColumnIndex(Tweets.COL_TID)));

			if (c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH) >= 0
					&& c.getColumnIndex("userRowId") >= 0) {
				Log.i(TAG, "adding picture");
				int userId = c.getInt(c.getColumnIndex("userRowId"));
				Uri imageUri = Uri.parse("content://"
						+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
						+ TwitterUsers.TWITTERUSERS + "/" + userId);
				try {
					InputStream is = getContentResolver().openInputStream(
							imageUri);
					byte[] image = toByteArray(is);
					o.put(TwitterUsers.COL_PROFILEIMAGE,
							Base64.encodeToString(image, Base64.DEFAULT));

				} catch (Exception e) {
					Log.e(TAG, "error", e);

				}
				;
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

		if (o.has(Tweets.COL_CREATED))
			cv.put(Tweets.COL_CREATED, o.getLong(Tweets.COL_CREATED));

		if (o.has(Tweets.COL_TEXT)) {
			cv.put(Tweets.COL_TEXT, o.getString(Tweets.COL_TEXT));
			cv.put(Tweets.COL_TEXT_PLAIN,
					Html.fromHtml(o.getString(Tweets.COL_TEXT)).toString());
		}

		if (o.has(Tweets.COL_TWITTERUSER)) {
			cv.put(Tweets.COL_TWITTERUSER, o.getLong(Tweets.COL_TWITTERUSER));
		}

		if (o.has(Tweets.COL_TID)) {
			cv.put(Tweets.COL_TID, o.getLong(Tweets.COL_TID));
		}

		if (o.has(Tweets.COL_REPLYTO))
			cv.put(Tweets.COL_REPLYTO, o.getLong(Tweets.COL_REPLYTO));

		if (o.has(Tweets.COL_LAT))
			cv.put(Tweets.COL_LAT, o.getDouble(Tweets.COL_LAT));

		if (o.has(Tweets.COL_LNG))
			cv.put(Tweets.COL_LNG, o.getDouble(Tweets.COL_LNG));

		if (o.has(Tweets.COL_SOURCE))
			cv.put(Tweets.COL_SOURCE, o.getString(Tweets.COL_SOURCE));

		if (o.has(Tweets.COL_MEDIA))
			cv.put(Tweets.COL_MEDIA, o.getString(Tweets.COL_MEDIA));

		if (o.has(Tweets.COL_HTML_PAGES))
			cv.put(Tweets.COL_HTML_PAGES, o.getString(Tweets.COL_HTML_PAGES));

		if (o.has(TwitterUsers.COL_SCREENNAME)) {
			cv.put(Tweets.COL_SCREENNAME,
					o.getString(TwitterUsers.COL_SCREENNAME));
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
			cv.put(DirectMessages.COL_CERTIFICATE,
					o.getString(DirectMessages.COL_CERTIFICATE));

		if (o.has(DirectMessages.COL_SIGNATURE))
			cv.put(DirectMessages.COL_SIGNATURE,
					o.getString(DirectMessages.COL_SIGNATURE));

		if (o.has(DirectMessages.COL_CREATED))
			cv.put(DirectMessages.COL_CREATED,
					o.getLong(DirectMessages.COL_CREATED));

		if (o.has(DirectMessages.COL_CRYPTEXT))
			cv.put(DirectMessages.COL_CRYPTEXT,
					o.getString(DirectMessages.COL_CRYPTEXT));

		if (o.has(DirectMessages.COL_DISASTERID))
			cv.put(DirectMessages.COL_DISASTERID,
					o.getLong(DirectMessages.COL_DISASTERID));

		if (o.has(DirectMessages.COL_SENDER))
			cv.put(DirectMessages.COL_SENDER,
					o.getLong(DirectMessages.COL_SENDER));

		if (o.has(DirectMessages.COL_RECEIVER))
			cv.put(DirectMessages.COL_RECEIVER,
					o.getLong(DirectMessages.COL_RECEIVER));

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

		if (o.has(TwitterUsers.COL_SCREENNAME)) {
			screenName = o.getString(TwitterUsers.COL_SCREENNAME);
			cv.put(TwitterUsers.COL_SCREENNAME,
					o.getString(TwitterUsers.COL_SCREENNAME));

		}

		if (o.has(TwitterUsers.COL_PROFILEIMAGE) && screenName != null) {

			InternalStorageHelper helper = new InternalStorageHelper(
					getBaseContext());
			byte[] image = Base64.decode(
					o.getString(TwitterUsers.COL_PROFILEIMAGE), Base64.DEFAULT);
			helper.writeImage(image, screenName);
			cv.put(TwitterUsers.COL_PROFILEIMAGE_PATH, new File(getFilesDir(),
					screenName).getPath());

		}

		if (o.has(Tweets.COL_TWITTERUSER)) {
			cv.put(TwitterUsers.COL_TWITTERUSER_ID,
					o.getLong(Tweets.COL_TWITTERUSER));

		}
		cv.put(TwitterUsers.COL_ISDISASTER_PEER, 1);

		return cv;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public IBinder asBinder() {
		return null;
	}

	@Override
	public void scampiCoreRunning(boolean running) throws RemoteException {
		Log.d(TAG, "scampiCoreRunning()");
		if (!running) {
			Log.e(TAG, "could not start SCAMPI Router");
			// "BackgroundService notified error in starting the router");
			Toast.makeText(this, "Could not start SCAMPI Router",
					Toast.LENGTH_LONG).show();
			;
		}
	}

	@Override
	public void connected(AppLib appLib) {
		Log.d(TAG, "Connected to the applib");
		scampiAppLib = appLib;
		scampiAppLib.subscribe(TWIMIGHT_SERVICE_NAME);
		scampiAppLib.startHostDiscovery(this);
	}

	@Override
	public void error(AppLib appLib, Exception e) {
		Log.d(TAG, "error()");
		Log.e(TAG, Log.getStackTraceString(e));
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				scampiApp.startAppLib(ScanningService.this);
				// scampiApp.connectAppLib();
			}
		}, APPLIB_RETRY_MS);
	}

	@Override
	public void messageReceived(AppLib appLib, String service,
			SCAMPIMessage message) {
		Log.d(TAG, "messageReceived(), service=" + service);
		if (TWIMIGHT_SERVICE_NAME.equals(service)) {
			String tweets = message.getAsString(MSG_KEY_TWEETS);
			new ProcessDataReceived().execute(tweets);
		}
	}

	@Override
	public void hostDiscovered(AppLib appLib, String host, int hopCount,
			long timestamp, double longitude, double latitude,
			double locationError) {
		Log.d(TAG, "host discovered: " + host);
		sendDisasterTweets(0L);
	}

}
