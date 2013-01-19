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
import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.ListFragment;
import ch.ethz.twimight.fragments.TweetListFragment;
import ch.ethz.twimight.fragments.UserListFragment;
import ch.ethz.twimight.fragments.adapters.PageAdapter;
import ch.ethz.twimight.listeners.TabListener;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUserAdapter;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.TwimightSuggestionProvider;

/**
 * Shows the most recent tweets of a user
 * @author thossmann
 * @author pcarta
 */
public class SearchableActivity extends TwimightBaseActivity{

	private static final String TAG = "SearchableActivity";

	
		
	ViewPager viewPager;
	
	public static String query;
	
	public static final int SHOW_SEARCH_TWEETS = 13;
	public static final int SHOW_SEARCH_USERS = 14;
	
	/** 
	 * Called when the activity is first created. 	
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);		

		//action bar
		actionBar = getActionBar();	
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);		
		
		PageAdapter pagAdapter = new PageAdapter(this, getFragmentManager(),null);		
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
				.setText("Tweets")
				.setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);

		tab = actionBar.newTab()
				.setText("Users")
				.setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);
		//viewPager.setCurrentItem(PageAdapter.POS_ZERO);
		// Get the intent and get the query
		Intent intent = getIntent();		
		processIntent(intent);


	}




	private void processIntent(Intent intent) {
		if (intent.hasExtra(SearchManager.QUERY)) {
			//if (!intent.getStringExtra(SearchManager.QUERY).equals(query))
			query = intent.getStringExtra(SearchManager.QUERY);	
			setTitle("Search Results for: " + query);
			
			SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
	                TwimightSuggestionProvider.AUTHORITY, TwimightSuggestionProvider.MODE);
	        suggestions.saveRecentQuery(query, null); 
	        	        
		
		
		} 
	}



	@Override
	protected void onNewIntent(Intent intent) {
		Log.i(TAG,"on new intent");
		setIntent(intent);
		processIntent(intent);		
	}





	/**
	 * Saves the current selection
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

		if(searchListView != null){
			positionIndex = searchListView.getFirstVisiblePosition();
			View v = searchListView.getChildAt(0);
			positionTop = (v == null) ? 0 : v.getTop();
			savedInstanceState.putInt("positionIndex", positionIndex);
			savedInstanceState.putInt("positionTop", positionTop);

			Log.i(TAG, "saving" + positionIndex + " " + positionTop);
		}

		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Loads the current user selection
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		positionIndex = savedInstanceState.getInt("positionIndex");
		positionTop = savedInstanceState.getInt("positionTop");

		Log.i(TAG, "restoring " + positionIndex + " " + positionTop);
	}
	
	
	 */
	
	

}
