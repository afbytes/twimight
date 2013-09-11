package ch.ethz.twimight.fragments;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.opportunistic.ScanningAlarm;
import ch.ethz.twimight.net.opportunistic.ScanningService;
import ch.ethz.twimight.net.twitter.TwitterAlarm;
import ch.ethz.twimight.util.Constants;

public class SettingsFragment extends PreferenceFragment {

	protected static final String TAG = "PreferenceActivity";

	private OnSharedPreferenceChangeListener prefListener;
	private SharedPreferences prefs;
	BluetoothAdapter mBluetoothAdapter;
	static final int REQUEST_DISCOVERABLE = 2;

	/**
	 * Set everything up.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.prefs);

		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

		prefListener = new OnSharedPreferenceChangeListener() {

			// this is where we take action after the user changes a setting!
			public void onSharedPreferenceChanged(
					SharedPreferences preferences, String key) {

				if (key.equals(getString(R.string.prefDisasterMode))) { // toggle
																		// disaster
																		// mode
					if (preferences.getBoolean(
							getString(R.string.prefDisasterMode),
							Constants.DISASTER_DEFAULT_ON) == true) {

						if (LoginActivity.getTwitterId(getActivity()
								.getBaseContext()) != null
								&& LoginActivity
										.getTwitterScreenname(getActivity()
												.getBaseContext()) != null) {
							enableDisasterMode();
							// Are we in disaster mode?

						}

					} else {
						disableDisasterMode(getActivity()
								.getApplicationContext());
						getActivity().finish();
					}

				} else if (key.equals(getString(R.string.prefTDSCommunication))) {

					// toggle TDS communication
					if (preferences.getBoolean(
							getString(R.string.prefTDSCommunication),
							Constants.TDS_DEFAULT_ON) == true) {
						// new TDSAlarm(getApplicationContext(),
						// Constants.TDS_UPDATE_INTERVAL);
						Log.i(TAG, "start TDS communication");
					} else {
						// stopService(new Intent(getApplicationContext(),
						// TDSService.class));
						// TDSAlarm.stopTDSCommuniction(getApplicationContext());
					}

				} else if (key.equals(getString(R.string.prefRunAtBoot))) {

					if (preferences.getBoolean(
							getString(R.string.prefRunAtBoot),
							Constants.TWEET_DEFAULT_RUN_AT_BOOT) == true) {
						ListPreference updatesBackground = (ListPreference) getPreferenceScreen()
								.findPreference(
										getString(R.string.prefUpdateInterval));
						updatesBackground.setEnabled(true);
						new TwitterAlarm(getActivity().getBaseContext(), false);

					} else {
						ListPreference updatesBackground = (ListPreference) getPreferenceScreen()
								.findPreference(
										getString(R.string.prefUpdateInterval));
						updatesBackground.setEnabled(false);
						TwitterAlarm.stopTwitterAlarm(getActivity()
								.getBaseContext());
					}
				} else if (key.equals(getString(R.string.prefUpdateInterval))) {
					Constants.UPDATER_UPDATE_PERIOD = Long
							.parseLong(preferences
									.getString(
											getString(R.string.prefUpdateInterval),
											"5")) * 60 * 1000L;
					Log.i(TAG, "new update interval: "
							+ Constants.UPDATER_UPDATE_PERIOD);

					// start the twitter update alarm
					if (PreferenceManager.getDefaultSharedPreferences(
							getActivity().getBaseContext()).getBoolean(
							getString(R.string.prefRunAtBoot),
							Constants.TWEET_DEFAULT_RUN_AT_BOOT) == true) {
						new TwitterAlarm(getActivity().getBaseContext(), false);
					}
				} else if (key.equals(getString(R.string.pref_offline_mode))) {

					if (preferences.getBoolean(
							getString(R.string.pref_offline_mode),
							Constants.OFFLINE_DEFAULT_ON)) {

						setOfflinePreference(true, getActivity());
					} else {

						setOfflinePreference(false, getActivity());
					}

				}
			}

		};

	}

	/**
	 * Enables Bluetooth when Disaster Mode get's enabled.
	 */
	private void enableDisasterMode() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled())
			ScanningAlarm.setBluetoothInitialState(getActivity()
					.getBaseContext(), true);
		else
			ScanningAlarm.setBluetoothInitialState(getActivity()
					.getBaseContext(), false);
		// for statistics
		setDisasterModeUsed();

		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
			startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);

		} else {
			new ScanningAlarm(getActivity().getApplicationContext(), true);
			getActivity().finish();
		}

	}

	private void setDisasterModeUsed() {
		SharedPreferences.Editor edit = prefs.edit();
		edit.putBoolean(Constants.DIS_MODE_USED, true);
		edit.commit();
	}

	public static void disableDisasterMode(Context context) {
		if (getBluetoothInitialState(context) == false) {
			if (BluetoothAdapter.getDefaultAdapter().isEnabled())
				BluetoothAdapter.getDefaultAdapter().disable();
		}
		ScanningAlarm.stopScanning(context);
		Intent in = new Intent(context, ScanningService.class);
		context.stopService(in);

	}

	private static boolean getBluetoothInitialState(Context context) {

		SharedPreferences pref = PreferenceManager
				.getDefaultSharedPreferences(context);
		return pref.getBoolean("wasBlueEnabled", true);

	}

	// set offline mode preference
	public static void setOfflinePreference(boolean pref, Context context) {

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putBoolean(HtmlPage.OFFLINE_PREFERENCE, pref);
		prefEditor.commit();
	}

	/**
	 * Important: register shared preference change listener here!
	 */
	@Override
	public void onResume() {
		super.onResume();
		prefs.registerOnSharedPreferenceChangeListener(prefListener);
	}

	/**
	 * Important: unregister shared preferece chnage listener here!
	 */
	@Override
	public void onPause() {
		super.onPause();
		prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
	}

	public static boolean isDisModeActive(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context)
				.getBoolean("prefDisasterMode", false);
	}
	
	
	
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode) {
		case REQUEST_DISCOVERABLE:		
			
			if (resultCode == BluetoothAdapter.STATE_CONNECTING) {
				new ScanningAlarm(getActivity().getApplicationContext(),true);
				getActivity().finish();
			} else if (resultCode == BluetoothAdapter.STATE_DISCONNECTED) {
				SharedPreferences.Editor edit = prefs.edit();
				edit.putBoolean(getString(R.string.prefDisasterMode), Constants.DISASTER_DEFAULT_ON);
				edit.commit();	
				CheckBoxPreference disPref = (CheckBoxPreference)findPreference(getString(R.string.prefDisasterMode));
				disPref.setChecked(false);
			}
			
			
		}
	}
}
