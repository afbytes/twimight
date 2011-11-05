package ch.ethz.twimight;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.Twitter.Status;
import winterwell.jtwitter.Twitter.User;
import winterwell.jtwitter.TwitterException;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.TweetDbActions;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.net.twitter.FetchProfilePic;
import ch.ethz.twimight.net.twitter.OAUTH;
import ch.ethz.twimight.util.Constants;


/**
 * Service, responsible for pulling twitter updates from twitter.com and putting it into
 * the database.
 * TODO: Use Twitter's user stream API for instant updates. 
 * The REST API should only be used to populate the DB when a user logs in the first time
 * @author pcarta
 * @author thossmann
 */
public class UpdaterService extends Service {
	// Class constants
	static final String TAG = "UpdaterService"; // For logging
	public static final String ACTION_NEW_TWITTER_STATUS = "ACTION_NEW_TWITTER_STATUS"; // TODO: Move this to Constants (this is not local)

	// Member variables
	Handler handler;
	Updater updater;
	UpdaterLessFrequent updaterLf;

	private static  SQLiteDatabase db;
	private static TweetDbActions dbActions;
	Thread updaterThread, updaterLfThread; // the two threads for updating
	DbOpenHelper dbHelper;  
	ConnectivityManager connec;
	SharedPreferences mSettings,prefs;
	ConnectionHelper connHelper;
	ArrayList<Status> results = null;
	ArrayList<Twitter.Message> messages = null;


	Notification notification;
	NotificationManager notificationManager;

	Twitter twitter = null;

	/**
	 * Create function, sets the service up
	 * @author pcarta
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		// Setup handler
		handler = new Handler();  
		dbHelper = new DbOpenHelper(this);
		setDb(dbHelper.getWritableDatabase()); 
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);   
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		Log.d(TAG, "onCreate");	

	}

	/**
	 * Starts the updating threads.
	 * @author pcarta
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (updater == null) {

			updater = new Updater();
			updaterLf = new UpdaterLessFrequent();
			setDbActions(new TweetDbActions());

			if (connHelper.testInternetConnectivity()) {
				if (ConnectionHelper.twitter == null)
					connHelper.doLogin();
				twitter = ConnectionHelper.twitter;
			}

			updaterThread = new Thread(updater);		    
			updaterThread.start();  
			updaterLfThread = new Thread(updaterLf);		    
			updaterLfThread.start();


			Log.d(TAG, "onStart'ed"); 
		}	    
		return START_STICKY;
	}

	/**
	 * onDestroy stops the updater
	 * @author pcarta
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		handler.removeCallbacks(updater); // stop the updater  
		Log.d(TAG, "onDestroy'd");
	}

	/**
	 * onBind ..
	 * @author pcarta
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	/**
	 * @param dbActions the dbActions to set
	 */
	public static void setDbActions(TweetDbActions dbActions) {
		UpdaterService.dbActions = dbActions;
	}

	/**
	 * @return the dbActions
	 */
	public static TweetDbActions getDbActions() {
		return dbActions;
	}

	/**
	 * @param db the db to set
	 */
	public static void setDb(SQLiteDatabase db) {
		UpdaterService.db = db;
	}

	/**
	 * @return the db
	 */
	public static SQLiteDatabase getDb() {
		return db;
	}

	/** 
	 * Thread to update the database from twitter.com data (not so frequently) 
	 * @author pcarta
	 * @author thossmann
	 */
	class UpdaterLessFrequent implements Runnable {

		@Override
		public void run() {		 
			try { 
				if (connHelper.testInternetConnectivity()) {
					twitter = ConnectionHelper.twitter;
					if (twitter !=null) {

						new FriendsLookup().execute();		    			

					}
					else {
						connHelper.doLogin();
						twitter = ConnectionHelper.twitter;
					}
				}    	
			} catch (Exception e) {		                    
			}

			// Set this to run again later
			//long random = Math.round(Math.random()*20000);
			handler.postDelayed(this, Constants.FRIENDS_UPDATE_INTERVAL);

		}


	}

	/** 
	 * Thread to update the database from twitter.com data (frequently) 
	 * @author pcarta
	 * @author thossmann
	 */
	class Updater implements Runnable {	  


		@Override
		public void run() {      
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {		
			}

			Log.d(UpdaterService.TAG, "Updater ran.");
			try { 
				if (connHelper.testInternetConnectivity()) {
					twitter = ConnectionHelper.twitter;
					if (twitter !=null) {     			
						// Start the Tweet downloader
						new Download().execute();    			
					}
					else {
						connHelper.doLogin();
						twitter = ConnectionHelper.twitter;
					}
				}    	
			} catch (Exception e) {
				Log.e(TAG, "Updater.run exception: " + e);               
			}      

			// Set this to run again later
			//long random = Math.round(Math.random()*20000);
			handler.postDelayed(this, Constants.TWEET_UPDATE_INTERVAL);
		}
	}

	/**
	 * Thread to download your timeline from Twitter!
	 */
	class Download extends AsyncTask<Long, Void, Boolean> {

		List<Twitter.Status> timeline;

		/**
		 * Starts Threads to load DM, Mentions and Favorites. Then loads the timeline. 
		 * @param id
		 * @return
		 */
		@Override
		protected Boolean doInBackground(Long... id ) {

			// Start threads to download DMs and mentions
			new Thread(new FetchDirect()).start();
			new Thread(new FetchMentions()).start(); 
			new Thread(new FetchFavorites()).start();

			// How many tweets to request from Twitter?
			int nrTweets;
			String nrTweetId_s = prefs.getString("nrTweets", "2");
			switch(Integer.parseInt(nrTweetId_s)) {
			case 1: nrTweets = 10; break;
			case 2: nrTweets = 50; break;
			case 3: nrTweets = 100; break;
			case 4: nrTweets = 200; break;
			default: nrTweets = 50; break;
			}

			try {
				twitter.setCount(nrTweets);
				Log.i(TAG,"inside download, " + nrTweetId_s + " requesting " + Integer.toString(nrTweets) + " Tweets");
				timeline = twitter.getHomeTimeline();
				Log.i(TAG, "timeline size " + timeline.size() );

				new Thread(new FetchProfilePic(timeline, getDbActions(), UpdaterService.this)).start();

			} catch (Exception ex) {					

			}
			return true;     	   		 
		}		

		/**
		 * After loading the timeline: insert everything in DB and notify the user and GUI
		 */
		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(Boolean result) {

			boolean haveNewStatus = false;
			PendingIntent twPendingIntent;

			if (timeline != null) {
				for (Twitter.Status status : timeline) {
					if (status != null) {
						// Insert the tweet into the DB. The function will return false if we already have had it
						if( getDbActions().insertIntoTimelineTable(status)){
							haveNewStatus = true;
						}

						// We also try to insert the user into the friends list
						ContentValues values = new ContentValues();
						values.put(DbOpenHelper.C_USER, status.getUser().getScreenName().toString()  );
						values.put(DbOpenHelper.C_ID, status.getUser().getId());
						values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.FALSE);
						getDbActions().insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);
					}

				}
			}				

			if (haveNewStatus) {
				// broadcast intent to notify timeline about the new tweets
				sendBroadcast(new Intent(ACTION_NEW_TWITTER_STATUS));
				Log.d(TAG, "run() sent ACTION_NEW_TWITTER_STATUS broadcast.");

				// Notify the user? Check settings.
				if(prefs.getBoolean("notifyTweet", false) == true){
					twPendingIntent = PendingIntent.getActivity(UpdaterService.this, 0,  new Intent(UpdaterService.this, TwimightActivity.class), 0);
					notifyUser("You have new tweets in the timeline","New Tweets", Constants.NOTIFICATION_ID, twPendingIntent );
				}
				haveNewStatus = false;
			} 


		}
	}

	/**
	 * Helper function to creates the notifications in the status bar. 
	 * @param message
	 * @param title
	 * @param notId
	 * @param pend
	 * 
	 */
	private void notifyUser(String message, String title, int notId, PendingIntent pend) {	 
		Notification notification;
		NotificationManager notificationManager;
		PendingIntent pendingIntent;	    

		notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		notification = new Notification(R.drawable.twitter_icon,
				"Twimight", System.currentTimeMillis() );
		pendingIntent = pend;

		// Create the notification
		notification.setLatestEventInfo(this, title, message, pendingIntent );

		notification.when = System.currentTimeMillis(); 	 	
		notification.defaults |= Notification.DEFAULT_LIGHTS;
		if (prefs.getBoolean("prefVibration", false))
			notification.defaults |= Notification.DEFAULT_VIBRATE;
		notificationManager.notify(notId, notification);  	    	  
	}

	/** 
	 * Thread to download Favorites from Twitter
	 * TODO: Make this an AsyncTask
	 * @author thossmann
	 * 
	 */
	private class FetchFavorites implements Runnable{
		/**
		 * The main function of the thread
		 */
		@Override
		public void run(){
			Log.i(TAG, "Loading Favorites");

			// Are we connected?
			if (connHelper.testInternetConnectivity()) {

				// Attempt to log in
				int attempts = 0;
				while (ConnectionHelper.twitter == null & attempts < 5) {						    			
					connHelper.doLogin();
					attempts++;
					// wait for 3 seconds in-between log in attempts
					try{
						Thread.sleep(3000);
					} catch (InterruptedException e){}
				}

				// Are we logged in?
				if(ConnectionHelper.twitter != null){
					// We are logged in, so we can load the favorites
					List<Status> favorites = null;
					try{
						favorites = twitter.getFavorites();
					} catch (NullPointerException e){
						Log.d(TAG, "Could not load Favorites!");
					} catch (TwitterException e){
						Log.d(TAG, "TwitterException: Timeout while loading favorites.");
					}
					
					if(favorites != null){
						boolean haveNewFavorites = false;
						
						// Insert Favorites in Table
						for (Status status : favorites) {
							if(status != null){
								ContentValues values = DbOpenHelper.statusToContentValues(status,null);
								if(getDbActions().insertGeneric(DbOpenHelper.TABLE_FAVORITES, values)==true){
									haveNewFavorites = true;
	
									// set the favorite flag in the timeline table
									Log.i(TAG,"Updating Favorites!!!!!!!");
									getDbActions().setFavorite(status.id);
								} 
							}
						}
	
						// TODO: What if a tweet got unfavorited in another application?
	
						// Broadcast an intent so that timeline is updates (favorites stars)
						if(haveNewFavorites){
							sendBroadcast(new Intent(ACTION_NEW_TWITTER_STATUS));
						}
	
						// Finally, start thread to load profile pics
						new Thread(new FetchProfilePic(favorites, getDbActions(), UpdaterService.this)).start();
					}
				} // End: Are we logged in?
			} // End: Are we connected?
		} // End: run
	} // End: FetchFavorites


	/** 
	 * Thread to download the mentions from Twitter
	 * TODO: Make this an AsyncTask
	 * @author pcarta
	 * @author thossmann
	 */
	private class FetchMentions implements Runnable {
		ContentValues values;	

		/**
		 * The main function of the thread
		 */
		@Override
		public void run() {

			boolean haveNewMentions = false;
			PendingIntent mePendingIntent;

			Log.i(TAG,"Loading mentions");
			if (connHelper.testInternetConnectivity()) {
				if (ConnectionHelper.twitter == null ) {						    			
					connHelper.doLogin() ;		
				}


				if (ConnectionHelper.twitter != null) {
					try {
						results = (ArrayList<Status>)ConnectionHelper.twitter.getReplies();
						new Thread(new FetchProfilePic(results, getDbActions(), UpdaterService.this)).start();
					} 
					catch (NullPointerException e){ Log.e(TAG, "NullPointer exception while loading mentions"); }
					catch (TwitterException e) { Log.i(TAG, "TwitterException while loading mentions."); }
										  	
				}
			}	    

			haveNewMentions = false;

			if (results != null) {
				// go through results
				for (Status status : results) {
					if(status != null){
						// prepare to enter in DB
						values = DbOpenHelper.statusToContentValues(status,null);
						if (getDbActions().insertGeneric(DbOpenHelper.TABLE_MENTIONS, values) ) {
							// if this is successful, we know that we have new mentions
							haveNewMentions = true;
						}	 
					}
				}	

				if (haveNewMentions) {
					// notify the user? Check the settings.
					mePendingIntent = PendingIntent.getActivity(UpdaterService.this, 0,
							new Intent(UpdaterService.this, Mentions.class), 0);
					if(prefs.getBoolean("notifyMention", true) == true){	 
						notifyUser("You have new mentions","New Mention", Constants.MENTION_NOTIFICATION_ID, mePendingIntent );
					}
				}

			}

		}  
	} 

	/**
	 * Thread to download DMs from Twitter
	 * TODO: Make this an AsyncTask
	 * @author pcarta
	 * @author thossmann
	 */
	private class FetchDirect implements Runnable {
		ContentValues values;	

		/**
		 * The main function of the thread
		 */
		@Override
		public void run() {

			boolean haveNewDirect = false;
			PendingIntent dmPendingIntent;

			Log.i(TAG,"Loading direct messages");
			if (connHelper.testInternetConnectivity()) {
				if (ConnectionHelper.twitter == null ) {						    			
					connHelper.doLogin() ;		
				}

				if (ConnectionHelper.twitter != null) {
					try{
					messages = (ArrayList<Twitter.Message>)ConnectionHelper.twitter.getDirectMessages();
					Log.i(TAG,"messages size " + messages.size());
					// new Thread(new FetchProfilePic()).start();
					}
					catch (NullPointerException e) { Log.e(TAG, "NullPointer exception while loading DMs"); }
					catch (TwitterException e) {Log.i(TAG, "TwitterException while loading DMs"); }
				}
			}		  
			// we sleep for a while. (why??)
			try {
				Thread.sleep(500);
			} 
			catch (InterruptedException e) {  }

			if ( messages != null) {
				for (Twitter.Message msg : messages ) {	
					if(msg != null){
						Date date = msg.getCreatedAt();
						User user = msg.getSender();
						Status status = new Status(user,msg.getText(),msg.getId(),date);
						values = DbOpenHelper.statusToContentValues(status,null);
						values.put(DbOpenHelper.C_USER, user.getScreenName().toString());
						if (getDbActions().insertGeneric(DbOpenHelper.TABLE_DIRECT, values)) {
							haveNewDirect =true;
						}
					}

				}	

				if (haveNewDirect) {
					// Notify the user? Check the settings.
					if(prefs.getBoolean("notifyDM", true) == true){
						dmPendingIntent = PendingIntent.getActivity(UpdaterService.this, 0,
								new Intent(UpdaterService.this, DirectMessages.class), 0);
						notifyUser("You have new direct messages", "New Direct Message", Constants.DIRECT_NOTIFICATION_ID, dmPendingIntent);
					}
					haveNewDirect = false;

				}
			}
		}
	}

	/**
	 * AsyncTask to download follower and followee lists from Twitter.
	 * @author pcarta
	 * @author thossmann
	 */
	class FriendsLookup extends AsyncTask<Void, Void, Boolean> {		
		ContentValues values;
		List<User> friendsList;
		List<User> followersList;
		List<Twitter.Status> dummyStatus;

		/**
		 * Main function of AsyncTask: Load followers and followees from Twitter.
		 * @return successful?
		 */
		@Override
		protected Boolean doInBackground(Void...nil ) {

			try {

				friendsList= ConnectionHelper.twitter.getFriends();
				Thread.sleep(2000);
				followersList= ConnectionHelper.twitter.getFollowers();				

			} catch (Exception ex) {

				Log.e(TAG,"error loading followers or followings",ex);
				return false;
			}	
			return true;
		}		

		/**
		 * Process result: Insert list of follwers and followees into DB
		 * @param result from main function
		 */
		// This is in the UI thread, so we can mess with the UI
		@SuppressWarnings("deprecation")
		@Override
		protected void onPostExecute(Boolean result) {


			if (result) {
				dummyStatus = new ArrayList<Twitter.Status>();				
				values = new ContentValues();

				for (User user : friendsList) {	

					values.put(DbOpenHelper.C_USER, user.getScreenName().toString() );
					values.put(DbOpenHelper.C_ID, user.getId().longValue());
					values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.FALSE);	
					values.put(DbOpenHelper.C_IS_FOLLOWED_BY_ME, Timeline.TRUE);	
					getDbActions().insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);

					Twitter.Status status = new Twitter.Status(user,null,null,null);					
					dummyStatus.add(status);					
					values = new ContentValues();					

				}
				values = new ContentValues();	
				for (User user : followersList) {	

					values.put(DbOpenHelper.C_USER, user.getScreenName().toString() );
					values.put(DbOpenHelper.C_ID, user.getId().longValue());
					values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.FALSE);
					values.put(DbOpenHelper.C_IS_MY_FOLLOWER, Timeline.TRUE);
					getDbActions().insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);

					Twitter.Status status = new Twitter.Status(user,null,null,null);					
					dummyStatus.add(status);					
					values = new ContentValues();					

				}

				sendBroadcast(new Intent(ACTION_NEW_TWITTER_STATUS));
				new Thread(new FetchProfilePic(dummyStatus, getDbActions(), UpdaterService.this)).start();
			}		 		 

		}
	}

}