package ch.ethz.twimight;

import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.net.twitter.OAUTH;
import winterwell.jtwitter.Twitter;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * Shows the preferences.
 * @author pcarta
 * @author thossmann
 */
public class PrefsActivity extends PreferenceActivity{

	SharedPreferences mSettings,prefs;
	OnSharedPreferenceChangeListener prefListener;
	ConnectionHelper connHelper;
	BluetoothAdapter mBluetoothAdapter;
	EditTextPreference mEditTextUsername;

	String prova;
	String userName = "";
	static final int BLUE_ENABLED = 1 ;
	static final int DIS_MODE_DISABLED = 3;
	static final int REQUEST_DISCOVERABLE = 2;

	static final String DEVICE_MAC = "DEVICE MAC ADDRESS";
	static final String TAG = "Prefs";	 
	boolean isAutomatic;


	/**
	 * Set everything up.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState){
		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}
		super.onCreate(savedInstanceState);    
		addPreferencesFromResource(R.xml.prefs);

		setResult(RESULT_CANCELED);  
		// Get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE); 


		ConnectivityManager connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);

		prefListener = new OnSharedPreferenceChangeListener() {

			public void onSharedPreferenceChanged(SharedPreferences preferences,
					String key) {

				if (key.equals("prefDisasterMode") &&  prefs.getBoolean("prefDisasterMode", false) == true ) {

					enableBluetooth();   
					Log.i(TAG,"enabling disaster mode!");

				}

				else if (key.equals("prefDisasterMode") &&  prefs.getBoolean("prefDisasterMode", false) == false) {        	
					if (!isAutomatic) {
						setResult(DIS_MODE_DISABLED);

					} else
						isAutomatic = false;        
					finish();
					Log.i(TAG,"disaster mode disabled");

				}

			}
		};

		prefs.registerOnSharedPreferenceChangeListener(prefListener);	    
	}

	/**
	 * Enables Bluetooth when Disaster Mode get's enabled.
	 */
	private void enableBluetooth() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			finish();
		} else {		  

			if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {		
				Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
				discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
				startActivityForResult(discoverableIntent,REQUEST_DISCOVERABLE);           

			} 
			else {
				Intent intent = new Intent();
				intent.putExtra(DEVICE_MAC, mBluetoothAdapter.getAddress());
				setResult(BLUE_ENABLED,intent);
				finish();
			}	 
		}
	}


	/**
	 * Clean up.
	 */
	@Override
	protected void onDestroy() {
		prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
		super.onDestroy();
	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case REQUEST_DISCOVERABLE:
			if (resultCode != Activity.RESULT_CANCELED) {
				mBluetoothAdapter.setName(mSettings.getString("user", "not found")); 			
				setResult(BLUE_ENABLED);
				finish();
			} else {
				isAutomatic = true;
				Log.i(TAG,"discoverability non enabled"); 
				CheckBoxPreference disasterPreference = (CheckBoxPreference) getPreferenceScreen().findPreference("prefDisasterMode");
				disasterPreference.setChecked(false);	  

			}
		}

	}  

	/**
	 * Get's the username of the current user from twitter.
	 * TODO: This should be done (and saved to shared preferences) at the authorization. 
	 * @author pcarta
	 *
	 */
	class GetAuthenticatingUsername extends AsyncTask<Void, Void, String> {	
		
		/**
		 * Main function of the thread.
		 */
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
								userName = status.getUser().getScreenName();			   
								Long id = status.getId().longValue();
								ConnectionHelper.twitter.destroyStatus(id);
								return userName;
							}
							else return "";
						}
						else {
							userName = status.getUser().getScreenName();
							return userName;
						}				   			  
					} 
					else
						return "";
				}  
				else
					return "";

			}
			catch (Exception ex){
				Log.i(TAG,"getting username exception");
				return "";
			}			 
		}		

		/**
		 * After obtaining the username, store it.
		 */
		@Override
		protected void onPostExecute(String result) {
			if (!result.equals("")) {
				SharedPreferences.Editor editor = mSettings.edit();
				editor.remove("user");
				editor.putString("user", result);
				editor.commit();
			}		
		}
	}

}
