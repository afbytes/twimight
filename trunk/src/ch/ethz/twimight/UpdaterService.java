package ch.ethz.twimight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.Twitter.Status;
import winterwell.jtwitter.Twitter.User;
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
import ch.ethz.twimight.AsyncTasks.FetchProfilePic;


/**
 * Responsible for pulling twitter updates from twitter.com and putting it into
 * the database.
 */
public class UpdaterService extends Service {
  static final String TAG = "UpdaterService";
  Handler handler;
  Updater updater;
  UpdaterLessFrequent updaterLf;
  //SQLiteDatabase db = MyTwitter.db;
  static  SQLiteDatabase db;
  static TweetDbActions dbActions;
  Thread updaterThread, updaterLfThread;
  DbOpenHelper dbHelper;  
  ConnectivityManager connec;
  SharedPreferences mSettings,prefs;
  ConnectionHelper connHelper;
  ArrayList<Status> results = null;
  ArrayList<Twitter.Message> messages = null;
  public static boolean isRunning = false;
  static final int NOTIFICATION_ID = 47, MENTION_NOTIFICATION_ID = 49;
  boolean haveNewStatus =  false, haveNewMentions = false, haveNewDirect = false;
  Notification notification;
  NotificationManager notificationManager;
  PendingIntent pendingIntent;
  
  public static final String ACTION_NEW_TWITTER_STATUS = "ACTION_NEW_TWITTER_STATUS";
  Twitter twitter = null;

  @Override
  public void onCreate() {
    super.onCreate();
    // Setup handler
    isRunning = true;
    handler = new Handler();  
    dbHelper = new DbOpenHelper(this);
  	db = dbHelper.getWritableDatabase(); 
  	mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);   
  	prefs = PreferenceManager.getDefaultSharedPreferences(this);
	connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
	connHelper = new ConnectionHelper(mSettings,connec);
	  Log.d(TAG, "onCreate");	
	
  }

  @Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	  if (updater == null) {
		  
		    updater = new Updater();
		    updaterLf = new UpdaterLessFrequent();
		    dbActions = new TweetDbActions();
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
  
  
  @Override
  public void onDestroy() {
	  isRunning = false;
    super.onDestroy();
    handler.removeCallbacks(updater); // stop the updater  
    Log.d(TAG, "onDestroy'd");
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
  

  class UpdaterLessFrequent implements Runnable {
	  static final long DELAY_L = 290000L;
	@Override
	public void run() {		 
		  try { 
		    	if (connHelper.testInternetConnectivity()) {
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
		  long random = Math.round(Math.random()*20000);
	      handler.postDelayed(this, DELAY_L + random);
		
	}
	  
	
  }
  
  /** Updates the database from twitter.com data */
  class Updater implements Runnable {	  
	    static final long DELAY = 110000L;	    
	   

    public void run() {      
      try {
		Thread.sleep(2000);
	} catch (InterruptedException e1) {		
	}
	
	 Log.d(UpdaterService.TAG, "Updater ran.");
      try { 
    	if (connHelper.testInternetConnectivity()) {
    		if (twitter !=null) {     			
    			
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
      long random = Math.round(Math.random()*20000);
      handler.postDelayed(this, DELAY + random);
    }
  }
  
  class Download extends AsyncTask<Long, Void, Boolean> {
	  
	  List<Twitter.Status> timeline;
	  
			@Override
			protected Boolean doInBackground(Long... id ) {
				try {
					twitter.setCount(40);
					Log.i(TAG,"inside download");
					timeline = twitter.getHomeTimeline();
					Log.i(TAG, "timeline size " + timeline.size() );
					
					new Thread(new FetchProfilePic(timeline, dbActions, UpdaterService.this)).start();
	    				    			
				 } catch (Exception ex) {					
					
				 }
				 return true;     	   		 
			}		

			// This is in the UI thread, so we can mess with the UI
			@Override
			protected void onPostExecute(Boolean result) {
				if (timeline != null) {
					for (Twitter.Status status : timeline) {
						if( dbActions.insertIntoTimelineTable(status))
           					 haveNewStatus = true;
						if (status != null) {
							ContentValues values = new ContentValues();
							values.put(DbOpenHelper.C_USER, status.getUser().getScreenName().toString()  );
							values.put(DbOpenHelper.C_ID, status.getUser().getId());
							values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.FALSE);
							dbActions.insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);
						}
				
					}
				}				
				
				if (haveNewStatus) {
			    	  sendBroadcast(new Intent(ACTION_NEW_TWITTER_STATUS));
			    	  Log.d(TAG, "run() sent ACTION_NEW_TWITTER_STATUS broadcast.");

			          // Create the notification
			    	  pendingIntent = PendingIntent.getActivity(UpdaterService.this, 0,
			    	          new Intent(UpdaterService.this, MyTwitter.class), 0);	 
			    	  notifyUser("You have new tweets in the timeline","New Twitter Status", NOTIFICATION_ID, pendingIntent );			    	  			          
			          haveNewStatus = false;
			      } 			
			}
		}
  
   private class FetchMentions implements Runnable {
	  ContentValues values;	
	  
	  public void run() {
		  if (connHelper.testInternetConnectivity()) {
			  if (ConnectionHelper.twitter == null ) {						    			
				  connHelper.doLogin() ;		
			  }
			  
			  try {
				  if (ConnectionHelper.twitter != null) {
					  results = (ArrayList<Status>)ConnectionHelper.twitter.getReplies();
				  	  new Thread(new FetchProfilePic(results, dbActions, UpdaterService.this)).start();
				  	  

					  try {
						Thread.sleep(1000);
					  } catch (InterruptedException e) {  }
					  
					  List<Status> favorites = twitter.getFavorites();
						new Thread(new FetchProfilePic(favorites, dbActions, UpdaterService.this)).start();
						for (Status status : favorites) {
							ContentValues values = DbOpenHelper.statusToContentValues(status,null);
			         		dbActions.insertGeneric(DbOpenHelper.TABLE_FAVORITES, values);		           			 
						}
				  }				  	
		 	   } 
		   	  catch (Exception e) {   }
		  }	    
		  
		  if (results != null) {
			  for (Status status : results) {
				  values = DbOpenHelper.statusToContentValues(status,null);
				 // Log.i(TAG,"adding mention to db");
				  if (dbActions.insertGeneric(DbOpenHelper.TABLE_MENTIONS, values) ) {
					  haveNewMentions = true;
					 // Log.i(TAG,"haveNewMentions set to true");
				  }
				  else 
					  haveNewMentions = false;
		 	 }	
			  if (haveNewMentions) {
				  pendingIntent = PendingIntent.getActivity(UpdaterService.this, 0,
		    	          new Intent(UpdaterService.this, Mentions.class), 0);	
				  Log.i(TAG,"new mentions, notifying the user");
		    	  notifyUser("You have new mentions","New mention tweet", MENTION_NOTIFICATION_ID, pendingIntent );			    	  			          
		          haveNewStatus = false;
			  }
				  
		  }
		  
		  new Thread(new FetchDirect()).start();	
	  }  
  } 
   
   
   private class FetchDirect implements Runnable {
	  ContentValues values;	
	  
	  public void run() {		 
		  if (connHelper.testInternetConnectivity()) {
			  if (ConnectionHelper.twitter == null ) {						    			
				  connHelper.doLogin() ;		
				}
			  try {
				  if (ConnectionHelper.twitter != null) {
					  messages = (ArrayList<Twitter.Message>)ConnectionHelper.twitter.getDirectMessages();
				  		Log.i(TAG,"messages size " + messages.size());
				  	 // new Thread(new FetchProfilePic()).start();
				  }
		 	   } 
		   	  catch (Exception e) { 
		   		  Log.e(TAG, "error, trying again", e);
		   	  }
		  }		  
		  try {
			Thread.sleep(500);
		  } 
		  catch (InterruptedException e) {  }
		  
		  if ( messages != null) {
			  for (Twitter.Message msg : messages ) {				 
				 Date date = msg.getCreatedAt();
				 User user = msg.getSender();
				 Status status = new Status(user,msg.getText(),msg.getId(),date);
				 values = DbOpenHelper.statusToContentValues(status,null);
				 values.put(DbOpenHelper.C_USER, user.getScreenName().toString());
				 if (dbActions.insertGeneric(DbOpenHelper.TABLE_DIRECT, values)) 
					 haveNewDirect =true;
				 
		 	 }	
			  
			  if (haveNewDirect) {
				  pendingIntent = PendingIntent.getActivity(UpdaterService.this, 0,
		    	          new Intent(UpdaterService.this, DirectMessages.class), 0);			
				  notifyUser("New Direct Message","You have new disaster direct messages in the database", Timeline.DIRECT_NOTIFICATION_ID, pendingIntent);
				  haveNewDirect = false;
				 
			  }
		  }
	  }
  }
   
	class FriendsLookup extends AsyncTask<Void, Void, Boolean> {		
		ContentValues values;
		List<User> friendsList;
		List<User> followersList;
		List<Twitter.Status> dummyStatus;
		 
		@Override
		protected Boolean doInBackground(Void...nil ) {
			
			try {
				
				friendsList= ConnectionHelper.twitter.getFriends();
				Thread.sleep(2000);
				followersList= ConnectionHelper.twitter.getFollowers();				
				
			} catch (Exception ex) {
				
				Log.e(TAG,"error, trying again",ex);
				try {
					Thread.sleep(3000);
					friendsList= ConnectionHelper.twitter.getFriends();	
				} catch (Exception e) {
					
				}
						
				return false;
			}	
			return true;
		}		

		// This is in the UI thread, so we can mess with the UI
		@SuppressWarnings("deprecation")
		@Override
		protected void onPostExecute(Boolean result) {
			
			new Thread(new FetchMentions()).start(); 
			
			if (result) {
				dummyStatus = new ArrayList<Twitter.Status>();				
				values = new ContentValues();
				
				for (User user : friendsList) {	
					
					values.put(DbOpenHelper.C_USER, user.getScreenName().toString() );
					values.put(DbOpenHelper.C_ID, user.getId().longValue());
					values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.FALSE);	
					values.put(DbOpenHelper.C_IS_FOLLOWED_BY_ME, Timeline.TRUE);	
					dbActions.insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);
					
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
					dbActions.insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);
					
					Twitter.Status status = new Twitter.Status(user,null,null,null);					
					dummyStatus.add(status);					
					values = new ContentValues();					
					
				 }
				
				sendBroadcast(new Intent(ACTION_NEW_TWITTER_STATUS));
				new Thread(new FetchProfilePic(dummyStatus, dbActions, UpdaterService.this)).start();
			}		 		 
			
		}
	}
	
	 private void notifyUser(String message, String title, int notId, PendingIntent pend) {	 
		    Notification notification;
		    NotificationManager notificationManager;
		    PendingIntent pendingIntent;	    
		    
		    notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
		    notification = new Notification( android.R.drawable.stat_sys_download,
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
  
 

}