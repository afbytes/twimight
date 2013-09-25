package ch.ethz.twimight.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import ch.ethz.twimight.R;

public class TipsActivity extends Activity {

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.tips);	
	}
	
	/**
	 * Starts the login activity. This method is called from the agree button's onClick
	 * listener set in the layout.
	 * 
	 * @param unused obligatory View argument for onClick callback methods
	 */
	public void advanceToLogin(View unused) {
		Intent intent = new Intent(this, LoginActivity.class);
		startActivity(intent);
		finish();
	}
}
