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
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.net.twitter.UserAdapter;
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
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

/**
 * Display a list of users (friends, followers, etc.)
 * @author thossmann
 *
 */
public class ShowUserListActivity extends Activity{

	private static final String TAG = "ShowUsersActivity";
	
	// Views
	private ListView userListView;
	private Button friendsButton;
	private Button followersButton;
	
	// the menu
	private static final int OPTIONS_MENU_HOME = 10;

	
	
	static final int SHOW_FRIENDS = 1;
	static final int SHOW_FOLLOWERS = 2;

	Cursor c;
	private UserAdapter adapter;
	
	private int currentFilter = SHOW_FRIENDS;
	private int positionIndex;
	private int positionTop;
	
	private static ShowUserListActivity instance;

	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.showusers);
		

		userListView = (ListView) findViewById(R.id.userList);
		friendsButton = (Button) findViewById(R.id.headerBarFriendsButton);		
		friendsButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setFilter(SHOW_FRIENDS);
			}
		});
		
		followersButton = (Button) findViewById(R.id.headerBarFollowersButton);
		followersButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setFilter(SHOW_FOLLOWERS);
			}
		});

		setInstance(this);
	}
	
	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
		
		Log.i(TAG, "resuming");
		
		// if we just got logged in, we load the timeline
		Intent i = getIntent();
		if(i.hasExtra("filter")){
			Log.i(TAG, "got filter from intent");
			currentFilter = i.getIntExtra("filter", SHOW_FRIENDS);
			i.removeExtra("filter");	
		}

		setFilter(currentFilter);
		
		if(positionIndex != 0 | positionTop !=0){
			userListView.setSelectionFromTop(positionIndex, positionTop);
		}
	}
	
	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		
		friendsButton.setOnClickListener(null);
		followersButton.setOnClickListener(null);
		
		userListView.setOnItemClickListener(null);
		
		if(c!=null) c.close();
		
		unbindDrawables(findViewById(R.id.showUsersRoot));
		
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

	  savedInstanceState.putInt("currentFilter", currentFilter);
	  positionIndex = userListView.getFirstVisiblePosition();
	  View v = userListView.getChildAt(0);
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
	  
	  currentFilter = savedInstanceState.getInt("currentFilter");
	  positionIndex = savedInstanceState.getInt("positionIndex");
	  positionTop = savedInstanceState.getInt("positionTop");
	  
	  Log.i(TAG, "restoring " + positionIndex + " " + positionTop);
	}
	
	/**
	 * Which users do we show? Friends, Followers?
	 * @param filter
	 */
	private void setFilter(int filter){
		// set all colors to transparent
		resetButtons();
		Button b=null;
		
		if(c!=null) c.close();
		
		switch(filter) {
			
		case SHOW_FRIENDS: 
			b = friendsButton;
			c = getContentResolver().query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + TwitterUsers.TWITTERUSERS_FRIENDS), null, null, null, null);
			currentFilter=SHOW_FRIENDS;

			break;
		case SHOW_FOLLOWERS: 
			b = followersButton;
			c = getContentResolver().query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + TwitterUsers.TWITTERUSERS_FOLLOWERS), null, null, null, null);
			currentFilter=SHOW_FOLLOWERS;

			break;
		default:
			break;
		}
		
		// style button
		if(b!=null){
			b.setEnabled(false);
		}

		adapter = new UserAdapter(this, c);		
		userListView.setAdapter(adapter);

		// Click listener when the user clicks on a user
		userListView.setClickable(true);
		userListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Cursor c = (Cursor) userListView.getItemAtPosition(position);
				//c.moveToFirst();
				Intent i = new Intent(getBaseContext(), ShowUserActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				startActivity(i);
			}
		});
	}
	
	/**
	 * Enables all header buttons
	 */
	private void resetButtons(){
		friendsButton.setEnabled(true);
		followersButton.setEnabled(true);
	}
	
	/**
	 * @param instance the instance to set
	 */
	public static void setInstance(ShowUserListActivity instance) {
		ShowUserListActivity.instance = instance;
	}

	/**
	 * @return the instance
	 */
	public static ShowUserListActivity getInstance() {
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
