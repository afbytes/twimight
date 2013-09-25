package ch.ethz.twimight.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import ch.ethz.twimight.R;

public class ConditionsActivity extends Activity {

	static final String TERMS = "termsAccepted";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences settings = PreferenceManager
				.getDefaultSharedPreferences(this);
		boolean termsAccepted = settings.getBoolean(TERMS, false);

		if (termsAccepted) {
			advanceToLogin();
		} else {
			setContentView(R.layout.conditions);
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
