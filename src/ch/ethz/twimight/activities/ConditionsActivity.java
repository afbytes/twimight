package ch.ethz.twimight.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import ch.ethz.twimight.R;

public class ConditionsActivity extends Activity {

	static final String TERMS = "termsAccepted";
	private static final String PREF_VERSION_CODE = "versionCode";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		clearPrefsOnUpdate();
		
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		
		// check if terms accepted
		boolean termsAccepted = settings.getBoolean(TERMS, false);
		if (termsAccepted) {
			advanceToLogin();
		} else {
			setContentView(R.layout.conditions);
		}
	}

	/**
	 * Clears the preferences when an update is detected.
	 */
	private void clearPrefsOnUpdate() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		// delete preferences if old version
		int currentVersionCode;
		// preferences are deleted when previous installed version is less than this (usually the version with the last db update because we need to):
		int minVersionCode = 1700026;
		try {
			int savedVersionCode = prefs.getInt(PREF_VERSION_CODE, 0);
			if (savedVersionCode<minVersionCode) {
				prefs.edit().clear().commit();
			}
			currentVersionCode = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionCode;
			prefs.edit().putInt(PREF_VERSION_CODE, currentVersionCode).commit();
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Saves the agreement to the terms in the preferences and proceeds to the
	 * tips activity. This method is called from the agree button's onClick
	 * listener set in the layout.
	 * 
	 * @param unused obligatory View argument for onClick callback methods
	 */
	public void agreeToTerms(View unused) {
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(ConditionsActivity.this);
		SharedPreferences.Editor editor = settings.edit();
		editor.putBoolean(TERMS, true);
		editor.commit();
		advanceToTips();
	}

	private void advanceToTips() {
		Intent intent = new Intent(this, TipsActivity.class);
		startActivity(intent);
		finish();
	}

	private void advanceToLogin() {
		Intent intent = new Intent(this, LoginActivity.class);
		startActivity(intent);
		finish();
	}

}
