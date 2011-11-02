package ch.ethz.twimight;

import java.util.Date;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

/**
 * Main activity of Twimight! Shows the login button, checks if updater is running and starts the timeline activity.
 * @author pcarta
 * @author thossmann
 *
 */
public class TwimightActivity extends Activity implements OnClickListener{

	static final String TAG = "Twimight";    

	SharedPreferences mSettings;
	ConnectionHelper connHelper;
	Button buttonOAuth;
	static PendingIntent restartIntent;

	private static TwimightActivity instance = null; /** The single instance of this class */

	/**
	 * returns the one instance of this activity
	 */
	public static TwimightActivity getInstance() {
		return instance;
	}

	/**
	 * onClick handler of the Twitter login button
	 */
	@Override
	public void onClick(View src) {
		switch (src.getId()) {		
		case R.id.buttonOAuth:
			startActivity(new Intent(this,OAUTH.class));	
			break;						
		}    
	}      

	/** 
	 * onCreate: Shows the timeline or login button 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}

		Log.i(TAG,"inside on create");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);  

		instance = this;

		// check if the updater service is running
		if(!isUpdaterServiceRunning()){
			startService(new Intent(this, UpdaterService.class));
			Log.i(TAG,"Updater service started");
		}

		if (Timeline.isRunning) {
			finish();
			startActivity(new Intent(this, Timeline.class));
		}
		else {
			mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);          
			// find views by id
			buttonOAuth = (Button) findViewById(R.id.buttonOAuth);

			restartIntent = PendingIntent.getActivity(this.getBaseContext(), 0, 
					new Intent(getIntent()), getIntent().getFlags());

			// Triggers RSA key generation
			new Thread(new GenerateKeys()).start();

			// Login
			ifTokensTryLogin();

			// Add listeners       
			buttonOAuth.setOnClickListener(this);      
		}

	}    


	/**
	 * onRestart: checks if updater service is still running and logs us in
	 */
	@Override
	protected void onRestart() {
		super.onRestart();
		// check if the updater service is running
		if(!isUpdaterServiceRunning()){
			startService(new Intent(this, UpdaterService.class));
			Log.i(TAG,"Updater service started");
		}

		// log in
		ifTokensTryLogin();
	}

	/**
	 * Validates the tokens and starts the timeline activity.
	 */
	private void ifTokensTryLogin() {

		if(mSettings.contains(OAUTH.USER_TOKEN) && mSettings.contains(OAUTH.USER_SECRET)) {    			
			ConnectivityManager connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
			connHelper = new ConnectionHelper(mSettings,connec); 
			new Login().execute();
			startActivity(new Intent(this, Timeline.class));
		} 

	}

	/**
	 * Gets the information if the service is running from the OS.
	 * @return true if UpdaterService is running, false otherwise.
	 */
	private boolean isUpdaterServiceRunning() {
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
			if ("ch.ethz.twimight.UpdaterService".equals(service.service.getClassName())) {
				Log.i(TAG, "UpdaterService is running");
				return true;
			}
		}
		return false;
	}

	/**
	 * 
	 * Thread to log in with the Twitter credentials.
	 * @author pcarta
	 *
	 */
	class Login extends AsyncTask<Long, Void, String> {

		/**
		 * Main function of AsyncTask. Checks connectivity and triggers the login.
		 */
		@Override
		protected String doInBackground(Long... id ) {
			if (connHelper.testInternetConnectivity()) {
				boolean result = connHelper.doLogin();
				Log.i(TAG,"" + result);
				if (result) {    				
					return "Login Successful";							   			    
				} else 
					return "Incorrect Login, showing old tweets"; 				
			}
			else 
				return "No internet connectivity";	

		}		

		/** 
		 * Shows a Toast with the Login return message.
		 */
		@Override
		protected void onPostExecute(String message) {				
			Toast.makeText(TwimightActivity.this, message, Toast.LENGTH_SHORT).show();
			finish();
		}
	}


	/**
	 * Thread to create a pair of public and private keys.
	 * @author pcarta
	 * TODO: This does not belong here!
	 */
	class GenerateKeys implements Runnable {

		@Override
		public void run() {
			long time = mSettings.getLong("generated_at", 0);
			if (time < (new Date().getTime() - Constants.KEY_GENERATION_INTERVAL)) {
				RSACrypto crypto = new RSACrypto(mSettings);
				crypto.createKeys();
				SharedPreferences.Editor editor = mSettings.edit();
				editor.remove("PublicKeyPosted");
				editor.commit();
			}

		}

	}


}