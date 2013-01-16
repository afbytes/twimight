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

import java.util.HashMap;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.UserListFragment;
import ch.ethz.twimight.fragments.adapters.PageAdapter;
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
	ViewPager viewPager;
	
	public static final int FRIENDS_KEY = 0;	
	public static final int FOLLOWERS_KEY = 1;
	public static final int PEERS_KEY = 2;
	
	public static final String USER_FILTER_REQUEST = "user_filter_request";

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.main);
		
		Intent intent = getIntent();
		//action bar
		actionBar = getActionBar();	
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		HashMap<Integer,UserListFragment> fragmentMap = createUserFragments();
		PageAdapter pagAdapter = new PageAdapter(getFragmentManager(),fragmentMap);
		
		LayoutInflater inflater =  (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		viewPager = (ViewPager)  findViewById(R.id.viewpager);	
		
		viewPager.setAdapter(pagAdapter);
		viewPager.setOnPageChangeListener(
	            new ViewPager.SimpleOnPageChangeListener() {
	                @Override
	                public void onPageSelected(int position) {
	                    // When swiping between pages, select the
	                    // corresponding tab.
	                    getActionBar().setSelectedNavigationItem(position);
	                }
	            });
		
		
		Tab tab = actionBar.newTab()
				.setText("Friends")
				.setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);
		
		tab = actionBar.newTab()
				.setText("Followers")
				.setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);
		
		tab = actionBar.newTab()
				.setText("Peers")
				.setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);
		
	    //actionBar.setSelectedNavigationItem(intent.getIntExtra(USER_FILTER_REQUEST, FRIENDS_KEY));
		viewPager.setCurrentItem(intent.getIntExtra(USER_FILTER_REQUEST, FRIENDS_KEY));


	}
	
private HashMap<Integer,UserListFragment> createUserFragments() {
		
		HashMap<Integer,UserListFragment> map = new HashMap<Integer,UserListFragment>();
		map.put(FRIENDS_KEY, new UserListFragment(this,FRIENDS_KEY));
		map.put(FOLLOWERS_KEY, new UserListFragment(this,FOLLOWERS_KEY));
		map.put(PEERS_KEY, new UserListFragment(this,PEERS_KEY));
		
		return map;
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
