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

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import ch.ethz.twimight.fragments.UserListFragment;
import ch.ethz.twimight.listeners.TabListener;

/**
 * Display a list of users (friends, followers, etc.)
 * @author thossmann
 *
 */
public class ShowUserListActivity extends TwimightBaseActivity{

	private static final String TAG = "ShowUsersActivity";	

	private int positionIndex;
	private int positionTop;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		
		Intent intent = getIntent();
		//action bar
		actionBar = getActionBar();	
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		Tab tab = actionBar.newTab()
				.setText("Friends")
				.setTabListener(new TabListener<UserListFragment>(
						this, "Friends", UserListFragment.class));
		actionBar.addTab(tab);
		
		tab = actionBar.newTab()
				.setText("Followers")
				.setTabListener(new TabListener<UserListFragment>(
						this, "Followers", UserListFragment.class));
		actionBar.addTab(tab);
		
		tab = actionBar.newTab()
				.setText("Peers")
				.setTabListener(new TabListener<UserListFragment>(
						this, "Peers",UserListFragment.class));
		actionBar.addTab(tab);	
		
	    actionBar.setSelectedNavigationItem(intent.getIntExtra("filter", UserListFragment.SHOW_FRIENDS));
		


	}
	
	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
				
		// if we just got logged in, we load the timeline
		Intent i = getIntent();
		
		
		if(positionIndex != 0 | positionTop !=0){
			//userListView.setSelectionFromTop(positionIndex, positionTop);
		}
	}
	
	
	
	/**
	 * Saves the current selection
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

	 
	 // positionIndex = userListView.getFirstVisiblePosition();
	 // View v = userListView.getChildAt(0);
	//  positionTop = (v == null) ? 0 : v.getTop();
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
	 
	}
	
	
	
}
