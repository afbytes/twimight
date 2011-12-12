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

import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.DMUserAdapter;
import ch.ethz.twimight.net.twitter.DirectMessages;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * Shows the overview of direct messages. A list view with an item for each user with which
 * we have exchanged direct messages.
 * @author thossmann
 *
 */
public class ShowDMUsersListActivity extends Activity{

	private static final String TAG = "ShowDMUsersListActivity";
	
	private static ShowDMUsersListActivity instance;

	// Views
	private ListView dmUsersListView;
	private ImageButton messageButton;

	private DMUserAdapter adapter;
	private Cursor c;

	// handler
	static Handler handler;

	// the menu
	private static final int OPTIONS_MENU_HOME = 10;

	private int positionIndex;
	private int positionTop;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
				
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.show_dm_users);
		
		dmUsersListView = (ListView) findViewById(R.id.dmUsersList);
		c = getContentResolver().query(Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/" + DirectMessages.DMS_USERS), null, null, null, null);
		adapter = new DMUserAdapter(this, c);		
		dmUsersListView.setAdapter(adapter);
		// Click listener when the user clicks on a user
		dmUsersListView.setClickable(true);
		dmUsersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				// TODO
				/*
				Cursor c = (Cursor) dmUsersListView.getItemAtPosition(position);
				//c.moveToFirst();
				Intent i = new Intent(getBaseContext(), ShowTweetActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				startActivity(i);
				c.close();
				*/
			}
		});
		
		messageButton = (ImageButton) findViewById(R.id.headerBarMessageButton);
		messageButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				// TODO
				//startActivity(new Intent(getBaseContext(), NewTweetActivity.class));
			}
		});
		
		setInstance(this);
		
		Log.i(TAG, "created");

	}
	
	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
		
		// Are we in disaster mode?
		LinearLayout headerBar = (LinearLayout) findViewById(R.id.headerBar);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			headerBar.setBackgroundResource(R.drawable.top_bar_background_disaster);
		} else {
			headerBar.setBackgroundResource(R.drawable.top_bar_background);
		}
		
		if(positionIndex != 0 | positionTop !=0){
			dmUsersListView.setSelectionFromTop(positionIndex, positionTop);
		}
	}
	
	/**
	 * On pause
	 */
	@Override
	public void onPause(){
		super.onPause();
				
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		
		messageButton.setOnClickListener(null);

		dmUsersListView.setOnItemClickListener(null);
		dmUsersListView.setAdapter(null);

		if(c!=null) c.close();
				
		unbindDrawables(findViewById(R.id.showDMUsersListRoot));
		setInstance(null);

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
	 * Saves the current selection
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

	  positionIndex = dmUsersListView.getFirstVisiblePosition();
	  View v = dmUsersListView.getChildAt(0);
	  positionTop = (v == null) ? 0 : v.getTop();
	  savedInstanceState.putInt("positionIndex", positionIndex);
	  savedInstanceState.putInt("positionTop", positionTop);
	  
	  Log.i(TAG, "saving" + positionIndex + " " + positionTop);
	  
	  super.onSaveInstanceState(savedInstanceState);
	}
	
	/**
	 * Loads the current user selection
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
	  super.onRestoreInstanceState(savedInstanceState);
	  
	  positionIndex = savedInstanceState.getInt("positionIndex");
	  positionTop = savedInstanceState.getInt("positionTop");
	  
	  Log.i(TAG, "restoring " + positionIndex + " " + positionTop);
	}
	
	/**
	 * @param instance the instance to set
	 */
	public static void setInstance(ShowDMUsersListActivity instance) {
		ShowDMUsersListActivity.instance = instance;
	}

	/**
	 * @return the instance
	 */
	public static ShowDMUsersListActivity getInstance() {
		return instance;
	}
	
	/**
	 * Turns the loading icon on and off
	 * @param isLoading
	 */
	public static void setLoading(final boolean isLoading) {
		if(getInstance()!=null){
			getInstance().runOnUiThread(new Runnable() {
			     public void run() {
			    	 getInstance().setProgressBarIndeterminateVisibility(isLoading);
			     }
			});
		}

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
