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
import ch.ethz.twimight.net.twitter.DMAdapter;
import ch.ethz.twimight.net.twitter.DirectMessages;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;

/**
 * Shows the overview of direct messages. A list view with an item for each user with which
 * we have exchanged direct messages.
 * @author thossmann
 *
 */
public class ShowDMListActivity extends TwimightBaseActivity{

	private static final String TAG = "ShowDMListActivity";
	
	// Views
	private ListView dmUserListView;
	private ImageButton messageButton;

	private DMAdapter adapter;
	private Cursor c;
	
	private int rowId;
	private String screenname;
	public static boolean running= false;

	// handler
	static Handler handler;


	private int positionIndex;
	private int positionTop;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		running = true;		
		setContentView(R.layout.show_dm_user);
		
		rowId = getIntent().getIntExtra("rowId", 0);
		screenname = getIntent().getStringExtra("screenname");
		
		// If we don't know which user to show, we stop the activity
		if(rowId == 0 || screenname == null) finish();

		setTitle("Direct Messages - @" + screenname);
		
		dmUserListView = (ListView) findViewById(R.id.dmUserList);
		c = getContentResolver().query(Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/" + DirectMessages.DMS_USER + "/" + rowId), null, null, null, null);
				
		adapter = new DMAdapter(this, c);		
		dmUserListView.setAdapter(adapter);
		dmUserListView.setEmptyView(findViewById(R.id.dmListEmpty));
		// Click listener when the user clicks on a user
		
		messageButton = (ImageButton) findViewById(R.id.headerBarMessageButtonDMUser);
		messageButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(), NewDMActivity.class);
				i.putExtra("recipient", screenname);
				startActivity(i);
			}
		});
		
		
	}
	
	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
		running = true;
		// Are we in disaster mode?
		LinearLayout headerBar = (LinearLayout) findViewById(R.id.headerBarDMUser);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			headerBar.setBackgroundResource(R.drawable.top_bar_background_disaster);
		} else {
			headerBar.setBackgroundResource(R.drawable.top_bar_background);
		}
		
		if(positionIndex != 0 | positionTop !=0){
			dmUserListView.setSelectionFromTop(positionIndex, positionTop);
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
		running=false;
		messageButton.setOnClickListener(null);

		dmUserListView.setAdapter(null);

		if(c!=null) c.close();
				
		unbindDrawables(findViewById(R.id.showDMUserListRoot));

	}

	
	/**
	 * Saves the current selection
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

	  positionIndex = dmUserListView.getFirstVisiblePosition();
	  View v = dmUserListView.getChildAt(0);
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
}
