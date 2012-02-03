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
import ch.ethz.twimight.net.twitter.TwitterUserAdapter;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
public class ShowUserListActivity extends TwimightBaseActivity{

	private static final String TAG = "ShowUsersActivity";
	
	// Views
	private ListView userListView;
	private Button friendsButton;
	private Button followersButton;
	private Button disPeersButton;
		
	static final int SHOW_FRIENDS = 1;
	static final int SHOW_FOLLOWERS = 2;
	static final int SHOW_DISASTER_PEERS = 3;

	Cursor c;
	private TwitterUserAdapter adapter;
	
	private int currentFilter = SHOW_FRIENDS;
	private int positionIndex;
	private int positionTop;
	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.showusers);		

		userListView = (ListView) findViewById(R.id.userList);
		userListView.setEmptyView(findViewById(R.id.userListEmpty));
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
		
		disPeersButton = (Button) findViewById(R.id.headerBarDisasterPeersButton);
		disPeersButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				setFilter(SHOW_DISASTER_PEERS);
			}
		});

	}
	
	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
				
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
			c = getContentResolver().query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + 
														"/" + TwitterUsers.TWITTERUSERS_FRIENDS), null, null, null, null);
			currentFilter=SHOW_FRIENDS;

			break;
		case SHOW_FOLLOWERS: 
			b = followersButton;
			c = getContentResolver().query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS 
														+ "/" + TwitterUsers.TWITTERUSERS_FOLLOWERS), null, null, null, null);
			currentFilter=SHOW_FOLLOWERS;

			break;
		case SHOW_DISASTER_PEERS:
			b = disPeersButton;
			c = getContentResolver().query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS 
					+ "/" + TwitterUsers.TWITTERUSERS_DISASTER), null, null, null, null);
			currentFilter=SHOW_DISASTER_PEERS;
			break;
		default:
			break;
		}
		
		// style button
		if(b!=null){
			b.setEnabled(false);
		}

		adapter = new TwitterUserAdapter(this, c);		
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
				c.close();
			}
		});
	}
	
	/**
	 * Enables all header buttons
	 */
	private void resetButtons(){
		friendsButton.setEnabled(true);
		followersButton.setEnabled(true);
		disPeersButton.setEnabled(true);
	}
	
}
