package ch.ethz.twimight;

import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.TwitterException;
import winterwell.jtwitter.Twitter.Status;
import winterwell.jtwitter.Twitter.User;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.TimelineAdapter;
import ch.ethz.twimight.data.TweetDbActions;
import ch.ethz.twimight.net.opportunistic.DevicesDiscovery;
import ch.ethz.twimight.net.opportunistic.DisasterOperations;
import ch.ethz.twimight.net.RSACrypto;
import ch.ethz.twimight.net.tds.KeysActions;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.net.twitter.DirectMsgTask;
import ch.ethz.twimight.net.twitter.FetchProfilePic;
import ch.ethz.twimight.net.twitter.OAUTH;
import ch.ethz.twimight.ui.Retweet;
import ch.ethz.twimight.net.opportunistic.packets.SignedTweet;
import ch.ethz.twimight.util.Constants;


/** Displays the list of all tweets from the DB. */
public class Timeline extends Activity {
	
  static final String TAG = "Timeline";
  private ListView listTimeline;
  private EditText input,inputProfile, inputDirect;
  boolean peerRequestedClosing =false;  
 
  private SQLiteDatabase db;
  private DbOpenHelper dbHelper;
  private Cursor cursor,cursorDisaster;
  private TimelineAdapter adapter;   
  TweetDbActions dbActions ;
  TweetContextActions contextActions; 
  AlertDialog.Builder alert =  null;
  ConnectionHelper connHelper;
  public String mToken;
  public String mSecret;
  BluetoothAdapter mBtAdapter;
  NotificationManager notificationManager;
  Thread postDisasterTweets;
  Status resultStatus;

  SharedPreferences mSettings,prefs;   
  public static boolean isRunning = false;
  int hasBeenSent = FALSE ;
  private boolean isShowingFavorites =  false;   

  private static final int PREF_SCREEN = 1;  
  
  static final int REPLY_ID = Menu.FIRST;
  static final int RETWEET_ID = Menu.FIRST + 1;
  static final int DELETE_ID = Menu.FIRST + 2;
  static final int FAVORITE_ID = Menu.FIRST + 3;
  public static final int R_FAVORITE_ID = Menu.FIRST + 4;
  static final int PROFILEINFO_ID = Menu.FIRST + 11;
  static final int DIRECT_ID = Menu.FIRST + 5;
  
  static final int REFRESH_ID = Menu.FIRST;
  static final int FOLLOWING_ID = Menu.FIRST +1;
  static final int SEND_ID = Menu.FIRST + 2;
  static final int SETTINGS_ID = Menu.FIRST + 3;
  static final int DISASTER_ID = Menu.FIRST + 4;
  static final int EXIT_ID = Menu.FIRST+ 5 ;
  static final int FAVORITES_ID = Menu.FIRST + 6;    
  static final int TIMELINE_ID = Menu.FIRST + 7;   
  static final int MENTIONS_ID = Menu.FIRST + 8;   
  static final int SEARCH_ID = Menu.FIRST + 9; 
  static final int LOGOUT_ID = Menu.FIRST + 10;
  static final int RECEIVED_DIRECT_ID = Menu.FIRST + 12;
  static final int DETAILS_ID = Menu.FIRST + 13;
  static final int FOLLOWERS_ID = Menu.FIRST + 14;
  
  
  public static final int FALSE = 0;
  public static final int TRUE = 1; 
  static final long DELAY = 10000L; 
  
  static PendingIntent restartIntent;
  private static Timeline activity ;
  WakeLock wakeLock;
  static ConnectivityManager connec;
  String username = "", destinationUsername;
  long id;
  
	
  public void onCreate(Bundle savedInstanceState) {
		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.simplelist);		    
	   	isRunning = true;  
	    prefs = PreferenceManager.getDefaultSharedPreferences(this);	    
	    mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);	    
	    mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);	    
	    input = new EditText(this);   
	    inputProfile = new EditText(this);  
	    inputDirect = new EditText(this); 	    
	    // Find views by id
	    listTimeline = (ListView) findViewById(R.id.itemList);		    
	   
	    contextActions = new TweetContextActions(connHelper,prefs, mSettings);
	    dbHelper = new DbOpenHelper(this);
	  	db = dbHelper.getWritableDatabase(); 	
	    
	  	registerForContextMenu(listTimeline);	 
	    setActivity(this);	  
	    Log.i(TAG,"on create 2.1");
	    dbActions = UpdaterService.getDbActions();
	    if (dbActions == null) {
	    	 dbHelper = new DbOpenHelper(this);
	    	 UpdaterService.setDb(dbHelper.getWritableDatabase()); 
	    	 UpdaterService.setDbActions(new TweetDbActions());
	    	 dbActions = UpdaterService.getDbActions();
	    }
	    //delete really old tweets from the tables
	    try {
	    	String where = DbOpenHelper.C_CREATED_AT + "<" + (new Date().getTime() - (3600000*24*2))  ;	
	    	dbActions.delete(where, DbOpenHelper.TABLE);
	    	dbActions.delete(where, DbOpenHelper.TABLE_FAVORITES);
	    	dbActions.delete(where, DbOpenHelper.TABLE_SEARCH);
	    	dbActions.delete(where, DbOpenHelper.TABLE_PICTURES); 
	    	
	    	// Get the data from the DB
			String query = "SELECT tim._id,user,created_at,status,isDisaster, isFavorite FROM timeline AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";
			cursor = dbActions.rawQuery(query);
			startManagingCursor(cursor);
			//cursor = dbActions.queryGeneric(DbOpenHelper.TABLE,null, DbOpenHelper.C_CREATED_AT + " DESC" ,"100");
			Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
			cursorPictures.moveToFirst();
			    // Setup the adapter		
			adapter = new TimelineAdapter(this, cursor, cursorPictures);		
			listTimeline.setAdapter(adapter); 
	    }
	    catch (Exception ex) {
	    	Log.e(TAG,"error  ",ex);
	    	}	    
		Log.i(TAG,"on create 3");	
		
		// Are we in disaster mode?
		if (prefs.getBoolean("prefDisasterMode", false) == true) {
			//it means we are restarting from a crash
			Log.i(TAG, "asdfasdfasdfasdfasdfasdfasdfasdf");
			startServices();	
		}		
		 // I need to know the authenticating username
		// at the end it will post also the public key
		new GetAuthenticatingUsername().execute();	
		
		// Register to get  broadcasts		
	    registerReceiver(twitterStatusReceiver, new IntentFilter(Constants.ACTION_NEW_TWEETS));	    
	    registerReceiver(directMsgSentReceiver,new IntentFilter("DirectMsg"));
	    registerReceiver(externalAppReceiver, new IntentFilter("test"),"com.permission.SEND" , null);
	    
	    registerReceiver(modeChangeReceiver, new IntentFilter(Constants.ACTION_DISASTER_MODE));
	    
		
  }
  
@Override
protected void onStart() {
	// TODO Auto-generated method stub
	super.onStart();
	// if (prefs.getBoolean("prefDisasterMode", false) == true) {
   	  publishDisasterTweets(false);
      String where = DbOpenHelper.C_CREATED_AT + "<" + (new Date().getTime() - (3600000*24*2))  ;
	  dbActions.delete(where, DbOpenHelper.TABLE_DISASTER);	
	 //}
}

private void restart(){
	
	Log.i(TAG, "IN RESTART!!!!!!!");
	Intent intent = getIntent();
	finish();
	startActivity(intent);
}

@Override
protected Dialog onCreateDialog(int id) {	
	alert = new AlertDialog.Builder(Timeline.this);
	
	//Dialog for sending a tweet
	if (id == 0) {
		alert.setView(input); 
		alert.setTitle("Send a Tweet");  	
	  	// Set an EditText view to get user input     	
	  	 	
	  	alert.setPositiveButton("Send", new OnClickListener() {
	  		
	  		public void onClick(DialogInterface dialog, int whichButton) {
	  			
	  		 String message = input.getText().toString();	  	
	  		  sendMessage(message);
	  		  dialog.dismiss();	  		  		
	  		  }
	  		});

	  	alert.setNegativeButton("Cancel", new OnClickListener() {
	  		  public void onClick(DialogInterface dialog, int whichButton) {
	  			 input.setText(""); 
	  			 dialog.dismiss();	  		    
	  		  }
	  		}); 
	} 
	// Dialog for searching a user profile
	else if (id == 1){
		alert.setTitle("Insert Username");  	
	  	// Set an EditText view to get user input     	
		alert.setView(inputProfile); 	
	  	alert.setPositiveButton("Search", new OnClickListener() {
	  		
	  		public void onClick(DialogInterface dialog, int whichButton) {
	  			
	  		 String message = inputProfile.getText().toString();	  		 
	  		 lookProfile(null, message);
	  		    		  		
	  		  }
	  		});

	  	alert.setNegativeButton("Cancel", new OnClickListener() {
	  		  public void onClick(DialogInterface dialog, int whichButton) {
	  			 inputProfile.setText(""); 
	  			 dialog.dismiss();	  		    
	  		  }
	  		}); 
	}
	// Dialog to send a direct message
	else {
		
		alert.setTitle("Insert message");  	
	  	// Set an EditText view to get user input     	
		alert.setView(inputDirect); 	
	  	alert.setPositiveButton("Send", new OnClickListener() {
	  		
	  		public void onClick(DialogInterface dialog, int whichButton) {
	  			
	  		 String message = inputDirect.getText().toString();	  		
	  		 new DirectMsgTask(message,destinationUsername,Timeline.this, connHelper,
	  				 mSettings,prefs.getBoolean("prefDisasterMode", false)).execute();	  		
	  		    		  		
	  		  }
	  		});

	  	alert.setNegativeButton("Cancel", new OnClickListener() {
	  		  public void onClick(DialogInterface dialog, int whichButton) {
	  			 inputDirect.setText(""); 
	  			 dialog.dismiss();	  		    
	  		  }
	  		}); 
	}
  	return alert.create();	
}



private void cancelNotification() {
	 notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	 notificationManager.cancel(Constants.NOTIFICATION_ID);
	 if (isShowingFavorites) {		
			changeView(false,DbOpenHelper.TABLE);	
	 }
}


 class DeleteMyTweets implements Runnable {
	
	public void run() {	
		try {
			if (ConnectionHelper.twitter != null) {				
				try {
					ConnectionHelper.twitter.setCount(100);
				} catch (Exception e) {	}
				
				ArrayList<Twitter.Status> tweets = (ArrayList<Twitter.Status>)ConnectionHelper.twitter.getUserTimeline();
				for (Twitter.Status status : tweets) {			
					contextActions.deleteTweet(status.getId().longValue(), false);			
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {	}			
				}
			}
		}
		catch (TwitterException ex){}
	}
}

// This async task will get the authenticating username that is used for different purposes
class GetAuthenticatingUsername extends AsyncTask<Void, Void, String> {		
	long userId;
	
		@Override
		protected String doInBackground(Void... nil ) {
			try {			
				if (connHelper.testInternetConnectivity()) {				
					  if (ConnectionHelper.twitter != null){
						  
						  Twitter.Status status =   ConnectionHelper.twitter.getStatus() ; 
					   	if (status == null) {					   
					   		ConnectionHelper.twitter.setStatus(".");
					   		try {
					   		Thread.sleep(1000);
					   		} catch (InterruptedException ex) {}
					   		status =ConnectionHelper.twitter.getStatus();
					   		if (status != null) {
							   username = status.getUser().getScreenName(); //got username of the user	
							  
							   id = status.getId().longValue(); //got user id
							   ConnectionHelper.twitter.destroyStatus(id);
							   userId = status.getUser().getId();
							   						   
							   return username;
						   }
						   else return "";
					  	}
					 	  else {					 		  
					 		  username = status.getUser().getScreenName();
					 		  userId = status.getUser().getId();
					 		  return username;
					   		}					      
				      	} 					  
				   }				
			}
			catch (Exception ex){
				Log.e(TAG,"getting username exception");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					
				}
				//new GetAuthenticatingUsername().execute();
				return "";
			}
			return "";
		}		

		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(String user) {
			if (!user.equals("")) {
				// I need to save the username in the shared preferences
				SharedPreferences.Editor editor = mSettings.edit();
				editor.remove("user");
				editor.remove("userId");
				editor.putString("user", user);
				editor.putLong("userId", userId);
				editor.commit();
				
				//need to insert my user in order to be visible in the main timeline
				ContentValues values = new ContentValues();
				values.put(DbOpenHelper.C_USER, user);
				values.put(DbOpenHelper.C_ID, userId);
				values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.TRUE);
				values.put(DbOpenHelper.C_IS_FOLLOWED_BY_ME, Timeline.FALSE);	
				String modulus = mSettings.getString("modulus_public", "");
				String exponent = mSettings.getString("exponent_public", "");
				values.put(DbOpenHelper.C_MODULUS, modulus);
				values.put(DbOpenHelper.C_EXPONENT, exponent);				
				dbActions.insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);
				
			//	if (!mSettings.contains("PublicKeyPosted")) 
					//new Thread(new PostPublicKey()).start();
				//new Thread(new GetFriendKeys()).start();      
			}		
		}
	}

class PostPublicKey implements Runnable {	
	   
	@Override
	public void run() {
		   String mod = mSettings.getString("modulus_public", null);
		   String exp = mSettings.getString("exponent_public", null);
		   String username = mSettings.getString("user", "");
		   long id = mSettings.getLong("userId", -1);	   
		   
		   KeysActions keys = new KeysActions();
		   
		   if (keys.postKey(mod, exp, username,"" +  id) ) {		   
			   SharedPreferences.Editor editor = mSettings.edit();
			   editor.putBoolean("PublicKeyPosted", true);
			   editor.commit();		
		   }
	}	
}

class GetFriendKeys implements Runnable {	
	   
	@Override
	public void run() {	
		   
		   Cursor cursorFriends = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS, null, null, null);
			if (cursorFriends != null) {
				if (cursorFriends.getCount() > 0) {
					cursorFriends.moveToFirst();
					//for each one of them
					for (int i=0; i< cursorFriends.getCount(); i++) {
						//get the id
						long friendId = cursorFriends.getLong(cursorFriends.getColumnIndex(DbOpenHelper.C_ID));
						String friendScreenName = cursorFriends.getString(cursorFriends.getColumnIndex(DbOpenHelper.C_USER));
						KeysActions keys = new KeysActions();
						RSAPublicKeySpec pubKeySpec = keys.getKey("" + friendId);									
												
						ContentValues values = new ContentValues();
						values.put(DbOpenHelper.C_MODULUS, pubKeySpec.getModulus().toString() );	
						values.put(DbOpenHelper.C_EXPONENT, pubKeySpec.getPublicExponent().toString() );
						values.put(DbOpenHelper.C_ID, friendId );							
						values.put(DbOpenHelper.C_USER, friendScreenName );		
						//save public key into the database
						dbActions.insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);
								        
						cursorFriends.moveToNext();
					}
				}
			}	
	}	
}


@Override
  public void onResume() {
	
    super.onResume();
   
    // Cancel notification
   cancelNotification(); 
   if (connHelper.testInternetConnectivity()  )  {
		if (!isShowingFavorites) {
			new RefreshTimeline(false).execute();			
		}
		new GetAuthenticatingUsername().execute();
   }
   


   //this thread was useful to remove spam during tests
   //new Thread(new DeleteMyTweets()).start(); 
  }


  @Override
  public void onDestroy() {
	Log.i(TAG,"inside on destroy, stopping everything");
    unregisterReceiver(twitterStatusReceiver);
    unregisterReceiver(externalAppReceiver);
    unregisterReceiver(directMsgSentReceiver);
    unregisterReceiver(modeChangeReceiver);
    isRunning = false;
    
    /*
    stopService(new Intent(this, UpdaterService.class));
    
    BluetoothAdapter mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    mBtAdapter.disable();
    if (prefs.getBoolean("prefDisasterMode", false)) {
    	stopService(new Intent(this,DisasterOperations.class));
    	//Try to publish disaster tweets
    	 publishDisasterTweets(true);
    }
    	  
    // disable disaster mode
    SharedPreferences.Editor editor = prefs.edit();   
    editor.putBoolean("prefDisasterMode", false);   
    editor.commit();
    */
       
    cursor.close();
    db.close();       
    ConnectionHelper.twitter = null;
    super.onDestroy();
  }
 
  
  @Override
  public void onCreateContextMenu(ContextMenu menu, View v,
          ContextMenuInfo menuInfo) {
      super.onCreateContextMenu(menu, v, menuInfo);
      AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;  
      Cursor cursor;
      String tweetUser;
      int isFromServer = FALSE;
      long userCode =  0;
      
      if (prefs.getBoolean("prefDisasterMode", false) == true)	
    	  publishDisasterTweets(false);
      
      Cursor cursorUsercode = dbActions.queryGeneric(DbOpenHelper.TABLE, DbOpenHelper.C_ID + "=" + info.id, null, null);
      if (cursorUsercode != null){
    	  if (cursorUsercode.getCount() == 1) {
    		  cursorUsercode.moveToFirst();
    		  userCode = cursorUsercode.getLong(cursorUsercode.getColumnIndex(DbOpenHelper.C_USER_ID));
    	  }
      }
      Cursor cur = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS, DbOpenHelper.C_IS_MY_FOLLOWER + "=" + TRUE + " AND " +
			  DbOpenHelper.C_ID + "=" + userCode , null,null);
      
      //if the app is showing the normal timeline
      if (isShowingFavorites == false) {
    	  
    	  tweetUser = dbActions.userDbQuery(info,DbOpenHelper.TABLE);
    	  cursor = dbActions.disasterDbQuery(DbOpenHelper.C_ID + "=" + info.id," DESC");    	  
    	  if (cursor != null) {
    		  
    		  if (cursor.getCount() > 0) {
    			  cursor.moveToFirst();
    			  isFromServer = cursor.getInt(cursor.getColumnIndex(DbOpenHelper.C_ISFROMSERVER));
    			  if (  !tweetUser.equals(mSettings.getString("user", "") ) ) {
    				  menu.add(0,DIRECT_ID,4, "Direct Message");
    				  menu.add(0, RETWEET_ID, 1 ,"Retweet"); 
    			  }
    		  }
    		  //if the tweet is not a disaster one and I am not the author I can add it as a favorite
    		  if ((cursor.getCount() == 0 || isFromServer == TRUE ) &&  
    				  !tweetUser.equals(mSettings.getString("user", "") ) ) {
    			  //show favorite if it isn t a disaster tweet and I am not the author
    			  if (!isFavorite(info.id))
    				  menu.add(0, FAVORITE_ID, 3, "Favorite"); 
    			  else 
    				  menu.add(0, R_FAVORITE_ID, 3, "Remove Favorite"); 
    			  
    			  if (cur.getCount() == 1)
    				  menu.add(0,DIRECT_ID,4, "Direct Message");
    			  menu.add(0, RETWEET_ID, 1 ,"Retweet"); 
    		  }
    	  }
    	  
    	  if (tweetUser.equals(mSettings.getString("user", ""))) {    		
        	  menu.add(0, DELETE_ID, 2, "Delete");        	        		  
    	  }
    	  else {    		  
    		  menu.add(0, REPLY_ID, 0, "Reply");
           	  }      	  
      }       
      //is showing the favorite tweets
      else {
    	  tweetUser = dbActions.userDbQuery(info,DbOpenHelper.TABLE_FAVORITES);
    	  menu.add(0, REPLY_ID, 0, "Reply");
    	  menu.add(0, R_FAVORITE_ID, 3, "Remove Favorite");  
    	  menu.add(0, RETWEET_ID, 1 ,"Retweet"); 
    	  if ( !tweetUser.equals(mSettings.getString("user", "") )) {
    		  if (cur.getCount() == 1)				 
    			  menu.add(0,DIRECT_ID,4, "Direct Message");
    	  }
      }
      menu.add(0, PROFILEINFO_ID, 5 , "About @" + tweetUser); 
  }      


  private boolean isFavorite(long id) {
	  Cursor cursorIsFavorite = dbActions.queryGeneric(DbOpenHelper.TABLE,
			  DbOpenHelper.C_ID + "=" + id  , null, null); 
	  if (cursorIsFavorite != null)
		  if (cursorIsFavorite.getCount() == 1) {
			  cursorIsFavorite.moveToFirst();
			  int isFavorite = cursorIsFavorite.getInt(cursorIsFavorite.getColumnIndex(DbOpenHelper.C_IS_FAVORITE));
			  if (isFavorite == TRUE)
				  return true;
		  }
	  return false;
  }
  
@Override
  public boolean onContextItemSelected(MenuItem item) {
	 AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
	
	 
      switch(item.getItemId()) {
          case REPLY_ID:              
                String user = dbActions.userDbQuery(info,DbOpenHelper.TABLE);              
                input.setText("@" + user);
                showDialog(0);         
              return true;
          case RETWEET_ID: 
        	  //I can retweet normally if there is connectivity or 
        	  //I can retweet into the disaster Db in case the mode is on
        	  if ( connHelper.testInternetConnectivity() ) 
        		  new Retweet(contextActions, DbOpenHelper.TABLE, this, true).execute(info.id); 
        	  
        	  else if ( prefs.getBoolean("prefDisasterMode", false) == true ) {
        		  new Retweet(contextActions, DbOpenHelper.TABLE, this, false).execute(info.id);  
        		  Toast.makeText(this, "Retweet added to the Disaster Db", Toast.LENGTH_LONG).show(); 
        	  }
        	  else       		  
        		  Toast.makeText(this, "No internet connectivity", Toast.LENGTH_LONG).show(); 
         	  return true;       	  
        	  
          case FAVORITE_ID:
        	 if (isShowingFavorites)
        		 new SetFavorite(FAVORITE_ID,DbOpenHelper.TABLE_FAVORITES).execute(info.id);
        	 else
        		 new SetFavorite(FAVORITE_ID,DbOpenHelper.TABLE).execute(info.id);
        	 return true;
        	  
          case R_FAVORITE_ID:
        	  if (isShowingFavorites)
        		  new SetFavorite(R_FAVORITE_ID,DbOpenHelper.TABLE_FAVORITES).execute(info.id);   
        	  else
        		  new SetFavorite(R_FAVORITE_ID,DbOpenHelper.TABLE).execute(info.id);   
        	  return true;
          case DELETE_ID: 
        	  if (connHelper.testInternetConnectivity() && 
        			  prefs.getBoolean("prefDisasterMode", false) == false) 
                  new DeleteTweet(false).execute(info.id); 
        	  
        	  else if (prefs.getBoolean("prefDisasterMode", false) == true) {
        		  new DeleteTweet(true).execute(info.id); 
        	  }
        	  else
        		  Toast.makeText(this, "No internet connectivity", Toast.LENGTH_SHORT).show();         	  
        	  return true;  
          case PROFILEINFO_ID:
        	  if (connHelper.testInternetConnectivity() && ConnectionHelper.twitter != null )
        		  lookProfile(info ,null);
        	  else 
        		  Toast.makeText(this, "No internet connectivity", Toast.LENGTH_SHORT).show();         
        	  return true;
          case DIRECT_ID:
        	  if (connHelper.testInternetConnectivity() || prefs.getBoolean("prefDisasterMode", false) == true) {
        	    destinationUsername = dbActions.userDbQuery(info, DbOpenHelper.TABLE);
        	    
        	  	showDialog(2);
        	  } 
        	  else
        		Toast.makeText(this, "No internet connectivity", Toast.LENGTH_SHORT).show();   
        	  return true;
        		 
      }
      return super.onContextItemSelected(item);
  }  

private void lookProfile(AdapterContextMenuInfo info, String user) {
	 String username = null;
	 
	if (connHelper.testInternetConnectivity()) {
		if (info != null) {
		  if (!isShowingFavorites)
			  username = dbActions.userDbQuery(info,DbOpenHelper.TABLE );
		  else
			  username = dbActions.userDbQuery(info,DbOpenHelper.TABLE_FAVORITES );
		}
		else 
			username = user;		  
		 Intent intent = new Intent(this,UserInfo.class);
		 intent.putExtra("username", username);
		 startActivity(intent);
	  }
}
  
  @Override
public void onBackPressed() {
	if (isShowingFavorites) {
		changeView(false,DbOpenHelper.TABLE);
		setTitle("Timeline");
	}		
	else {
		if (prefs.getBoolean("prefDisasterMode", false) == false)
			super.onBackPressed();
		else
			Toast.makeText(this, "To exit disable disaster mode first", Toast.LENGTH_SHORT).show();     
	}
}

/* Creates the main Menu of the application */
@Override
  public boolean onCreateOptionsMenu(Menu menu){
	  super.onCreateOptionsMenu(menu);
	 // menu.add(0, REFRESH_ID, 0, "Refresh").setIcon(R.drawable.ic_menu_refresh);
	  menu.add(0, SEND_ID, 1, "Send tweet").setIcon(R.drawable.ic_menu_send);
	  menu.add(0, MENTIONS_ID,2, "Mentions").setIcon(R.drawable.ic_menu_revert);	
	  menu.add(0, FAVORITES_ID, 3, "Favorites").setIcon(android.R.drawable.ic_menu_today);	
	  menu.add(0, RECEIVED_DIRECT_ID, 4, "Direct Messages").setIcon(R.drawable.ic_menu_agenda);	
	  menu.add(0, SETTINGS_ID, 5, "Settings").setIcon(R.drawable.ic_menu_preferences);	
	  menu.add(0, DISASTER_ID, 6, "Disaster Table");
	  menu.add(0, FOLLOWING_ID, 7, "Following");
	  menu.add(0, FOLLOWERS_ID, 8, "Followers");
	  menu.add(0, SEARCH_ID, 9, "Search");
	  menu.add(0, PROFILEINFO_ID,10, "Search User");
	  menu.add(0, LOGOUT_ID, 11, "Logout");
	  //menu.add(0, DETAILS_ID, 12, "App Details");
	  //menu.add(0, EXIT_ID, 11, "Exit");	
	  
	  	
    return true;
  }    
  
  @Override
public boolean onPrepareOptionsMenu(Menu menu) {
	  if (prefs.getBoolean("prefDisasterMode", false) == true)
    	  publishDisasterTweets(false);
	  
	  if (isShowingFavorites) {
		     if (menu.findItem(TIMELINE_ID) == null) {
		    	 menu.add(0, TIMELINE_ID,3 , "Timeline").setIcon(android.R.drawable.ic_menu_agenda);
		    	 menu.removeItem(FAVORITES_ID);
		     }
	  }
	  else {
		  if (menu.findItem(FAVORITES_ID) == null) {
		    	 menu.add(0, FAVORITES_ID,3 , "Favorites").setIcon(android.R.drawable.ic_menu_today);
		    	 menu.removeItem(TIMELINE_ID);
		     }
	  }
	new Thread(new FetchMentions()).start();
	return true;
}

 class FetchMentions implements Runnable {
	  ContentValues values;	
	  ArrayList<Status> results = null;
	  
	  public void run() {
		  if (connHelper.testInternetConnectivity()) {
			  if (ConnectionHelper.twitter == null ) {						    			
				  connHelper.doLogin() ;		
				}
			  try {
				  if (ConnectionHelper.twitter != null) {
					  results = (ArrayList<Status>)ConnectionHelper.twitter.getReplies();
				      if (results != null) {
				    	  for (Status status : results) {
				    		  values = DbOpenHelper.statusToContentValues(status,null); 
				    		  dbActions.insertGeneric(DbOpenHelper.TABLE_MENTIONS, values);
				 	      }	
				     }
				  }
		 	   } 
		   	  catch (Exception e) {   }
		  }
		  results = null;
		  findMentionDisasterTweets();		 	
		  
	  }
  }
  

  @SuppressWarnings("deprecation")
	private void findMentionDisasterTweets() {		 
		 Cursor cursor = dbActions.disasterDbQuery(null, " DESC");		 
		 if (cursor != null) {			 
			 
			 if (cursor.getCount() > 0) {
				 cursor.moveToFirst();
				 for (int i=0; i < cursor.getCount(); i++) {
					 try {
						 String text = cursor.getString(cursor.getColumnIndexOrThrow(DbOpenHelper.C_TEXT));
						 long userId = cursor.getLong(cursor.getColumnIndex(DbOpenHelper.C_USER_ID));
						 if (text.contains("@" + mSettings.getString("user", ""))) {
							 
							 Cursor cursorFriends = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS, 
						    			DbOpenHelper.C_ID + "=" + userId,	null, null);	    	
						     cursorFriends.moveToFirst();
						     String username = cursorFriends.getString(cursorFriends.getColumnIndex(DbOpenHelper.C_USER));	  
						     long id  = cursor.getLong(cursor.getColumnIndexOrThrow(DbOpenHelper.C_ID));
						     long time = cursor.getLong(cursor.getColumnIndexOrThrow(DbOpenHelper.C_CREATED_AT));
						     Date date = new Date(time);
							 User user = new User(username);
						     Status status = new Status(user,text,id,date);
						     
						     ContentValues values = DbOpenHelper.statusToContentValues(status,userId); 
						     values.put(DbOpenHelper.C_IS_DISASTER, TRUE);
						     dbActions.insertGeneric(DbOpenHelper.TABLE_MENTIONS, values);						     
						     
						 }							 
						 cursor.moveToNext();
					 }
					 catch (IllegalArgumentException ex) {
						
						 break;	}
				 }
			 }
		 }	 
	}
  
 
 
// Called when menu item is selected //
  @Override
  public boolean onOptionsItemSelected(MenuItem item){
    
    switch(item.getItemId()){
    
    case SETTINGS_ID:
      // Launch Prefs activity
      Intent i = new Intent(this, Prefs.class);
      startActivityForResult(i, PREF_SCREEN);    
      return true;       
    case EXIT_ID:
  	  finish();
  	  return true;  	  
    case DISASTER_ID:    	   		
    	startActivity(new Intent(this,showDisasterDb.class));     	
    	return true;  
    case FOLLOWING_ID:
      startActivity(new Intent(this,Following.class));
      if (!connHelper.testInternetConnectivity()  )  
    	  Toast.makeText(this, "No internet connectivity", Toast.LENGTH_LONG).show();        
      
  	  return true;  
    case FOLLOWERS_ID:
        startActivity(new Intent(this,Followers.class));
        if (!connHelper.testInternetConnectivity()  )  
      	  Toast.makeText(this, "No internet connectivity", Toast.LENGTH_LONG).show();        
        
    	  return true;   
    case SEND_ID:    	
    	showDialog(0);
    	return true;  
  	  
    case FAVORITES_ID:     	
    	changeView(true,DbOpenHelper.TABLE_FAVORITES);
    	setTitle("Favorites");    
    	return true;
   
    case TIMELINE_ID:
    	changeView(false,DbOpenHelper.TABLE);
    	setTitle("Timeline");
    	return true;
    case MENTIONS_ID:     
    		startActivity(new Intent(this,Mentions.class));     	    
    	return true;
    case LOGOUT_ID:
    	if (prefs.getBoolean("prefDisasterMode", false) == false) {
    	  SharedPreferences.Editor editor = prefs.edit();  
    	  editor.clear();
    	  editor.commit();
    	  editor = mSettings.edit();  
    	  editor.clear();
    	  editor.commit();        	
    	  db.execSQL("DROP TABLE IF EXISTS timeline;");
    	  db.execSQL("DROP TABLE IF EXISTS FavoritesTable;");
    	  db.execSQL("DROP TABLE IF EXISTS MentionsTable;");
    	  db.execSQL("DROP TABLE IF EXISTS DirectMessagesTable;");
    	  db.execSQL("DROP TABLE IF EXISTS PicturesTable;");
    	  db.execSQL("DROP TABLE IF EXISTS FriendsTable;");    	
    	  db.execSQL("DROP TABLE IF EXISTS DirectOutgoingTable;");    	  
    	  dbActions.createTables(this,db);
    	  isRunning = false;
    	  finish();
    	  startActivity(new Intent(this,TwimightActivity.class));
    	}
    	else 
    		Toast.makeText(this, "Disable the disaster mode first", Toast.LENGTH_LONG).show();     
      	  return true;   
    case SEARCH_ID:
    	onSearchRequested();
    	return true;
    case PROFILEINFO_ID:
    	if (connHelper.testInternetConnectivity())
    		showDialog(1);
    	else
    		Toast.makeText(this, "No internet connectivity", Toast.LENGTH_SHORT).show();   
    	return true;
    case RECEIVED_DIRECT_ID:
    	startActivity(new Intent(this,DirectMessagesActivity.class));   
    	return true;
    case DETAILS_ID:
    	if (connHelper.testInternetConnectivity()) {
    		Intent intent = new Intent(Intent.ACTION_VIEW);
        	intent.setData(Uri.parse("market://details?id=ch.ethz.twimight"));
        	startActivity(intent);
    	}    	
    	return true;
    }    
    return false;
  }
  
 

private void changeView(boolean isShowing, String table){
	String query;
	// Get the data from the DB	
    if (table.equals(DbOpenHelper.TABLE))
		query = "SELECT tim._id,user,created_at,status,isDisaster,isFavorite FROM " + table + " AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";

    else
		query = "SELECT tim._id,user,created_at,status FROM " + table + " AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";

	    cursor = dbActions.rawQuery(query);   
	    // cursor = dbActions.queryGeneric(table, null, DbOpenHelper.C_CREATED_AT + " DESC",  "100");

    	startManagingCursor(cursor);
    	Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);
    	cursorPictures.moveToFirst();    
    	// Setup the adapter	    
    	adapter = new TimelineAdapter(this, cursor, cursorPictures);
    	listTimeline.setAdapter(adapter);		   
    	registerForContextMenu(listTimeline);
    	isShowingFavorites =  isShowing;
  }
  
  class RefreshTimeline extends AsyncTask<Void, Void, Boolean> {		
		 ProgressDialog postDialog;     	
		 boolean showDialog;
		 
		 public RefreshTimeline(boolean showDialog) {
			 this.showDialog = showDialog;
		 }
			@Override
			protected void onPreExecute() {
				if (showDialog)
					postDialog = ProgressDialog.show(Timeline.this, 
							"Refreshing Timeline", "Please wait while your timeline is being refreshed", 
							true,	// indeterminate duration
							false); // not cancel-able
			}
			
			@Override
			protected Boolean doInBackground(Void... message ) {
				    cancelNotification();					
		    		if (ConnectionHelper.twitter == null) {		    			
		    			connHelper.doLogin();
		    		}	    		
		    		try {
		    			if (ConnectionHelper.twitter != null) {
		    				ConnectionHelper.twitter.setCount(40);
		    		    	List<Twitter.Status> timeline = ConnectionHelper.twitter.getHomeTimeline();
		    		    	new Thread(new FetchProfilePic(timeline, dbActions, Timeline.this)).start();
		    		    	for (Twitter.Status status : timeline) {		    		    		
		    		    		
		    		    		dbActions.insertIntoTimelineTable(status);    		        		
		    		      	 }		    		        
			    			return true; 
		    			 }
		    			else 
		    				return false;
		    		  } 
		    		  catch (Exception e) {		    			 
		    			  return false;
		    		  }	    					    				 		
			}	
			
			@Override
			protected void onPostExecute(Boolean result) {
				if (showDialog)
					postDialog.dismiss();				
				if (result) {
					cursor.requery();
					if (showDialog)
						Toast.makeText(Timeline.this, "Timeline updated", Toast.LENGTH_LONG).show();
				}									
				else {
					if (showDialog)
						Toast.makeText(Timeline.this, "Timeline not updated", Toast.LENGTH_LONG).show(); 	
				}
			}
		}
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {  		
  		switch (requestCode) {
  		case PREF_SCREEN:
  			
  			if (resultCode == Prefs.BLUE_ENABLED) { 
  				startServices();
  				
  				// broadcast the mode change
  				sendBroadcast(new Intent(Constants.ACTION_DISASTER_MODE));
  			} 
  			
  			else if (resultCode ==  Prefs.DIS_MODE_DISABLED) {  				
  				
  				SharedPreferences.Editor editor = prefs.edit();   
  			    editor.putBoolean("prefDisasterMode", false);   
  			    editor.commit();  			   
  	  			publishDisasterTweets(true);
  	  			if (mBtAdapter != null)
  	  				mBtAdapter.disable();
  	  		    stopService(new Intent(this,DisasterOperations.class));   	  		 
  				stopService(new Intent(this,DevicesDiscovery.class));
  				//stopService(new Intent(this,RandomTweetGenerator.class));
  				  				
  				// broadcast the mode change
  				sendBroadcast(new Intent(Constants.ACTION_DISASTER_MODE));
  				
  	  		    try {
  	  		    	unregisterReceiver(airplaneModeReceiver);
  	  		    } catch (Exception ex) {}
  	  		    
  	  		    editor = mSettings.edit();   
  	  		    editor.remove("firstBroadcastTime");	//battery       
  	  		    editor.remove("numberOfContacts");
  	     		editor.remove("numberOfConnAttempts");
  	     		editor.remove("connAttemptsSucceded");
  	     		editor.remove("isFullSavingActive");
  	     		editor.remove("isPartialSavingActive");
  	     		editor.commit(); 
  	     		
  			}
  			break;	  		
  		}
  } 
 
  private void startServices() {
	  
	  String where = DbOpenHelper.C_MET_AT + "<" + (new Date().getTime() - (3600000*24*5))  ;	
	  dbActions.delete(where, DbOpenHelper.TABLE_ADDRESSES);
	  where = DbOpenHelper.C_CREATED_AT + "<" + (new Date().getTime() - (3600000*24*2))  ;
	  dbActions.delete(where, DbOpenHelper.TABLE_DISASTER);	
	  
	  startService(new Intent(this, DisasterOperations.class));         	         
      startService(new Intent(this, DevicesDiscovery.class));    	         
      //startService(new Intent(this, RandomTweetGenerator.class)); 
      
      IntentFilter filter = new IntentFilter(Constants.ACTION_NEW_DISASTER_TWEET);
      this.registerReceiver(twitterStatusReceiver, filter); 
      filter = new IntentFilter("android.intent.action.SERVICE_STATE");
	  this.registerReceiver(airplaneModeReceiver, filter);	  
       
  }
  

 class PostDisasterTweets implements Runnable {
	
	public synchronized void run() {
		
		try {
		for (int i =0; i< cursorDisaster.getCount(); i++) {			    		
    		long id  = cursorDisaster.getLong(cursorDisaster
		 	        .getColumnIndexOrThrow(DbOpenHelper.C_ID));      		
    		String userDb = cursorDisaster.getString(cursorDisaster.getColumnIndexOrThrow(DbOpenHelper.C_USER));		    		
    		int isFromServ = cursorDisaster.getInt(cursorDisaster.getColumnIndexOrThrow(DbOpenHelper.C_ISFROMSERVER));		    		
    		int hasBeenSent = cursorDisaster.getInt(cursorDisaster.getColumnIndexOrThrow(DbOpenHelper.C_HASBEENSENT));
    		int isValid = cursorDisaster.getInt(cursorDisaster.getColumnIndexOrThrow(DbOpenHelper.C_IS_VALID));
    		String user = mSettings.getString("user", "");		    		
    				    		
    		if ( (user.equals(userDb) && isFromServ == FALSE && hasBeenSent == FALSE && isValid == TRUE) || 
    				(isFromServ == FALSE && hasBeenSent == FALSE && isValid == FALSE )) {
    			//publish my tweets that have not been sent
    			String status = cursorDisaster.getString(cursorDisaster.getColumnIndexOrThrow(DbOpenHelper.C_TEXT));		    		
				setStatus(id,status);
    			
    		} else if (isFromServ == TRUE && hasBeenSent == FALSE) {
    			//publish the retweets 
    			contextActions.retweet(id,DbOpenHelper.TABLE,false);  
    			
    		} 
    		if (!cursorDisaster.isLast())
    			cursorDisaster.moveToNext();
    		else 
    			break;
    		
    			try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {		}    		
    	}
		new RefreshTimeline(false).execute(); 
		} catch (IllegalArgumentException ex){ 
			Log.e(TAG,"error",ex);
		}
		postDisasterTweets = null;
	}
	
}

private void publishDisasterTweets(boolean show) {	  
	  if (connHelper.testInternetConnectivity()) {
		  String query = "SELECT tim._id,user,created_at,status,hasBeenSent,isFromServer,isValid" +
		  		" FROM DisasterTable AS tim, FriendsTable AS f WHERE tim.userCode = f._id ";
		  cursorDisaster = dbActions.rawQuery(query);		  
		  if (cursorDisaster != null) {			  
		    if (cursorDisaster.getCount() > 0) {		    	
		    	cursorDisaster.moveToFirst();
		    	 if (show)
		    		 Toast.makeText(this, "Publishing disaster tweets", Toast.LENGTH_SHORT).show(); 
		    	 if (postDisasterTweets == null) {
		    		 postDisasterTweets = new Thread(new PostDisasterTweets());	
    				 postDisasterTweets.start();
		    	 }
		    }		    
		  } 
	  } 	   
  } 

   void sendMessage(String status) { 	
	  boolean isDisaster = prefs.getBoolean("prefDisasterMode", false);
	  //send a msg if there is connectivity and length is ok (or if we are in disaster mode)
 	  if (connHelper.testInternetConnectivity() || isDisaster == true ) {			
			if (status.length() > 0 && status.length() <= 140 ) {	//if length is ok				
				new SendTask(0,status,isDisaster).execute(); // try to send it
			}	
			else if (status.length() > 140) {
				Toast.makeText(this,"The message is too long ", Toast.LENGTH_LONG).show();
			} 
			else Toast.makeText(this,"No text to be sent ", Toast.LENGTH_LONG).show();			
	      } 
 	  else 
	    	Toast.makeText(this, "No Internet connection", Toast.LENGTH_LONG).show();	
 }
 
 /* posts a Tweet to the twitter server */
 private boolean setStatus(long id, String status) { // I need an id since it is used for publish disaster tweets as well
	 if (ConnectionHelper.twitter == null) {			
	  		connHelper.doLogin();	  							
	 } 
			try {
				// we only publish the status if it does not contain the TWHISPER or TWINTERNAL hashtags
				if (ConnectionHelper.twitter != null & !(status.contains(Constants.TWHISPER_HASHTAG) | status.contains(Constants.TWINTERNAL_HASHTAG))) {					
					resultStatus = ConnectionHelper.twitter.setStatus(status);										
					if (id != 0) { //in case we are automatically publishing disaster tweets
						dbActions.updateDisasterTable(resultStatus.getId().longValue(),id,TRUE,TRUE);
						dbActions.delete(DbOpenHelper.C_ID + "=" + id,DbOpenHelper.TABLE);
					}					 
					return true;
				}
				else
					return false;
			} catch (Exception ex) {					
				return false;
			} 
 }
 
  /**
 * @param activity the activity to set
 */
public static void setActivity(Timeline activity) {
	Timeline.activity = activity;
}

/**
 * @return the activity
 */
public static Timeline getActivity() {
	return activity;
}


class SendTask extends AsyncTask<Void, Void, Boolean> {	
		 long id;
		 String status;		
		 boolean isDisaster;
		 
		 
		 public SendTask(long id, String status, boolean isDisaster) {
			this.id = id; 
			this.status =  status;
			this.isDisaster =  isDisaster;
		 }	 
		
			
			@Override
			protected Boolean doInBackground(Void... message ) {
						return setStatus(id,status);
					}		

			// This is in the UI thread, so we can mess with the UI
			@Override
			protected void onPostExecute(Boolean result) {	
				long resultId;
				if (result) {  //if it has been sent correctly
					
					input.setText("");  //reset text
					if (id == 0) {  //if is just a normal sending and not publishing all disaster tweets
						
						if (connHelper.testInternetConnectivity())
							new RefreshTimeline(false).execute();  
						Toast.makeText(Timeline.this, "Tweet has been sent", Toast.LENGTH_SHORT).show();  
						hasBeenSent = TRUE;
					}					
				}
				else {  //if it hasn't been sent
					if (id == 0) {  // and it is a normal sending
						if (!isDisaster)
							Toast.makeText(Timeline.this, "Tweet not sent", Toast.LENGTH_SHORT).show();
						else
							Toast.makeText(Timeline.this, "Tweet added to disaster table", Toast.LENGTH_SHORT).show();
					}
				}					
				if (isDisaster == true ) {	//if we are in disaster mode	
					
				 	if (status.length() > 0 && status.length() <= 140 ) {	//and length is ok		
				 		
				 		String user = mSettings.getString("user", "not found");
				 		long userId = mSettings.getLong("userId", 0);
				 		String concatenate = status.concat(" " + user) ; 
				 		long time = new Date().getTime();
				 		
				 		if (hasBeenSent == TRUE)
				 			resultId = resultStatus.getId().longValue(); //id from twitter servers
				 		else
				 			resultId = concatenate.hashCode(); //local id				 	
				 		SignedTweet tweet = new SignedTweet(resultId, time, status, user, userId, FALSE,
		    					hasBeenSent, 0, null, null);			 			
			 			RSACrypto crypto = new RSACrypto(mSettings);
			 			byte[] signature = crypto.sign(tweet);
				 		
				 		if (dbActions.saveIntoDisasterDb(resultId,time,time,
							 status,userId,"",FALSE,hasBeenSent, TRUE, 0, signature)) {			 			
				 			
				 			//String mac = mBtAdapter.getAddress();				 			
				 			/*
				 			 * try {
				 				if (RandomTweetGenerator.generatorWriter != null)
				 					RandomTweetGenerator.generatorWriter.write(mac + ":" + user + ":" 
				 							+ concatenate.hashCode() + ":manual:" + time
				 							+ ":" + new Date().toString() + "\n");
							} catch (IOException e) {	}
							*
							*/				 			
				 			//if it has been added successfully
				 			sendBroadcast(new Intent(Constants.ACTION_NEW_DISASTER_TWEET));
							if (!result) { //if it has not been successfully pub. I need to show in the timeline
								//otherwise it means i will receive it from online central servers
								dbActions.copyIntoTimelineTable(concatenate.hashCode(), new Date().getTime(),
										  status,userId,FALSE);
								cursor.requery();								
							}
				 		}
				 		hasBeenSent = FALSE;								   
				 	}
				 	input.setText("");
				}
			}
		}
  
 class DeleteTweet extends AsyncTask<Long, Void, Boolean> {	
	 boolean isDisaster;
	 
	 public DeleteTweet(boolean isDisaster){
		 this.isDisaster = isDisaster;
	 }
				
		@Override
		protected Boolean doInBackground(Long... id ) {
			 return (contextActions.deleteTweet(id[0],isDisaster));           	  
				}		

		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(Boolean result) {		
			cursor.requery();
			if (!isDisaster) {
				if (!result)				
					Toast.makeText(Timeline.this, "Unable to delete the tweet", Toast.LENGTH_SHORT).show();
				else
					Toast.makeText(Timeline.this, "Tweet deleted", Toast.LENGTH_SHORT).show();
			}
			else {
				if (result)
					Toast.makeText(Timeline.this, "Disaster Tweet deleted", Toast.LENGTH_SHORT).show();
				else 
					Toast.makeText(Timeline.this, "Disaster Tweet deleted from disaster table", Toast.LENGTH_SHORT).show();
			}
			
		}
	}
 
  
 class SetFavorite extends AsyncTask<Long, Void, Boolean> {		
	 ProgressDialog postDialog; 
	 int action;
	 String table, query;
	 
	 	public SetFavorite(int action, String table) {
	 		this.action = action;
	 		this.table = table;
	 	}
	 	
		@Override
		protected Boolean doInBackground(Long... id ) {
			return contextActions.favorite(id[0],action, table);	       	   		 
		}		

		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(Boolean result) {			
			if (action == FAVORITE_ID) {
				if (result)
					Toast.makeText(Timeline.this, "Favorite set succesfully", Toast.LENGTH_SHORT).show();
				else 
					Toast.makeText(Timeline.this, "Favorite not set", Toast.LENGTH_SHORT).show();
				
				// After favoriting, refresh the timeline
				sendBroadcast(new Intent(Constants.ACTION_NEW_TWEETS));
			}
			else {
				if (result)
					Toast.makeText(Timeline.this, "Favorite removed succesfully", Toast.LENGTH_SHORT).show();
				else 
					Toast.makeText(Timeline.this, "Favorite not removed", Toast.LENGTH_SHORT).show();				
						
				// After unfavoriting, refresh timeline
				if(table.equals(DbOpenHelper.TABLE_FAVORITES)) {
					query = "SELECT tim._id,user,created_at,status FROM FavoritesTable AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";				
					cursor = dbActions.rawQuery(query);	
				
					if (cursor != null) {						
						
						if (cursor.getCount() == 0)
							changeView(false,DbOpenHelper.TABLE);
	        			else {
	        		 		 Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);	
	        		 		 cursorPictures.moveToFirst();
	        		 		 adapter = new TimelineAdapter(Timeline.this, cursor, cursorPictures);
	        		 		
	   		    			 listTimeline.setAdapter(adapter);		   
	   		    			 registerForContextMenu(listTimeline);
	        			 }	
						
						
					}
			    } else if (table.equals(DbOpenHelper.TABLE)) {
			    	// Get the data from the DB
					query = "SELECT tim._id,user,created_at,status,isDisaster, isFavorite FROM timeline AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";
					cursor = dbActions.rawQuery(query);
					//startManagingCursor(cursor);
					//cursor = dbActions.queryGeneric(DbOpenHelper.TABLE,null, DbOpenHelper.C_CREATED_AT + " DESC" ,"100");
					Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
					cursorPictures.moveToFirst();
					    // Setup the adapter		
					adapter = new TimelineAdapter(Timeline.this, cursor, cursorPictures);		
					listTimeline.setAdapter(adapter); 
					registerForContextMenu(listTimeline);
			    }
			}			
		}
	}
 
 /*
  * Down here we define broadcast receivers
  */

 // Listens to changes from normal mode to disaster mode and vice versa and changes the theme accordingly
 private final BroadcastReceiver modeChangeReceiver = new BroadcastReceiver() {
     @Override
     public void onReceive(Context context, Intent intent) {
	    	 		  
    	 Log.i(TAG,"GOOOOOOOOOOOOOOOT 1111111");
    	 restart();
     }
 };
 
 // Listens to new disaster tweets and  updates the shown timeline accordingly
 private final BroadcastReceiver twitterStatusReceiver = new BroadcastReceiver() {
     @Override
     public void onReceive(Context context, Intent intent) {
    	 		  
		 String query = "SELECT tim._id,user,created_at,status,isDisaster,isFavorite FROM timeline AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";
 	     cursor = dbActions.rawQuery(query); 	    
 	     startManagingCursor(cursor);
    	 Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);	
    	 cursorPictures.moveToFirst();
    		    // Setup the adapter
    	 adapter = new TimelineAdapter(Timeline.this, cursor, cursorPictures);
    	 listTimeline.setAdapter(adapter);   
     }
 };
 
 // Listens to airplane mode notification and stops disaster mode (no bluetooth in airplane mode!)
 private final BroadcastReceiver airplaneModeReceiver = new BroadcastReceiver() {
     @Override
     public void onReceive(Context context, Intent intent) {           
           int isModeOn = Settings.System.getInt(context.getContentResolver(),
        		   Settings.System.AIRPLANE_MODE_ON, 0);
           if (isModeOn == TRUE) {
        	   if (prefs.getBoolean("prefDisasterMode", false) == true) {
        		   stopService(new Intent(Timeline.this,DisasterOperations.class));
        		   stopService(new Intent(Timeline.this, DevicesDiscovery.class));    	         
        		  // stopService(new Intent(Timeline.this, RandomTweetGenerator.class)); 
        		   Toast.makeText(Timeline.this, "Airplane mode on, disaster mode will be restarted as soon as it will be switch off",
        				   Toast.LENGTH_LONG).show();         		  
        	   }
           }
           else if (isModeOn == FALSE) {
        	   if (prefs.getBoolean("prefDisasterMode", false) == true) {
        		  try {
        		   startService(new Intent(Timeline.this,DisasterOperations.class));
        		   startService(new Intent(Timeline.this, DevicesDiscovery.class));    	         
        		  // startService(new Intent(Timeline.this, RandomTweetGenerator.class)); 
        		  } catch (Exception ex) {}
        	   }
        	   
           } 
     }
};

// Listens to and publishes tweets sent by other apps
private final BroadcastReceiver externalAppReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {	  
   
   	 if (intent.getBooleanExtra("publish", false) )
   		 sendMessage(intent.getStringExtra("tweet"));  	 
   	   
    }
};

// Listens to the result from sending a direct message and updates the input field accordingly
private final BroadcastReceiver directMsgSentReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {   
    	int result = intent.getIntExtra("result", DirectMsgTask.NOT_SENT );
    	if (result == DirectMsgTask.SENT) 
    		inputDirect.setText("");
   	   
    }
};


  
}