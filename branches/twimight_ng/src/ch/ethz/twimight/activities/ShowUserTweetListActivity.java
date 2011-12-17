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
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * Shows the most recent tweets of a user
 * @author thossmann
 *
 */
public class ShowUserTweetListActivity extends Activity{

	private static final String TAG = "ShowUserTweetListActivity";
	
	// Views
	private ListView userTweetListView;

	private TweetAdapter adapter;
	private Cursor c;

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
		
		if(!getIntent().hasExtra("userId")) finish();
		
		
		setContentView(R.layout.showusertweets);
		
		userTweetListView = (ListView) findViewById(R.id.userTweetList);
		userTweetListView.setEmptyView(findViewById(R.id.userTweetListEmpty));
		
		
		long userId = getIntent().getLongExtra("userId", 0);
		c = getContentResolver().query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS +"/" + Tweets.TWEETS_TABLE_USER + "/" + userId), null, null, null, null);

		adapter = new TweetAdapter(this, c);		
		userTweetListView.setAdapter(adapter);


		// Click listener when the user clicks on a tweet
		userTweetListView.setClickable(true);
		userTweetListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Cursor c = (Cursor) userTweetListView.getItemAtPosition(position);
				//c.moveToFirst();
				Intent i = new Intent(getBaseContext(), ShowTweetActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				startActivity(i);
				//c.close();
			}
		});
		
		Log.i(TAG, "created");

	}
	
	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
		
		
		if(positionIndex != 0 | positionTop !=0){
			userTweetListView.setSelectionFromTop(positionIndex, positionTop);
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
		
		userTweetListView.setOnItemClickListener(null);
		userTweetListView.setAdapter(null);

		if(c!=null) c.close();
				
		unbindDrawables(findViewById(R.id.showUserTweetListRoot));

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

	  positionIndex = userTweetListView.getFirstVisiblePosition();
	  View v = userTweetListView.getChildAt(0);
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
