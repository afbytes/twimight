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

import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.util.TwimightSuggestionProvider;

/**
 * Shows the most recent tweets of a user
 * @author thossmann
 *
 */
public class SearchableActivity extends TwimightBaseActivity{

	private static final String TAG = "SearchableActivity";

	// Views
	private ListView searchListView;

	private ListAdapter adapter;
	private Cursor c;


	private int positionIndex;
	private int positionTop;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.showsearch);

		setTitle("Search Results");
		
		searchListView = (ListView) findViewById(R.id.searchList);
		searchListView.setEmptyView(findViewById(R.id.searchListEmpty));

		// Get the intent and get the query
		Intent intent = getIntent();
		if (intent.hasExtra(SearchManager.QUERY)) {
			String query = intent.getStringExtra(SearchManager.QUERY);
			
			//@author pcarta
			SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
	                TwimightSuggestionProvider.AUTHORITY, TwimightSuggestionProvider.MODE);
	        suggestions.saveRecentQuery(query, null);

			Log.e(TAG, query);

			// TODO : check input
			Uri uri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+ "/"+Tweets.SEARCH);
			c = getContentResolver().query(uri, null, query, null, null);
			startManagingCursor(c); //@author pcarta

			adapter = new TweetAdapter(this, c);		
			searchListView.setAdapter(adapter);


			// Click listener when the user clicks on a tweet
			searchListView.setClickable(true);
			searchListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
					Cursor c = (Cursor) searchListView.getItemAtPosition(position);
					//c.moveToFirst();
					Intent i = new Intent(getBaseContext(), ShowTweetActivity.class);
					i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
					startActivity(i);
					//c.close();
				}
			});
		}

		Log.v(TAG, "created");

	}

	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();


		if(positionIndex != 0 | positionTop !=0){
			if(searchListView!=null) searchListView.setSelectionFromTop(positionIndex, positionTop);
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

		if(searchListView != null) {
			searchListView.setOnItemClickListener(null);
			searchListView.setAdapter(null);
		}


		if(c!=null) c.close();

		unbindDrawables(findViewById(R.id.showSearchListRoot));

	}


	/**
	 * Saves the current selection
	 */
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
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		positionIndex = savedInstanceState.getInt("positionIndex");
		positionTop = savedInstanceState.getInt("positionTop");

		Log.i(TAG, "restoring " + positionIndex + " " + positionTop);
	}

}
