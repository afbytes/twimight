package ch.ethz.twimight;

import winterwell.jtwitter.OAuthSignpostClient;
import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.TwitterAccount;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Provides functionality to login with Twitter
 * @author pcarta
 * @author thossmann
 *
 */
public class ConnectionHelper  {
	SharedPreferences mSettings;
	ConnectivityManager connec;
	
	public static Twitter twitter = null;
	
	public static final String TAG = "ConnectionHelper"; // for log output

/**
 * Login with Twitter
 * @param prefs SharedPreferences with user token and user secret
 * @param conn ConnectivityManager
 */
ConnectionHelper(SharedPreferences prefs, ConnectivityManager conn) {
	connec = conn;
	mSettings = prefs;		
}

/**
 * Verifies credentials with Twitter
 * @return bool credentials valid?
 */
public boolean verifyLogin() {
	try {
		TwitterAccount twitterAcc = new TwitterAccount(twitter);
		twitterAcc.verifyCredentials();
		Log.i(TAG, "Credentials verified");
		return true;
			
	} catch (Exception ex) {	        				      				
		return false;
	}	
 }

 /**
  * Creates twitter object with the credentials from the shared preferences and verifies it
  * @return bool success of login 
  */
 public boolean doLogin() {
	 
	// get token and secred from shared prefs
	String mToken = mSettings.getString(OAUTH.USER_TOKEN, null);
	String mSecret = mSettings.getString(OAUTH.USER_SECRET, null);
	if(!(mToken == null || mSecret == null) ) {
		// get ready for OAuth
		OAuthSignpostClient client = new OAuthSignpostClient(OAUTH.CONSUMER_KEY, OAUTH.CONSUMER_SECRET, mToken, mSecret);
		twitter = new Twitter(null ,client);
		// check the credentials
		return verifyLogin();       	
	        	               
	} 			
	return false;		
} 

 /**
  * Checks the network state (UMTS? WIFI?)
  * @return bool do we have connectivity?
  */
 public boolean testInternetConnectivity() {
	
	 // poll network state 
	 if(connec.getNetworkInfo(0).getState() == NetworkInfo.State.CONNECTED || //UMTS
			   connec.getNetworkInfo(1).getState() == NetworkInfo.State.CONNECTING || //WiFi
			   connec.getNetworkInfo(1).getState() == NetworkInfo.State.CONNECTED) {
		   return true;
	 } else {
		 return false;
	 }
}	

}
