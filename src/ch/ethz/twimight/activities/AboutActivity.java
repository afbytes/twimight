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

import java.util.Date;

import ch.ethz.twimight.R;
import ch.ethz.twimight.net.tds.TDSService;
import ch.ethz.twimight.security.CertificateManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Show information about Twimight
 * @author thossmann
 *
 */
public class AboutActivity extends Activity{

	public static final String TAG = "AboutActivity";
	Button revokeButton;
	Button updateButton;
	TextView lastUpdate;
	TextView keyOk;
	TextView versionName;
	
	// the menu
	private static final int OPTIONS_MENU_HOME = 10;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.showabout);
		
		LinearLayout showAboutLogo = (LinearLayout) findViewById(R.id.showAboutLogo);
		showAboutLogo.setBackgroundResource(R.drawable.about_background);
		
		keyOk = (TextView) findViewById(R.id.showAboutKeys);
		revokeButton = (Button) findViewById(R.id.showAboutRevoke);
		CertificateManager cm = new CertificateManager(this);
		if(cm.hasCertificate()){
			keyOk.setText("OK!");
			revokeButton.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					showRevokeDialog();
				}
			});
		} else {
			revokeButton.setVisibility(Button.GONE);
			keyOk.setText("No valid certificate!");
		}
		
		updateButton = (Button) findViewById(R.id.showAboutUpdate);
		lastUpdate = (TextView) findViewById(R.id.showAboutLastUpdate);
		long lastTimestamp = TDSService.getLastUpdate(this);
		if(lastTimestamp == 0){
			lastUpdate.setText("no update");
		} else {
			Date date = new Date(lastTimestamp);
			
			lastUpdate.setText(DateFormat.format("MM/dd/yy h:mmaa", date));
		}
		updateButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Intent updateIntent = new Intent(getBaseContext(), TDSService.class);
				updateIntent.putExtra("synch_request", TDSService.SYNCH_ALL_FORCE);
				startService(updateIntent);
				Toast.makeText(getBaseContext(), "Starting TDS update.", Toast.LENGTH_LONG).show();
				finish();
			}
			
		});
		
		
		versionName = (TextView) findViewById(R.id.showAboutVersion);
		try
		{
		    String appVer = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
		    versionName.setText(appVer);
		}
		catch (NameNotFoundException e)
		{
		    Log.v(TAG, e.getMessage());
		}
	}
	
	/**
	 * on Resume
	 */
	@Override
	public void onResume(){
		super.onResume();

	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		
		revokeButton.setOnClickListener(null);
		updateButton.setOnClickListener(null);
		
		unbindDrawables(findViewById(R.id.showAboutRoot));


	}
	
	/**
	 * Populate the Options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		menu.add(1, OPTIONS_MENU_HOME, 1, "Home");
		return true;
	}

	/**
	 * Handle options menu selection
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item){

		Intent i;
		switch(item.getItemId()){
		
		case OPTIONS_MENU_HOME:
			// show the timeline
			i = new Intent(this, ShowTweetListActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
			break;
		default:
			return false;
		}
		return true;
	}
	
	/**
	 * Asks the user if she really wants to revoke the key
	 */
	private void showRevokeDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to revoke your key? You should only do that if the private key was compromised (e.g., your phone was stolen).")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
						Intent updateIntent = new Intent(getBaseContext(), TDSService.class);
						updateIntent.putExtra("synch_request", TDSService.SYNCH_REVOKE);
						startService(updateIntent);
						Toast.makeText(getBaseContext(), "Revoking key.", Toast.LENGTH_LONG).show();
						finish();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	/**
	 * Clean up the views
	 * @param view
	 */
	private void unbindDrawables(View view) {
	    if (view.getBackground() != null) {
	        view.getBackground().setCallback(null);
	    }
	    if (view instanceof ViewGroup) {
	        for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
	            unbindDrawables(((ViewGroup) view).getChildAt(i));
	        }
	        try{
	        	((ViewGroup) view).removeAllViews();
	        } catch(UnsupportedOperationException e){
	        	// No problem, nothing to do here
	        }
	    }
	}
}
