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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * The base activity for all Twimight activities.
 * @author thossmann
 *
 */
public class TwimightBaseActivity extends Activity{
	
	static TwimightBaseActivity instance;
	
	// the menu
	private static final int OPTIONS_MENU_HOME = 10;


	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
	}
	
	/**
	 * on Pause
	 */
	@Override
	public void onPause(){
		
		instance = null;
		
		super.onPause();

	}
	
	/**
	 * on Resume
	 */
	@Override
	public void onResume(){
		
		instance = this;
		
		super.onResume();

	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();

	}
	
	/**
	 * Populate the Options menu with the "home" option. 
	 * For the "main" activity ShowTweetListActivity we don't add the home option.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		if(!(instance instanceof ShowTweetListActivity))
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
	 * Turns the loading icon on and off
	 * @param isLoading
	 */
	public static void setLoading(final boolean isLoading) {
		if(instance!=null){
			instance.runOnUiThread(new Runnable() {
			     public void run() {
			    	 instance.setProgressBarIndeterminateVisibility(isLoading);
			     }
			});
		} else {
			Log.v("TwimightBaseActivity", "Cannot show loading icon");
		}

	}
	
	/**
	 * Clean up the views
	 * @param view
	 */
	protected void unbindDrawables(View view) {
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
