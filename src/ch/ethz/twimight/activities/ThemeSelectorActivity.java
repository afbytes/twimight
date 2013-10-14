package ch.ethz.twimight.activities;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;

public abstract class ThemeSelectorActivity extends Activity {

	private boolean isDisasterThemeSet = false;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		updateTheme();
		super.onCreate(savedInstanceState);
	}

	@Override
	public void onResume() {
		checkTheme();
		super.onResume();
	}

	/**
	 * Checks if the correct theme is currently set and if necessary restarts
	 * the activity so that a different them can be applied.
	 */
	private void checkTheme() {
		if (isDisasterModeEnabled() != isDisasterThemeSet) {
			recreate();
		}
	}

	private void updateTheme() {
		if (isDisasterModeEnabled()) {
			setDisasterTheme();
			isDisasterThemeSet = true;
		} else {
			setNormalTheme();
			isDisasterThemeSet = false;
		}
	}

	private boolean isDisasterModeEnabled() {
		return PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
				"prefDisasterMode", false);
	}

	protected abstract void setNormalTheme();

	protected abstract void setDisasterTheme();
}
