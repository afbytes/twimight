package ch.ethz.twimight.net.opportunistic;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.twimight.DirectMessages;
import ch.ethz.twimight.Timeline;
import ch.ethz.twimight.TweetContextActions;
import ch.ethz.twimight.TwimightActivity;
import ch.ethz.twimight.UpdaterService;
import ch.ethz.twimight.showDisasterDb;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.TweetDbActions;
import ch.ethz.twimight.net.RSACrypto;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.net.twitter.OAUTH;
import ch.ethz.twimight.net.opportunistic.packets.AbstractPacket;
import ch.ethz.twimight.net.opportunistic.packets.DirectMessage;
import ch.ethz.twimight.net.opportunistic.packets.HelloPacket;
import ch.ethz.twimight.net.opportunistic.packets.SignedTweet;
import ch.ethz.twimight.net.opportunistic.packets.TweetPacket;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.LogFilesOperations;

/**
 * Service for running the disaster mode. This is the meat of the disaster mode!
 * Implementing the protocol for epidemice Tweet exchange.
 * @author pcarta
 *
 */
public class DisasterOperations extends Service {

	//Message types sent from the BluetoothService Handler	  
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_DELAY = 6;
	public static final int MESSAGE_TOAST =8 ;

	// Key names received from the BluetoothService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String DEVICE_ADDRESS = "device address";
	public static final String TOAST = "toast";
	public static final String DEVICE = "device";
	public static final String PEERS_MET = "peers_met";

	//types of packets
	public static final int HELLO_FIRST_PACKET = 1 ;
	public static final int HELLO_SUCC_PACKET = 2 ;
	public static final int DATA_PACKET = 3 ;
	public static final int KEYS_PACKET = 4 ;

	static final int FALSE = 0;
	static final int TRUE = 1; 	

	private static final String TAG = "DisasterOperations";
	// Member object for the chat services
	static BluetoothComms mBlueService = null;
	ConnectToDevices connectToDevices;
	private Cursor cursorDisaster,cursorPeers;
	SharedPreferences mSettings,prefs;  
	private int numberOfConnAttempts,numberOfContacts;	
	static long numberOfTweets;
	ConnectionHelper connHelper;
	boolean isPartialSavingActive,isFullSavingActive,notify;
	// Name of the connected device
	private String mConnectedDeviceName = null;
	private String mConnectedDeviceAddress =  null;
	private ArrayList<BluetoothDevice> devicesArrayList = null; 
	TweetDbActions dbActions = UpdaterService.getDbActions();
	TweetContextActions contextActions;  
	WakeLock wakeLock;
	Handler handler;	
	boolean peerRequestedClosing =false;	 
	CheckState checkState = null;
	long startingTime,now, firstBroadcastTime = 0;
	String startingDate;
	private BluetoothAdapter mBtAdapter;

	FileWriter batteryWriter;
	//,rxTweetsWriter,startTimeWriter,customExceptionWriter,contactsTimestampsWriter;
	//FileWriter neighboursWriter,connAttemptsWriter;
	LogFilesOperations logOps;
	ConnectionAttemptTimeout connTimeout;
	String myUsername;
	ArrayList<BluetoothDevice> pairedDevices;
	DbOpenHelper dbHelper;
	static int connAttemptsSucceded=0;
	boolean alreadyReceived = false;
	long limit = 4;


	/**
	 * onCreate. Setup the Bluetooth communication, battery monitor, logging, etc.
	 */
	@Override
	public void onCreate() {

		super.onCreate();			
		prefs = PreferenceManager.getDefaultSharedPreferences(this);	    
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);		 
		myUsername = mSettings.getString("user", "");

		isPartialSavingActive =mSettings.getBoolean("isPartialSavingActive", false);
		isFullSavingActive = mSettings.getBoolean("isFullSavingActive", false);
		ConnectivityManager connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);	   
		contextActions = new TweetContextActions(connHelper,prefs,mSettings);

		// Register for broadcasts when a device is discovered
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		this.registerReceiver(mReceiver, filter);
		// Register for broadcasts when discovery has finished
		filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		this.registerReceiver(mReceiver, filter);  

		registerReceiver(mBatInfoReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();   
		devicesArrayList = new  ArrayList<BluetoothDevice>();
		handler = new Handler();
		connectToDevices = new ConnectToDevices();		

		PowerManager mgr = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
		wakeLock.acquire();

		logOps = new LogFilesOperations();
		logOps.createLogsFolder();
		batteryWriter = logOps.createLogFile("Battery_" + myUsername );
		// createLogFiles();
		// Initialize the BluetoothChatService to perform bluetooth connections
		mBlueService = new BluetoothComms(this, mHandler);    	      
		mBlueService.start();        
		// Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler()); 


	}


	/*
	 * method used for writing logs during the experiments
	 * 
	 * private void createLogFiles() {		
	 //creating files for logs
    logOps = new LogFilesOperations();
    logOps.createLogsFolder();

    rxTweetsWriter = logOps.createLogFile("Received_Tweets_" + user); // no saving into shared preferences   
    if (!mSettings.contains("numberOfConnAttempts")) {
    	startTimeWriter = logOps.createLogFile("startingTime_" + user);	 // no saving into shared preferences
    	startingTime = new Date().getTime();
    	startingDate = new Date().toString();
    	try {
    		startTimeWriter.write("starting time:" + startingTime + ":" + startingDate  + "\n");
    		startTimeWriter.close();
		} catch (IOException e) {}
    }      
   // neighboursWriter = logOps.createLogFile("NeighboursMac_" + user);
    connAttemptsWriter = logOps.createLogFile("connAttempts_" + user);
    contactsTimestampsWriter = logOps.createLogFile("contactsTimestamps_" + user);
} 
	 */



	public class CustomExceptionHandler implements UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread t, Throwable e) {		
			/*
			 * Writes error into logs
			 * 
			 * customExceptionWriter = logOps.createLogFile("CustomException_" + user);		 
		try {
			customExceptionWriter.write("CustomExceptionHandler executed " + new Date().toString() + "\n");
			Process process = Runtime.getRuntime().exec("logcat -d -t 1000 *:E");
		    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		    StringBuilder log=new StringBuilder();
		    String line;
		    while ((line = bufferedReader.readLine()) != null) {
		        log.append(line + "\n");
		    }		    
			customExceptionWriter.write(log.toString() + "\n");
		} 
		catch (IOException e1) {	}
			 */

			notifyCrash();		
			//closeService();		
			DisasterOperations.this.stopSelf();
			AlarmManager mgr = (AlarmManager) TwimightActivity.getInstance().getSystemService(Context.ALARM_SERVICE);
			mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 500, TwimightActivity.getRestartIntent());
			System.exit(2);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/*
	 * 
private void closeLogFiles() {
	try {
		//if (prefs.getBoolean("prefDisasterMode", false) == false) {		
			connAttemptsWriter.write("number of successful attempts till " + new Date().toString() + ":" + connAttemptsSucceded + "\n");
			connAttemptsWriter.write("number of attempts till " + new Date().toString() + ":" + numberOfConnAttempts + "\n");
		//}		

		rxTweetsWriter.close();			
		connAttemptsWriter.close();
		contactsTimestampsWriter.close();
		//neighboursWriter.close();
		if (customExceptionWriter != null)
			customExceptionWriter.close();
	  } 
	catch (IOException e) {}
}

	 */

	private void closeService() {	
		handler.post(new ShutDownDelayed());   
		// closeLogFiles();  
		try {
			batteryWriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// Unregister broadcast listener   
		try {
			unregisterReceiver(mReceiver);  
			unregisterReceiver(mBatInfoReceiver);
		} catch (Exception ex) {			 
		}		 

		handler.removeCallbacks(connectToDevices);  
		wakeLock.release();	 
	}

	@Override
	public void onDestroy() { 

		closeService();	 
		connAttemptsSucceded=0;
		super.onDestroy();	       
	}

	class ShutDownDelayed implements Runnable {

		@Override
		public void run() {
			if (mBlueService != null) {
				if (mBlueService.getState() != BluetoothComms.STATE_CONNECTING &&
						mBlueService.getState() != BluetoothComms.STATE_CONNECTED) {
					mBlueService.stop();
					mBlueService = null;   	  		    			 
				} 
				else
					handler.postDelayed(this, 2000);  
			}
		}
	}

	/**
	 *  The BroadcastReceiver that listens for discovered devices (Bluetooth).
	 *  @author pcarta 
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				String action = intent.getAction();

				// When discovery finds a device
				if (BluetoothDevice.ACTION_FOUND.equals(action)) {

					// Get the BluetoothDevice object from the Intent
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);            	  
					// If it's already paired, skip it
					if (device.getBondState() != BluetoothDevice.BOND_BONDED) {            	  
						if (!devicesArrayList.contains(device)){    
							if (device.getBluetoothClass().getDeviceClass() == 
								BluetoothClass.Device.PHONE_SMART) {

								devicesArrayList.add(device);             			 
							}
						}           	
					}               
					// When discovery is finished...
				} else  if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {         

					// Get a set of currently paired devices       	 
					Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();        	 
					// If there are paired devices, add each one to the ArrayAdapter
					if (pairedDevices.size() > 0) {                
						for (BluetoothDevice device : pairedDevices) {
							devicesArrayList.add(device);
							Log.i(TAG,"added paired device: " + device.getAddress());
						}
					} 
					Log.i(TAG,"Discovery finished" );        	 
					if (mBlueService.getState() != BluetoothComms.STATE_CONNECTED) {        	    	         	    	
						long delay = Math.round(Math.random()*2000);        	    	
						handler.postDelayed(connectToDevices,delay);      	    	   	

					} else {        	    	    	    	
						checkState = new CheckState(5);
						checkState.execute();
					}           	            
				} 
			}
			catch (Exception ex) {}
		}
	};

	class CheckState extends AsyncTask<Void, Void, Void> {	
		int delay;

		CheckState(int delay) {
			this.delay = delay;
		}

		@Override
		protected Void doInBackground(Void... id ) {
			int i=0;
			try {
				while (mBlueService.getState() == BluetoothComms.STATE_CONNECTED) {
					try {
						if (i == delay) {
							mBlueService.start();
							numberOfTweets = 0;		    				
						}
						Thread.sleep(1000);
						i++;		    			
					} catch (Exception ex) {}			    		
				}						  
			}
			catch (Exception ex) {}	
			return null;
		}		

		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(Void result) {			
			handler.post(connectToDevices);				
			checkState =  null;
		}
	}

	private void notifyCrash() {	 
		Notification notification;
		NotificationManager notificationManager;
		PendingIntent pendingIntent;	    

		notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		notification = new Notification( android.R.drawable.stat_sys_download,
				"DisasterMode", System.currentTimeMillis() );
		pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, Timeline.class), 0);	

		// Create the notification
		notification.setLatestEventInfo(this, "Crash",
				"The disaster mode has crashed", pendingIntent );
		notification.when = System.currentTimeMillis();		 
		notificationManager.notify(20, notification);  	    	  
	}

	class ConnectionAttemptTimeout implements Runnable {
		@Override
		public void run() {
			if (mBlueService != null) {		 
				if (mBlueService.getState() == BluetoothComms.STATE_CONNECTING) {				
					mBlueService.cancelConnectionAttempt();
				}
				connTimeout = null;
			}
		}
	}

	// The Handler that gets information back from the BluetoothService
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {          

			case MESSAGE_READ:         	 
				if (msg.obj != null) {
					AbstractPacket ap = (AbstractPacket) msg.obj;                                                    
					processPacket(ap);	
				}        	              		               
				break;             
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				mConnectedDeviceAddress = msg.getData().getString(DEVICE_ADDRESS);           
				Log.i(TAG,"Connected to " + mConnectedDeviceName);
				numberOfTweets = 0;

				if (connTimeout != null) { // I need to remove the timeout started at the beginning
					handler.removeCallbacks(connTimeout);
					connTimeout = null;
				}
				//LOGS STUFF
				if (connAttemptsSucceded > 0) {
					SharedPreferences.Editor editor = mSettings.edit();
					editor.putInt("connAttemptsSucceded",connAttemptsSucceded );
					editor.commit();
				} 

				if (numberOfContacts == 0) { //restore variable in case of crash
					if (mSettings.contains("numberOfContacts")) {
						numberOfContacts = mSettings.getInt("numberOfContacts", 0);	

					}
				} 

				//query the peers db to see if it has already been met  
				cursorPeers = dbActions.peersDbQuery(mConnectedDeviceAddress);            
				if (cursorPeers != null) {
					int isPresent = cursorPeers.getCount();	               
					if (isPresent == 1) {
						cursorPeers.moveToFirst();//they have already met             		   
						if (!didTheyMeetRecently(cursorPeers) ) {
							incrementNumContacts();
							sendNumberOfTweets(mConnectedDeviceAddress);
						}

					} 
					else { //they have not met before
						incrementNumContacts();
						sendTimestampOfLastTweet();  
					}           	   
				}              
				break;              
			case MESSAGE_TOAST:             
				//need to connect to following phone
				Log.i(TAG,"Unable to connect to the device" ); 
				if (connTimeout != null) {
					handler.removeCallbacks(connTimeout);
					connTimeout = null;
				}
				handler.post(connectToDevices);             
				break;
			}
		}
	};

	class SentMessageTimeout implements Runnable {


		// public SentMessageTimeout(int method) {

		// }

		public void run() {
			if (mBlueService != null) {
				if (mBlueService.getState() == BluetoothComms.STATE_CONNECTED) {

				}
			}
		}
	}

	private void incrementNumContacts() {
		/* try {
		String date = new Date().toString(); 
		String date_r = date.replace(":", "-");
		//contactsTimestampsWriter.write("connected to:" + mConnectedDeviceAddress + ":" + mConnectedDeviceName + 
				 //":at:" + date_r + ":" + new Date().getTime() + "\n");
	} catch (IOException e) {	}
		 */
		numberOfContacts++;
		SharedPreferences.Editor editor = mSettings.edit();   
		editor.putInt("numberOfContacts", numberOfContacts);
		editor.commit();

	}

	private void sendTimestampOfLastTweet() {

		long recentTweetTime = dbActions.getTimestampLastTweet(mConnectedDeviceName);	  		  
		// if (mBlueService.getState() == BluetoothComms.STATE_CONNECTED)  {
		HelloPacket helloPacket = new HelloPacket(recentTweetTime, -1, HELLO_FIRST_PACKET);
		mBlueService.write(helloPacket);  
		Log.i(TAG, "timestamp of last tweet sent: " +  recentTweetTime); 
		if (checkState == null) {
			checkState = new CheckState(8);
			checkState.execute();	
		}
		// } else {Log.i(TAG, "STATE is NOT CONNECTED"); }	  	 
	}

	private void sendNumberOfTweets(String address) {
		try {	 

			int index = cursorPeers.getColumnIndex(DbOpenHelper.C_TWEETS_NUMBER);
			if (index != -1)
				numberOfTweets = cursorPeers.getLong(index);


			//if (mBlueService.getState() == BluetoothComms.STATE_CONNECTED)  {
			HelloPacket helloPacket = new HelloPacket(new Date().getTime(),numberOfTweets , HELLO_SUCC_PACKET);
			if (mBlueService != null)
				mBlueService.write(helloPacket);  
			Log.i(TAG, "sent: the number of tweets seen last time is " + numberOfTweets); 
			if (checkState == null) {
				checkState = new CheckState(8);
				checkState.execute();	
			}
			// } 
			// else {Log.i(TAG, "sendNumberOfTweets: STATE is NOT CONNECTED"); }
		} 
		catch (IllegalArgumentException ex) {}	  
	}  

	private void notifyUser(String message, String title, int notId, PendingIntent pend) {	 
		Notification notification;
		NotificationManager notificationManager;
		PendingIntent pendingIntent;	    

		notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		notification = new Notification( android.R.drawable.stat_sys_download,
				"DisasterMode", System.currentTimeMillis() );
		pendingIntent = pend;

		// Create the notification
		notification.setLatestEventInfo(this, title, message, pendingIntent );

		notification.when = System.currentTimeMillis(); 	 	
		notification.defaults |= Notification.DEFAULT_LIGHTS;
		if (prefs.getBoolean("prefVibration", false))
			notification.defaults |= Notification.DEFAULT_VIBRATE;
		notificationManager.notify(notId, notification);  	    	  
	}

	private void extractDirectMessages(DirectMessage directMessage) {

		if (directMessage.recipientUser.equals(myUsername)) {
			Log.i(TAG,"I am the recipient of the message, decrypting it");
			//NEED TO DECRYPT AND SAVE INTO INCOMING DIRECT MESSAGES 

			RSACrypto crypto = new RSACrypto(mSettings);  
			String message = crypto.decrypt(directMessage.message);
			String sender = crypto.decrypt(directMessage.sender);
			Log.i(TAG,"message = " + message);
			Log.i(TAG,"sender = " + sender);
			if(message != null && sender != null) {

				ContentValues values = new ContentValues();	 
				values.put(DbOpenHelper.C_ID, directMessage.id);
				values.put(DbOpenHelper.C_CREATED_AT, directMessage.created_at);
				values.put(DbOpenHelper.C_USER,sender );
				values.put(DbOpenHelper.C_TEXT, message);	
				values.put(DbOpenHelper.C_IS_DISASTER, TRUE);
				Log.i(TAG,"trying to insert into the incoming table");
				if ( dbActions.insertGeneric(DbOpenHelper.TABLE_DIRECT, values) )
					notify = true;
			}

		}
		else {
			//insert into the outgoing table since I am not the recipient and I cannot decrypt
			ContentValues values = new ContentValues();
			values.put(DbOpenHelper.C_ID, directMessage.id);
			values.put(DbOpenHelper.C_CREATED_AT, directMessage.created_at);
			values.put(DbOpenHelper.C_USER_RECIPIENT, directMessage.recipientUser);

			values.put(DbOpenHelper.C_USER, directMessage.sender);
			values.put(DbOpenHelper.C_IS_DISASTER, TRUE);
			values.put(DbOpenHelper.C_HASBEENSENT, directMessage.hasBeenSent);
			values.put(DbOpenHelper.C_TEXT, directMessage.message);	 	
			Log.i(TAG,"trying to insert into the outgoing table");
			dbActions.insertGeneric(DbOpenHelper.TABLE_DIRECT_OUTGOING, values);
		}

	}


	private void extractFields(SignedTweet readMessage) {
		int hasBeenSent = FALSE;
		RSACrypto crypto = new RSACrypto(mSettings);

		Log.i(TAG, "extractFields");

		if (readMessage != null) {
			Log.i(TAG, "readMessage not null");
			if (readMessage.publicKey != null) {
				Log.i(TAG, "readMessage.publicKey not null");
				// I  NEED TO VERIFY THE SIGNATURE
				if (crypto.verifySignature(readMessage)) {

					Log.i(TAG, "Signature verified");
					long id = readMessage.id;
					long created = readMessage.created;
					long userId = readMessage.userId;
					String status = readMessage.status;
					String user = readMessage.user;	  				
					int isFromServer = readMessage.isFromServer; //I need to know if it is a retweet spreaded locally   	    		 
					if (isFromServer == FALSE)
						hasBeenSent = TRUE; //if it is a local tweet I will not publish it so i can set like it has been sent
					else
						hasBeenSent = readMessage.hasBeenSent;	    		 	
					int hopCount = readMessage.hopCount + 1;
					byte[] signature = readMessage.signature;

					Log.i(TAG, "fields read");
					//NEED TO SAVE USER AND PUBLIC KEY 
					ContentValues values = new ContentValues();
					values.put(DbOpenHelper.C_USER, user );
					values.put(DbOpenHelper.C_ID, userId);		

					Cursor cursor = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS,DbOpenHelper.C_ID + "=" + userId, null,null);
					if (cursor != null) {
						if(cursor.getCount() == 0) {
							values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.TRUE);						
						}					
					}				
					values.put(DbOpenHelper.C_MODULUS, readMessage.publicKey.getModulus().toString() );
					values.put(DbOpenHelper.C_EXPONENT, readMessage.publicKey.getPublicExponent().toString());


					dbActions.insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);

					Log.i(TAG, "friend inserted");

					// try to save into the disaster table	
					if (dbActions != null) {	    		

						if (dbActions.saveIntoDisasterDb(id, created, now, status, userId,
								mConnectedDeviceName,isFromServer,hasBeenSent,TRUE, hopCount,signature)) {

							Log.i(TAG, "saved Into Disaster DB");

							// if success notify the user and copy into the timeline table
							notify = true;
							sendBroadcast(new Intent(Constants.ACTION_NEW_DISASTER_TWEET));	    			 		
							//long delay = Math.round( (now/ 60000) - (created / 60000) );	    			 		
							/*
							 * try {
	    			 			 if (rxTweetsWriter != null) {
	    			 				 String date =  new Date().toString();
	    			 				 long nowForReceiver = new Date().getTime();
	    			 				rxTweetsWriter.write(id + ":" + user + ":" + now + ":" + delay + ":" 
   			 							 + hopCount + ":" + nowForReceiver + ":" + date + ":" + created + "\n");
	    			 			 }	    			 					 
	    			 		 } catch (IOException e) {	Log.i(TAG,"io exception");	} 
							 */
							if (!status.contains(Constants.TWINTERNAL_HASHTAG)) {	    			 			 
								dbActions.copyIntoTimelineTable(id,created,status,userId,isFromServer);	    			 					    	    			 			 
							}
						}
					}

				}

			}
		}

	} 

	private void processPacket(AbstractPacket ap) {
		long number = 0;	
		HelloPacket helloPacket = null;

		switch (ap.type) {

		case HELLO_FIRST_PACKET:		 
			helloPacket = (HelloPacket)ap;			  
			long time = helloPacket.timestamp;		
			Log.i(TAG,"content: last tweet seen at " + time);
			cursorDisaster = dbActions.disasterDbQuery(null," DESC");
			sendRelevantTweets(0,time);				  
			//need to memorize they met so that I can avoid further connections for a while
			dbActions.savePairedPeer(mConnectedDeviceAddress,-1);			
			break;

		case HELLO_SUCC_PACKET:
			helloPacket = (HelloPacket)ap;	
			number = helloPacket.messagesSeenLastTime;
			Log.i(TAG,"content: # messages seen last time " + number);
			cursorDisaster = dbActions.disasterDbQuery(null," DESC");
			if (cursorDisaster != null) {		 			  
				if (  cursorDisaster.getCount() > number  ) {	  			
					sendRelevantTweets(number,0);
				} 
				else if (cursorDisaster.getCount() < number) {	  			
					sendRelevantTweets(0,0);		 	  		
				}
				else {	  			
					handler.postDelayed(new SendClosingRequest(), 200);
				}					  
			}					  
			dbActions.savePairedPeer(mConnectedDeviceAddress,-1);  
			break;
		case DATA_PACKET:
			TweetPacket dataPacket = (TweetPacket)ap;		 
			ArrayList<SignedTweet> tweetsList = dataPacket.tweets;		 		
			numberOfTweets = dataPacket.actualTweetNumber;

			if (tweetsList != null && tweetsList.size() <= limit) {
				Log.i(TAG,"tweetsList.size() =  " + tweetsList.size());
				now = dataPacket.timestamp;
				Log.i(TAG,"data received, #  " + numberOfTweets + " ");
				for (SignedTweet tweet: tweetsList ) {		    
					extractFields(tweet);		   
				}	
				if (notify) {
					PendingIntent pend = PendingIntent.getActivity(this, 0, new Intent(this, showDisasterDb.class), 0);
					notifyUser("New Disaster Tweets","You have new tweets in the database", Constants.NOTIFICATION_ID, pend);
					notify = false;
				}

				dbActions.savePairedPeer(mConnectedDeviceAddress,numberOfTweets);	
			}	

			if ( dataPacket.directmessages != null  && dataPacket.directmessages.size() <= limit ) {
				ArrayList<DirectMessage> directMessagesList = dataPacket.directmessages;			
				numberOfTweets = dataPacket.actualTweetNumber;

				for (DirectMessage directMessage : directMessagesList) {
					extractDirectMessages(directMessage);
				}
				if (notify) {
					PendingIntent pend = PendingIntent.getActivity(this, 0, new Intent(this, DirectMessages.class), 0);
					notifyUser("New Direct Message","You have new disaster direct messages in the database", Constants.DIRECT_NOTIFICATION_ID,pend);
					notify = false;
				}			
				dbActions.savePairedPeer(mConnectedDeviceAddress,numberOfTweets);	
			}	

			if (dataPacket.closeConnection) {
				if (mBlueService != null) {				  
					mBlueService.start();	
					numberOfTweets = 0;
					handler.post(connectToDevices);
				}
			} 
			break;

		case KEYS_PACKET:
			break;

		}	 
	} 

	private boolean didTheyMeetRecently(Cursor cursorPeers) {	 
		try {
			long met_at = cursorPeers.getLong(cursorPeers.getColumnIndexOrThrow(DbOpenHelper.C_MET_AT));
			//String mac =  cursorPeers.getString(cursorPeers.getColumnIndexOrThrow(DbOpenHelper.C_MAC));
			long diff = (new Date().getTime() - met_at); 		
			if (TimeUnit.MILLISECONDS.toSeconds(diff) < 50 )
				return true;
			else
				return false;
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	class ConnectToDevices implements Runnable {	

		public void run( ) {

			if (!devicesArrayList.isEmpty()) {		   
				//remove the devices that have been met recently
				for (int i=0; i< devicesArrayList.size(); i++) {
					cursorPeers = dbActions.peersDbQuery(devicesArrayList.get(i).getAddress());
					if (cursorPeers != null) {
						if (cursorPeers.getCount() == 1) {
							cursorPeers.moveToFirst();	            		   
							if (didTheyMeetRecently(cursorPeers) ) {	            			   
								try {
									devicesArrayList.remove(i);  	            				   
								} 
								catch (IndexOutOfBoundsException ex) {}
							}
						}
						cursorPeers.moveToNext();
					}  	 				
				}
			}
			if (!devicesArrayList.isEmpty()) {
				if (mBlueService != null ) { 				 
					if (mBlueService.getState() != BluetoothComms.STATE_CONNECTED &&
							mBlueService.getState() != BluetoothComms.STATE_CONNECTING) { 					
						try {
							BluetoothDevice phone = devicesArrayList.remove(0);
							// Attempt to connect to the device
							Log.i(TAG,"connection attempt to " + phone.getName());	 					  	 					  
							connTimeout = new ConnectionAttemptTimeout();
							handler.postDelayed(connTimeout, 10000); //timeout for the conn attempt	 	
							mBlueService.connect(phone);	
							//update for the logs
							if (connAttemptsSucceded == 0) {
								//i need to check whether we are restarting after crashing or not
								if (mSettings.contains("connAttemptsSucceded")) {
									connAttemptsSucceded = mSettings.getInt("connAttemptsSucceded", 0);	 							 	 							 
								}
							}
							if (numberOfConnAttempts == 0) {
								//i need to check whether we are restarting after crashing or not
								if (mSettings.contains("numberOfConnAttempts")) {
									numberOfConnAttempts = mSettings.getInt("numberOfConnAttempts", 0);		 							 

								}
							}	
							numberOfConnAttempts++;
							SharedPreferences.Editor editor = mSettings.edit();   
							editor.putInt("numberOfConnAttempts", numberOfConnAttempts);
							editor.commit();
						} catch (IndexOutOfBoundsException ex) {}
					} else
						handler.postDelayed(connectToDevices, 2000); 					  
				}	  
			}	   		 	
		}
	} 

	class SendClosingRequest implements Runnable {	
		TweetPacket dataPacket;

		public void run( ) {
			//String messageClose = "Close connection";
			if (mBlueService != null) {
				dataPacket = new TweetPacket(new Date().getTime(), null, null, cursorDisaster.getCount());
				mBlueService.write(dataPacket);			  
			}
			cursorDisaster = null;			 		 		
		}
	}

	private long computeNumberOfTweetsToBeSent(long number, long time) {
		boolean enableSent_by = false;
		int count = 0;		    	

		for (int i =0; i< (cursorDisaster.getCount()- number); i++) {

			long userId = cursorDisaster.getLong(cursorDisaster.getColumnIndex(DbOpenHelper.C_USER_ID));

			Cursor cursorFriends = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS, 
					DbOpenHelper.C_ID + "=" + userId,	null, null);
			if (cursorFriends.getCount() == 1) {	    		
				cursorFriends.moveToFirst();
				String user = cursorFriends.getString(cursorFriends.getColumnIndex(DbOpenHelper.C_USER));	    	
				String sent_by = cursorDisaster.getString(cursorDisaster.getColumnIndex(DbOpenHelper.C_SENT_BY));				    	
				// i dont wanna send tweets to the author or to whom gave them to me, so i must check it
				if (time == 0)
					enableSent_by = true;

				if (!user.equals(mConnectedDeviceName) && ( ( !sent_by.equals(mConnectedDeviceName) ) 
						|| enableSent_by  ) ) {

					int isValid = cursorDisaster.getInt(cursorDisaster.getColumnIndex(DbOpenHelper.C_IS_VALID));
					if (isValid == TRUE) {

						long received = cursorDisaster.getLong(cursorDisaster.getColumnIndex(DbOpenHelper.C_ADDED_AT)) ;
						//if it is the first encounter i send everything older than the timestamp received
						//that represents the most recent information received somehow from other nodes
						if (received > time ) {	
							count ++;
						} else 
							break;
					}

				}
			}			    	
			cursorDisaster.moveToNext();
		} 
		cursorDisaster.moveToFirst();
		Log.i(TAG, "number of tweets to be sent = " + count); 
		return count;  
	}

	private ArrayList<SignedTweet> addMyTweetsToListFirst(long number, long time) {
		ArrayList<SignedTweet> tweetList = new ArrayList<SignedTweet>();	 

		if (cursorDisaster != null) {
			if (cursorDisaster.getCount() > 0) {		    	

				for (int i =0; i< (cursorDisaster.getCount()- number); i++) {
					SignedTweet tweet = createSignedTweet(time); 
					if (tweet != null) {
						if (tweet.user.equals(mSettings.getString("user", "")))	{							
							tweetList.add(tweet);
							if (tweetList.size() >= (limit/2)) {		    					
								break;
							}
						}
					}
					if (!cursorDisaster.isLast())			    				    	
						cursorDisaster.moveToNext();
				}		    	
				return tweetList;
			}
		}

		return null;
	}

	private void setCursorPosition(long number) {
		cursorDisaster.moveToFirst();
		for(int i = 0; i<number; i++) {
			cursorDisaster.moveToNext();
		}
	}

	private SignedTweet createSignedTweet(long time) {
		boolean enableSent_by = false;

		long userId = cursorDisaster.getLong(cursorDisaster.getColumnIndex(DbOpenHelper.C_USER_ID));  	
		Cursor cursorFriends = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS, 
				DbOpenHelper.C_ID + "=" + userId,	null, null);
		if (cursorFriends.getCount() == 1) {	    		
			cursorFriends.moveToFirst();
			String user = cursorFriends.getString(cursorFriends.getColumnIndex(DbOpenHelper.C_USER));	    	
			String sent_by = cursorDisaster.getString(cursorDisaster.getColumnIndex(DbOpenHelper.C_SENT_BY));	    	
			String status = cursorDisaster.getString(cursorDisaster.getColumnIndex(DbOpenHelper.C_TEXT));
			// i dont wanna send tweets to the author or to whom gave them to me, so i must check it
			if (time == 0)
				enableSent_by = true;

			if (!user.equals(mConnectedDeviceName) && ( ( !sent_by.equals(mConnectedDeviceName) ) 
					|| enableSent_by  ) ) {

				int isValid = cursorDisaster.getInt(cursorDisaster.getColumnIndex(DbOpenHelper.C_IS_VALID));
				if (isValid == TRUE) {

					long received = cursorDisaster.getLong(cursorDisaster.getColumnIndex(DbOpenHelper.C_ADDED_AT)) ;
					//if it is the first encounter i send everything older than the timestamp received
					//that represents the most recent information received somehow from other nodes
					if (received > time ) {

						long created = cursorDisaster.getLong(cursorDisaster.getColumnIndex(DbOpenHelper.C_CREATED_AT)) ;
						long id = cursorDisaster.getLong(cursorDisaster.getColumnIndex(DbOpenHelper.C_ID));		    			
						int isFromServ = cursorDisaster.getInt(cursorDisaster.getColumnIndex(DbOpenHelper.C_ISFROMSERVER));
						int hasBeenSent = cursorDisaster.getInt(cursorDisaster.getColumnIndex(DbOpenHelper.C_HASBEENSENT));
						int hopCount = cursorDisaster.getInt(cursorDisaster.getColumnIndex(DbOpenHelper.C_HOPCOUNT));
						byte[] tweetSignature = cursorDisaster.getBlob(cursorDisaster.getColumnIndex(DbOpenHelper.C_SIGNATURE));

						String modulus = cursorFriends.getString(cursorFriends.getColumnIndex(DbOpenHelper.C_MODULUS));	
						String exponent = cursorFriends.getString(cursorFriends.getColumnIndex(DbOpenHelper.C_EXPONENT));

						RSACrypto crypto =  new RSACrypto(mSettings);
						RSAPublicKey publicKey = crypto.encodePublicKey(modulus, exponent);
						SignedTweet tweet = new SignedTweet(id, created, status,user ,userId, isFromServ,
								hasBeenSent, hopCount, publicKey, tweetSignature);

						return tweet; 	    			
					} 
				}	    	
			}
		}	
		return null;
	}

	private void sendRelevantTweets(long number, long time) {
		boolean spamFlag = false;
		int myOwnTweets = 0;
		ArrayList<SignedTweet> tweetList = new ArrayList<SignedTweet>();

		if (cursorDisaster != null) {

			TweetPacket dataPacket;	   
			ArrayList<DirectMessage> directMessList ;	    
			directMessList = getDirectMessages();

			if (cursorDisaster.getCount() > 0) {	    
				cursorDisaster.moveToFirst();

				//anti spam mechanism
				boolean isAboveLimit = (  computeNumberOfTweetsToBeSent(number, time) > limit ) ;
				if ( isAboveLimit ) {	
					Log.i(TAG, "number is above limit " ); 
					cursorDisaster = dbActions.disasterDbQuery(null," ASC");
					if (cursorDisaster != null) {
						setCursorPosition(number);	    		
						tweetList = addMyTweetsToListFirst(number, time);
						myOwnTweets = tweetList.size();
						setCursorPosition(number);
						Log.i(TAG, "number of my own tweets = " + myOwnTweets); 
						spamFlag = true;

					}
					else {
						Log.i(TAG, "cursor null" ); 
					}
				}	    	

				// send all the new messages. I subtract from the total number the old ones.	    	
				Log.i(TAG, "cursorDisaster.getCount() = " + cursorDisaster.getCount()); 

				for(int i=0; i<(cursorDisaster.getCount() - number); i++ ) {	    		

					if (tweetList.size() >= limit ) {
						//Log.i(TAG, "spamFlag = true set "); 
						//spamFlag = true;
						break;
					}		    	
					SignedTweet tweet = createSignedTweet(time); 
					if (tweet != null) {
						if (tweet.user.equals(mSettings.getString("user", ""))) {	    				
							if (!isAboveLimit) {
								Log.i(TAG, "my own tweet added to the list"); 	    					
								tweetList.add(tweet);	    					
							}

						} else {
							Log.i(TAG, "tweet from other peer added to the list"); 
							tweetList.add(tweet);

						}
					}
					//moving to the next row	    		
					if (!cursorDisaster.isAfterLast())
						cursorDisaster.moveToNext();
					else
						break;
				} 		     
				//if we are still connected and/or I have updates, send the message	    	
				if ( tweetList.size() > 0  ) {
					long TweetsNumber = 0;
					if (mBlueService != null) {
						if (spamFlag) {

							TweetsNumber = (limit-myOwnTweets) + number  ;	    					
							Log.i(TAG, "anti spam on, limiting tweets");
							Log.i(TAG, "tweetsNumber sent = " + TweetsNumber);
							Log.i(TAG, "total number = " + cursorDisaster.getCount()); 
						} else
							TweetsNumber = cursorDisaster.getCount();	

						dataPacket = new TweetPacket(new Date().getTime(),tweetList, directMessList , TweetsNumber );
						mBlueService.write(dataPacket);  
						Log.i(TAG, "TWEETS HAVE BEEN SENT"); 
					}	    			
				} 
				else {		

					if (directMessList != null) {
						sendDirectMessages(directMessList);
					}
					else	    			
						handler.postDelayed(new SendClosingRequest(), 200);
				}	    	

			} else {
				//IN CASE I HAVE ONLY DIRECT MESSAGES I NEED TO SEND THEM HERE	    	
				//if i don't have any tweet i just send a request to close connection		
				if (directMessList != null) {
					sendDirectMessages(directMessList);
				}
				else
					handler.postDelayed(new SendClosingRequest(), 200);				
			}
		}	    
	}

	private void sendDirectMessages(ArrayList<DirectMessage> directMessList){
		if(directMessList.size() > 0)
			if (mBlueService != null) {
				TweetPacket dataPacket = new TweetPacket(new Date().getTime(),null, directMessList ,
						cursorDisaster.getCount());
				mBlueService.write(dataPacket);  
				Log.i(TAG, "DIRECT MSGS HAVE BEEN SENT"); 
			}
	}

	private ArrayList<DirectMessage> getDirectMessages() {

		ArrayList<DirectMessage> directList = new ArrayList<DirectMessage>();
		DirectMessage message ;

		Cursor cursorDirect = dbActions.queryGeneric(DbOpenHelper.TABLE_DIRECT_OUTGOING, null,DbOpenHelper.C_CREATED_AT + " DESC", null);
		if ( cursorDirect != null) {
			if (cursorDirect.getCount() > 0) {

				cursorDirect.moveToFirst();
				for(int i = 0; i< cursorDirect.getCount(); i++) {

					long id = cursorDirect.getLong(cursorDirect.getColumnIndex(DbOpenHelper.C_ID));
					long created_at = cursorDirect.getLong(cursorDirect.getColumnIndex(DbOpenHelper.C_CREATED_AT)); 

					byte[] encr_text = cursorDirect.getBlob(cursorDirect.getColumnIndex(DbOpenHelper.C_TEXT));
					String recipientUser = cursorDirect.getString(cursorDirect.getColumnIndex(DbOpenHelper.C_USER_RECIPIENT));
					byte[] encrSender = cursorDirect.getBlob(cursorDirect.getColumnIndex(DbOpenHelper.C_USER));
					int hasBeenSent = cursorDirect.getInt(cursorDirect.getColumnIndex(DbOpenHelper.C_HASBEENSENT));				

					message = new DirectMessage( id, created_at, encr_text,recipientUser,encrSender, hasBeenSent);
					directList.add(message);

					cursorDirect.moveToNext();

				}
				return directList;
			}
		}
		return null;
	}


	private final BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){	 

		@Override
		public void onReceive(Context arg0, Intent intent) {     
			int level = intent.getIntExtra("level", 0);	      
			if (firstBroadcastTime == 0) {
				if (mSettings.contains("firstBroadcastTime")) {
					firstBroadcastTime = mSettings.getLong("firstBroadcastTime", 0);	    		  
				}
				else {
					firstBroadcastTime = new Date().getTime();
					SharedPreferences.Editor editor = mSettings.edit();
					editor.putLong("firstBroadcastTime", firstBroadcastTime);
					editor.commit();
				}
			}	      		

			try {	    	  

				long time =  Math.round( ( (new Date().getTime() - firstBroadcastTime) / 1000) )  ;
				batteryWriter.write(time + ":" + level + "\n");	 	    	  	    	 	    	  
			} 
			catch (IOException e) {}	     


			if ( prefs.getBoolean("prefDisasterMode", false) == true) { 

				if (level <=50 && isPartialSavingActive == false) {
					Log.i(TAG,"discovery delay has been doubled");
					DevicesDiscovery.discoveryDelay = (DevicesDiscovery.discoveryDelay *2);
					isPartialSavingActive = true;
					SharedPreferences.Editor editor = mSettings.edit();
					editor.putBoolean("isPartialSavingActive", isPartialSavingActive);
					editor.commit();

				}
				else if (level > 50 && isPartialSavingActive == true) {
					Log.i(TAG,"discovery delay has been reduced");
					DevicesDiscovery.discoveryDelay = (DevicesDiscovery.discoveryDelay / 2);
					isPartialSavingActive = false;
					SharedPreferences.Editor editor = mSettings.edit();
					editor.putBoolean("isPartialSavingActive", isPartialSavingActive);
					editor.commit();

				}

				if (level <= 30 && isFullSavingActive == false) {
					Toast.makeText(DisasterOperations.this, "battery level low, stopping discovery", Toast.LENGTH_SHORT).show(); 
					Log.i(TAG,"battery level low, stopping discovery");
					isFullSavingActive = true;
					SharedPreferences.Editor editor = mSettings.edit();
					editor.putBoolean("isFullSavingActive", isFullSavingActive);
					editor.commit();	    		
					stopService(new Intent(DisasterOperations.this, DevicesDiscovery.class));  
					// stopService(new Intent(DisasterOperations.this, RandomTweetGenerator.class)); 
				}
				else if (level > 30 && isFullSavingActive == true) {
					Toast.makeText(DisasterOperations.this, "enabling discovery again", Toast.LENGTH_SHORT).show(); 
					Log.i(TAG,"enabling dicovery again discovery");
					isFullSavingActive = false;
					SharedPreferences.Editor editor = mSettings.edit();
					editor.putBoolean("isFullSavingActive", isFullSavingActive);
					editor.commit();	    		
					startService(new Intent(DisasterOperations.this, DevicesDiscovery.class));  
					// startService(new Intent(DisasterOperations.this, RandomTweetGenerator.class)); 
				}
			}



		}
	};

}