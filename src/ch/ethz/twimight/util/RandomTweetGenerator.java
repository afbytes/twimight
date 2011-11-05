package ch.ethz.twimight.util;

import java.util.Date;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.util.LogFilesOperations;
import ch.ethz.twimight.net.twitter.OAUTH;
import ch.ethz.twimight.net.RSACrypto;
import ch.ethz.twimight.TweetContextActions;
import ch.ethz.twimight.data.TweetDbActions;
import ch.ethz.twimight.UpdaterService;
import ch.ethz.twimight.net.opportunistic.packets.SignedTweet;
import ch.ethz.twimight.util.Constants;

public class RandomTweetGenerator extends Service {
	 
	private static final String TAG = "RandomTweetGenerator";
	//Handler hand;	

	TweetDbActions dbActions = UpdaterService.getDbActions();
	TweetContextActions contextActions;
	Handler hand;
	ConnectionHelper connHelper;
	SharedPreferences mSettings,prefs; 
	 private BluetoothAdapter mBtAdapter;
	//static FileWriter generatorWriter;
	GenerateRandomTweets generateRandTweets =  null;	
	WakeLock wakeLock;	
	String mac;
	 @Override
	public void onCreate() {
		
		super.onCreate();		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);		    
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);
		//String user = mSettings.getString("user", "");
	    //dbActions = new TweetDbActions();	 
	    hand = new Handler();
	    generateRandTweets = new GenerateRandomTweets();	      
	    hand.post(generateRandTweets);   
	    PowerManager mgr = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
	    wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
	    wakeLock.acquire();
	    LogFilesOperations logOps = new LogFilesOperations();
	    logOps.createLogsFolder();
	   // generatorWriter = logOps.createLogFile("TweetsGenerated_" + user);
      	mBtAdapter = BluetoothAdapter.getDefaultAdapter();
      	mac = mBtAdapter.getAddress();
	}

	@Override
	  public IBinder onBind(Intent intent) {
	    return null;
	  }
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {	    
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;
	}
	
	 @Override
	    public void onDestroy() {	       
	       
	        hand.removeCallbacks(generateRandTweets); 	        
	        wakeLock.release();	        
	       /* try {	        	
	    		generatorWriter.close();		
	    	  } 
	    	catch (IOException e) {}	
	    	*/    	
	        super.onDestroy();	       
	    } 
	 
  class GenerateRandomTweets implements Runnable {	
		  
		  String user = mSettings.getString("user", "");
		  long userId = mSettings.getLong("userId", 0);
		  
		  public void run() {					  
			  long tweetsNumber = Math.round(Math.random())+1;			 
    	      
			  for (int i=0; i<tweetsNumber; i++) {
				  String status = "random tweet " + Math.round(Math.random() * 10000) + " " + user + " " + Constants.TWHISPER_HASHTAG + " " + Constants.TWINTERNAL_HASHTAG;	
				  long time = new Date().getTime();
				  
				  SignedTweet tweet = new SignedTweet(status.hashCode(), time, status, user, userId, Constants.FALSE,
	    					Constants.TRUE, 0, null, null);			 			
		 			RSACrypto crypto = new RSACrypto(mSettings);
		 			byte[] signature = crypto.sign(tweet);
				  
				  if (dbActions.saveIntoDisasterDb(status.hashCode(),time,time,status,userId,"",
						  Constants.FALSE,Constants.TRUE, Constants.TRUE, 0,signature)) {
					 /* try {
						generatorWriter.write(mac + ":" + user + ":" + status.hashCode() + ":random:" + time
								+ ":" + new Date().toString() + "\n");						
					} 
					  catch (IOException e) {	}
					  */
					  sendBroadcast(new Intent(Constants.ACTION_NEW_DISASTER_TWEET));
					  //dbActions.copyIntoTimelineTable(status.hashCode(),time, status,user,FALSE);				
				  }
				  
			  } 			 
			  long delay = Math.round(Math.random()*240000) + 840000;		
			  
			  hand.postDelayed(this,delay);  
		  }	   
	  }

}
