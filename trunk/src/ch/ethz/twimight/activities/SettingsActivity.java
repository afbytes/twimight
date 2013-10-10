package ch.ethz.twimight.activities;

import android.os.Bundle;
import android.view.MenuItem;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.SettingsFragment;

public class SettingsActivity extends ThemeSelectorActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Display the fragment as the main content.
		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, new SettingsFragment()).commit();
	}

	@Override
	public void onResume() {
		super.onResume();
		String title = getString(R.string.settings);
		getActionBar().setTitle(title);
	}

	@Override
	protected void setDisasterTheme() {
		setTheme(R.style.TwimightHolo_DisasterMode);
	}

	@Override
	protected void setNormalTheme() {
		setTheme(R.style.TwimightHolo_NormalMode);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// app icon in action bar clicked; return
			finish();
			return true;
		default:
			return false;
		}
	}
}
