package ch.ethz.twimight;

import winterwell.jtwitter.OAuthSignpostClient;
import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.TwitterAccount;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class ConnectionHelper  {
	SharedPreferences mSettings;
	public String mToken;
	public String mSecret;
	ConnectivityManager connec;
	public static Twitter twitter = null;  

		
ConnectionHelper(SharedPreferences prefs, ConnectivityManager conn) {
	connec = conn;
	mSettings = prefs;	
	
}

 boolean verifyLogin() {
	 try {
			TwitterAccount twitterAcc = new TwitterAccount(twitter);
			twitterAcc.verifyCredentials();
			Log.i("connHelper","true");
			return true;
			
			} catch (Exception ex) {	        				      				
				return false;
			}	
 }

 boolean  doLogin() {
	 
		mToken = mSettings.getString(OAUTH.USER_TOKEN, null);
		mSecret = mSettings.getString(OAUTH.USER_SECRET, null);
		if(!(mToken == null || mSecret == null) ) {	      
	        	OAuthSignpostClient client = new OAuthSignpostClient(OAUTH.CONSUMER_KEY, 
			    						OAUTH.CONSUMER_SECRET, mToken, mSecret);	        		        		
	        		twitter = new Twitter(null ,client);	        		
	        		return verifyLogin();       	
	        	               
		 } 			
		return false;		
	  } 

 public boolean testInternetConnectivity() {
	
	  
	   if ( connec.getNetworkInfo(0).getState() == NetworkInfo.State.CONNECTED || //UMTS
			   connec.getNetworkInfo(1).getState() == NetworkInfo.State.CONNECTING || //WiFi
			   connec.getNetworkInfo(1).getState() == NetworkInfo.State.CONNECTED) {
		   return true;
	            } else return false;
}	

}
