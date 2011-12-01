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

package ch.ethz.twimight.activities;

import junit.framework.Assert;
import oauth.signpost.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthProvider;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import oauth.signpost.exception.OAuthNotAuthorizedException;
import ch.ethz.twimight.R;
import ch.ethz.bluetest.credentials.Obfuscator;
import ch.ethz.twimight.data.DBOpenHelper;
import ch.ethz.twimight.data.RevocationDBHelper;
import ch.ethz.twimight.location.LocationAlarm;
import ch.ethz.twimight.net.opportunistic.ScanningService;
import ch.ethz.twimight.net.tds.TDSAlarm;
import ch.ethz.twimight.net.tds.TDSThread;
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.security.CertificateManager;
import ch.ethz.twimight.security.KeyManager;
import ch.ethz.twimight.util.Constants;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

/**
 * Logging in to Twitter.
 * TODO: Dump the state in a file upon logout and read it again when logging in.
 * @author thossmann
 *
 */
public class LoginActivity extends Activity implements OnClickListener{

	private static final String TAG = "LoginActivity"; /** For logging */
	
	// shared preferences
	private static final String TWITTER_ID = "twitter_id"; /** Name of Twitter ID in shared preferences */
	
	private static final String TWITTER_ACCESS_TOKEN = "twitter_access_token"; /** Name of access token in preference */
	private static final String TWITTER_ACCESS_TOKEN_SECRET = "twitter_access_token_secret"; /** Name of secret in preferences */

	private static final String TWITTER_REQUEST_TOKEN = "twitter_request_token"; /** Name of the request token in preferences */
	private static final String TWITTER_REQUEST_TOKEN_SECRET = "twitter_request_token_secret"; /** Name of the request token secret in preferences */
	
	// twitter urls
	private static final String TWITTER_REQUEST_TOKEN_URL = "http://twitter.com/oauth/request_token"; 
	private static final String TWITTER_ACCESS_TOKEN_URL = "http://twitter.com/oauth/access_token";
	private static final String TWITTER_AUTHORIZE_URL = "http://twitter.com/oauth/authorize";
	private static final Uri CALLBACK_URI = Uri.parse("my-app://bluetest");

	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "onCreate");
		super.onCreate(savedInstanceState);
		
		// if we have token and secret, launch the timeline activity
		if(hasAccessToken(this) && hasAccessTokenSecret(this)){
			Log.i(TAG, "we have the tokens, do we have connectivity?");
			
			ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
			if(cm.getActiveNetworkInfo()!=null && cm.getActiveNetworkInfo().isConnected()){
				// call the twitter service to verify the credentials
				Intent i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_LOGIN);
				startService(i);
			} else {
				Toast.makeText(this,"Not connected to the Internet, showing old Tweets!", Toast.LENGTH_LONG).show();
			}
			startTimeline();
		}
		
		// We get the URI when we are called back from Twitter
		Uri uri = getIntent().getData();
		if(uri != null){
			getAccessTokens(uri);
			return;
		}
		
		// if we don't have request token and secret, we show the login button
		Log.i(TAG, "we do not have the tokens, show login button");
		setContentView(R.layout.login);
		Button buttonLogin = (Button) findViewById(R.id.buttonLogin);
		buttonLogin.setOnClickListener(this);
		
	}

	/**
	 * When the login button is pressed
	 */
	@Override
	public void onClick(View view) {
		Log.i(TAG, "onClick");
		switch (view.getId()) {		
		case R.id.buttonLogin:
			ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
			if(cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected()){
				getRequestToken();
			} else {
				Toast.makeText(this,"Not connected to the Internet, please try again later!", Toast.LENGTH_LONG).show();
			}
			break;						
		}
	}
	
	/**
	 * onDestroy
	 */
	@Override
	public void onDestroy(){
		Log.i(TAG, "destroying login");
		super.onDestroy();
	}
	
	/**
	 * Upon pressing the login button, we first get Request tokens from Twitter.
	 * @param context
	 */
	private void getRequestToken(){

		OAuthConsumer consumer = new CommonsHttpOAuthConsumer(Obfuscator.getKey(),Obfuscator.getSecret());		
		OAuthProvider provider = new CommonsHttpOAuthProvider (TWITTER_REQUEST_TOKEN_URL,TWITTER_ACCESS_TOKEN_URL,TWITTER_AUTHORIZE_URL);

		provider.setOAuth10a(true);
		
		try {
			// TODO: This has to be done in a Thread!
			String authUrl = provider.retrieveRequestToken(consumer, CALLBACK_URI.toString());
			setRequestToken(consumer.getToken(), this);
			setRequestTokenSecret(consumer.getTokenSecret(), this);
			
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
			intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
			intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

			// Show twitter login in Browser.
			this.startActivity(intent);
			
			// now we have the request token.
		} catch (OAuthMessageSignerException e) {
			e.printStackTrace();
			Toast.makeText(this,"signing the request failed ", Toast.LENGTH_LONG).show();
			return;
		} catch (OAuthNotAuthorizedException e) {
			e.printStackTrace();
			Toast.makeText(this,"Twitter is not reachable at the moment, please try again later", Toast.LENGTH_LONG).show();
			return;
		} catch (OAuthExpectationFailedException e) {
			e.printStackTrace();
			Toast.makeText(this,"required parameters were not correctly set", Toast.LENGTH_LONG).show();
			return;
		} catch (OAuthCommunicationException e) {
			e.printStackTrace();
			Toast.makeText(this,"server communication failed, check internet connectivity", Toast.LENGTH_SHORT).show();
			return;
		}
	}
	
	/**
	 * Get an access token and secret from Twitter
	 */
	private void getAccessTokens(Uri uri) {
		String requestToken = getRequestToken(this);
		String requestSecret = getRequestTokenSecret(this);

		OAuthConsumer consumer = new CommonsHttpOAuthConsumer(Obfuscator.getKey(),Obfuscator.getSecret());		
		OAuthProvider provider = new CommonsHttpOAuthProvider (TWITTER_REQUEST_TOKEN_URL,TWITTER_ACCESS_TOKEN_URL,TWITTER_AUTHORIZE_URL);

		provider.setOAuth10a(true);

		boolean success = false;
		
		String accessToken = null;
		String accessSecret = null;
		
		try {
			if(!(requestToken == null || requestSecret == null)) {
				consumer.setTokenWithSecret(requestToken, requestSecret);
			}
			String otoken = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
			String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);

			// This is a sanity check which should never fail - hence the assertion
			Assert.assertEquals(otoken, consumer.getToken());

			// This is the moment of truth - we could throw here
			// TODO: This should be done in a thread!
			provider.retrieveAccessToken(consumer, verifier);
			
			// Now we can retrieve the goodies
			accessToken = consumer.getToken();
			accessSecret = consumer.getTokenSecret();
			
			success = true;

		} catch (OAuthMessageSignerException e) {
			e.printStackTrace();
			Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();
			success = false;
			finish();
		} catch (OAuthNotAuthorizedException e) {
			e.printStackTrace();
			Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();
			success = false;
			finish();
		} catch (OAuthExpectationFailedException e) {
			e.printStackTrace();
			Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();
			success = false;
			finish();
		} catch (OAuthCommunicationException e) {
			e.printStackTrace();
			Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();
			success = false;
			finish();
			
		} finally {
		
			// save the access token and secret
			setAccessToken(accessToken, this);
			setAccessTokenSecret(accessSecret, this);

			// Clear the request token and secret
			setRequestToken(null, this);
			setRequestTokenSecret(null, this);

			// As a last step, we verify the correctness of the credentials and retrieve our Twitter ID
			if(success){
				// call the twitter service to verify the credentials
				Intent i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_LOGIN);
				startService(i);
				startTimeline();

			}
		}
		
				
	}

	private void startTimeline() {
		startAlarms();
		Intent i = new Intent(this, ShowTweetListActivity.class);
		i.putExtra("login", true);
		startActivity(i);
		finish();
	}

	/**
	 * Start all the enabled alarms and services.
	 */
	private void startAlarms() {
		// Start the alarm for communication with the TDS
		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("prefTDSCommunication", Constants.TDS_DEFAULT_ON)==true){
			new TDSAlarm(this, Constants.TDS_UPDATE_INTERVAL);
		}
		
		
		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON)==true){
			startService(new Intent(this, ScanningService.class));
		}
		
		
		// Start the location update alarm
		
		if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("prefLocationUpdates", Constants.LOCATION_DEFAULT_ON)==true){
			new LocationAlarm(this, Constants.LOCATION_UPDATE_TIME);
		}
				
	}
	
	/**
	 * Stop all the alarms and services
	 */
	private static void stopAlarms(Context context) {

		TDSAlarm.stopTDSCommuniction(context);
		
		ScanningService.stopScanning();
		
		LocationAlarm.stopLocationUpdate(context);				
	}
	
	/**
	 * Deleting all the state
	 */
	public static void logout(Context context){
		
		// Delete persistent Twitter update information
		TwitterService.setFavoritesSinceId(null, context);
		TwitterService.setLastFavoritesUpdate(null, context);
		TwitterService.setMentionsSinceId(null, context);
		TwitterService.setLastMentionsUpdate(null, context);
		TwitterService.setTimelineSinceId(null, context);
		TwitterService.setLastTimelineUpdate(null, context);
		TwitterService.setLastFriendsUpdate(null, context);
		TwitterService.setLastFollowerUpdate(null, context);
		
		// Stop all alarms
		stopAlarms(context);
		TDSThread.resetLastUpdate(context);
		TDSThread.resetUpdateInterval(context);
		
		// Delete our Twitter ID
		setTwitterId(null, context);
		
		// Delete Access token and secret
		setAccessToken(null, context);
		setAccessTokenSecret(null, context);
		
		// Delete Request token and secret
		setRequestToken(null, context);
		setRequestTokenSecret(null, context);
		
		// Delete key and certificate
		KeyManager km = new KeyManager(context);
		km.deleteKey();
		CertificateManager cm = new CertificateManager(context);
		cm.deleteCertificate();
		
		// Flush DB
		DBOpenHelper dbHelper = DBOpenHelper.getInstance(context);
		dbHelper.flushDB();
		
		// Flush revocation list
		RevocationDBHelper rm = new RevocationDBHelper(context);
		rm.open();
		rm.flushRevocationList();
		
		// Start login activity
		Intent intent = new Intent(context, LoginActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
		
	}
	
	/**
	 * Saves a token (in string format) to shared prefs.
	 */
	public static void setAccessToken(String token, Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(TWITTER_ACCESS_TOKEN, token);
		prefEditor.commit();
		
	}
	
	/**
	 * Saves a secret (in string format) to shared prefs.
	 * @param secret
	 * @param context
	 */
	public static void setAccessTokenSecret(String secret, Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(TWITTER_ACCESS_TOKEN_SECRET, secret);
		prefEditor.commit();
	}
	
	/**
	 * Gets the twitter access token from the shared preferences.
	 * @param context
	 * @return
	 */
	public static String getAccessToken(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_ACCESS_TOKEN, null);
	}
	
	/**
	 * Returns the secret stored in shared preferences
	 * @param context
	 * @return
	 */
	public static String getAccessTokenSecret(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_ACCESS_TOKEN_SECRET, null);
	}
	
	/**
	 * True if we have an access token in the shared preferences, false otherwise
	 * @param context
	 * @return
	 */
	public static boolean hasAccessToken(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_ACCESS_TOKEN, null)!= null;
	}
	
	/**
	 * True if we have a secret in the shared preferences, false otherwise
	 * @param context
	 * @return
	 */
	public static boolean hasAccessTokenSecret(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_ACCESS_TOKEN_SECRET, null)!=null;
	}
	
	/**
	 * Saves a request token (in string format) to shared prefs.
	 */
	public static void setRequestToken(String token, Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(TWITTER_REQUEST_TOKEN, token);
		prefEditor.commit();
		
	}
	
	/**
	 * Saves a secret (in string format) to shared prefs.
	 * @param secret
	 * @param context
	 */
	public static void setRequestTokenSecret(String secret, Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(TWITTER_REQUEST_TOKEN_SECRET, secret);
		prefEditor.commit();
	}
	
	/**
	 * True if we have a request token in the shared preferences, false otherwise
	 * @param context
	 * @return
	 */
	public static boolean hasRequestToken(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_REQUEST_TOKEN, null)!= null;
	}
	
	/**
	 * True if we have a request token secret in the shared preferences, false otherwise
	 * @param context
	 * @return
	 */
	public static boolean hasRequestTokenSecret(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_REQUEST_TOKEN_SECRET, null)!=null;
	}
	
	/**
	 * Gets the twitter request token from the shared preferences.
	 * @param context
	 * @return
	 */
	public static String getRequestToken(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_REQUEST_TOKEN, null);
	}
	
	/**
	 * Returns the secret stored in shared preferences
	 * @param context
	 * @return
	 */
	public static String getRequestTokenSecret(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_REQUEST_TOKEN_SECRET, null);
	}
	
	/**
	 * Stores the local Twitter ID in the shared preferences
	 * @param id
	 * @param context
	 */
	public static void setTwitterId(String id, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(TWITTER_ID, id);
		prefEditor.commit();
	}
	
	/**
	 * Gets the Twitter ID from shared preferences
	 * @param context
	 * @return
	 */
	public static String getTwitterId(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_ID, null);
	}
	
	/**
	 * Do we have a Twitter ID in shared preferences?
	 * @param context
	 * @return
	 */
	public static boolean hasTwitterId(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(TWITTER_ID, null)!=null;
	}
	
	
}
