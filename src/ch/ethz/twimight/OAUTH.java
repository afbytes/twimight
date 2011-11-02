package ch.ethz.twimight;

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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

/**
 * 		   This code is based on "BLOA" example,
 *         http://github.com/brione/Brion-Learns-OAuth 
 */



public class OAUTH extends Activity {
	private static final String TAG = "OAUTH";

	static final String CONSUMER_KEY="zgzydHlCf081YeoPiGDTA";

	// IMPORTANT!!!! Replace the following with the Twimight consumer secret
	// To obtain the secret, get in touch with @twimight
	static final String CONSUMER_SECRET="dummy";

	public static final String USER_TOKEN = "user_token";
	public static final String USER_SECRET = "user_secret";
	public static final String REQUEST_TOKEN = "request_token";
	public static final String REQUEST_SECRET = "request_secret";

	public static final String TWITTER_REQUEST_TOKEN_URL = "http://twitter.com/oauth/request_token";
	public static final String TWITTER_ACCESS_TOKEN_URL = "http://twitter.com/oauth/access_token";
	public static final String TWITTER_AUTHORIZE_URL = "http://twitter.com/oauth/authorize";

	private static final Uri CALLBACK_URI = Uri.parse("my-app://twitt");

	public static final String PREFS = "MyPrefsFile";

	private OAuthConsumer mConsumer = null;
	private OAuthProvider mProvider = null;

	SharedPreferences mSettings;

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);   

		mSettings = this.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		if (!(mSettings.contains(OAUTH.USER_TOKEN) && mSettings.contains(OAUTH.USER_SECRET)) ) {			

			// We don't need to worry about any saved states: we can reconstruct the state
			mConsumer = new CommonsHttpOAuthConsumer(CONSUMER_KEY,CONSUMER_SECRET);		
			mProvider = new CommonsHttpOAuthProvider (TWITTER_REQUEST_TOKEN_URL,TWITTER_ACCESS_TOKEN_URL,
					TWITTER_AUTHORIZE_URL);

			// It turns out this was the missing thing to making standard Activity launch mode work
			mProvider.setOAuth10a(true);

			Intent i = this.getIntent();
			if (i.getData() == null) {		
				try {
					// This is really important. If you were able to register your real callback Uri with Twitter, and not some fake Uri
					// like I registered when I wrote this example, you need to send null as the callback Uri in this function call. Then
					// Twitter will correctly process your callback redirection
					String authUrl = mProvider.retrieveRequestToken(mConsumer, CALLBACK_URI.toString());
					saveRequestInformation(mSettings, mConsumer.getToken(), mConsumer.getTokenSecret());
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl));
					intent.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
					intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
					this.startActivity(intent);
					// At this point I should have catched the Request Token
				} catch (OAuthMessageSignerException e) {
					e.printStackTrace();
					Toast.makeText(this,"signing the request failed " , Toast.LENGTH_LONG).show();
					finish();
				} catch (OAuthNotAuthorizedException e) {
					e.printStackTrace();
					Toast.makeText(this,"Twitter is not reachable at the moment, please try again later" ,
							Toast.LENGTH_LONG).show();
					finish();
				} catch (OAuthExpectationFailedException e) {
					e.printStackTrace();
					Toast.makeText(this,"required parameters were not correctly set" , Toast.LENGTH_LONG).show();
					finish();
				} catch (OAuthCommunicationException e) {
					e.printStackTrace();
					Toast.makeText(this,"server communication failed, check internet connectivity" ,
							Toast.LENGTH_SHORT).show();
					finish();
				}
			}
		} else  {
			finish();		 
		}
	}




	@Override
	protected void onResume() {
		super.onResume();

		Uri uri = getIntent().getData();
		if (uri != null && CALLBACK_URI.getScheme().equals(uri.getScheme())) {
			String token = mSettings.getString(OAUTH.REQUEST_TOKEN, null);
			String secret = mSettings.getString(OAUTH.REQUEST_SECRET, null);

			try {
				if(!(token == null || secret == null)) {
					mConsumer.setTokenWithSecret(token, secret);
				}
				String otoken = uri.getQueryParameter(OAuth.OAUTH_TOKEN);
				String verifier = uri.getQueryParameter(OAuth.OAUTH_VERIFIER);

				// We send out and save the request token, but the secret is not the same as the verifier
				// Apparently, the verifier is decoded to get the secret, which is then compared - crafty
				// This is a sanity check which should never fail - hence the assertion
				Assert.assertEquals(otoken, mConsumer.getToken());

				// This is the moment of truth - we could throw here
				mProvider.retrieveAccessToken(mConsumer, verifier);
				// Now we can retrieve the goodies
				token = mConsumer.getToken();
				secret = mConsumer.getTokenSecret();
				OAUTH.saveAuthInformation(mSettings, token, secret);
				// Clear the request stuff, now that we have the real thing
				OAUTH.saveRequestInformation(mSettings, null, null);

			} catch (OAuthMessageSignerException e) {
				e.printStackTrace();
				Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();

			} catch (OAuthNotAuthorizedException e) {
				e.printStackTrace();
				Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();
			} catch (OAuthExpectationFailedException e) {
				e.printStackTrace();
				Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();
			} catch (OAuthCommunicationException e) {
				e.printStackTrace();
				Toast.makeText(this,"Error authenticating" , Toast.LENGTH_LONG).show();
			} finally {				
				finish();	
				startActivity(new Intent(this, TwimightActivity.class ));

			}
		}  
	}





	public static void saveRequestInformation(SharedPreferences settings, String token, String secret) {
		// null means to clear the old values
		SharedPreferences.Editor editor = settings.edit();
		if(token == null) {
			editor.remove(OAUTH.REQUEST_TOKEN);
			Log.d(TAG, "Clearing Request Token");
		}
		else {
			editor.putString(OAUTH.REQUEST_TOKEN, token);
			Log.d(TAG, "Saving Request Token: " + token);
		}
		if (secret == null) {
			editor.remove(OAUTH.REQUEST_SECRET);
			Log.d(TAG, "Clearing Request Secret");
		}
		else {
			editor.putString(OAUTH.REQUEST_SECRET, secret);
			Log.d(TAG, "Saving Request Secret: " + secret);
		}
		editor.commit();

	}

	public static void saveAuthInformation(SharedPreferences settings, String token, String secret) {
		// null means to clear the old values
		SharedPreferences.Editor editor = settings.edit();
		if(token == null) {
			editor.remove(OAUTH.USER_TOKEN);
			Log.d(TAG, "Clearing OAuth Token");
		}
		else {
			editor.putString(OAUTH.USER_TOKEN, token);
			Log.d(TAG, "Saving OAuth Token: " + token);
		}
		if (secret == null) {
			editor.remove(OAUTH.USER_SECRET);
			Log.d(TAG, "Clearing OAuth Secret");
		}
		else {
			editor.putString(OAUTH.USER_SECRET, secret);
			Log.d(TAG, "Saving OAuth Secret: " + secret);
		}
		editor.commit();

	}

}

