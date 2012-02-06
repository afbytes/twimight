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
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import ch.ethz.twimight.R;
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

	// Views
	private ListView searchListView;
	private Button searchTweetsButton;
	private Button searchUsersButton;

	private ListAdapter adapter;
	private Cursor c;
	private int positionIndex;
	private int positionTop;
	
	String query;
	
	private static final int SHOW_TWEETS = 1;
	private static final int SHOW_USERS = 2;
	
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
			query = intent.getStringExtra(SearchManager.QUERY);			
			
			SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
	                TwimightSuggestionProvider.AUTHORITY, TwimightSuggestionProvider.MODE);
	        suggestions.saveRecentQuery(query, null);
	        
	        searchUsersButton = (Button) findViewById(R.id.headerBarUsersButton);
	        searchTweetsButton = (Button) findViewById(R.id.headerBarTweetsButton);		
	        setFilter(SHOW_TWEETS);	        
	        	        
			searchTweetsButton.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					setFilter(SHOW_TWEETS);
				}
			});
			
					
			searchUsersButton.setOnClickListener(new OnClickListener(){
				@Override
				public void onClick(View v) {
					setFilter(SHOW_USERS);
				}
			});
		
		} else 
			finish();

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
	
	
	/**
	 * WWhat do we wanna show, tweets or users?
	 * @param filter
	 */
	private void setFilter(int filter){
		// set all colors to transparent
		resetButtons();
		Button b=null;
		
		switch(filter) {
			
		case SHOW_TWEETS: 
			b = searchTweetsButton;
			new PerformSearchTweetsTask().execute(query);	
			//currentFilter=;

			break;
			
		case SHOW_USERS	: 
			b = searchUsersButton;
			new PerformSearchUsersTask().execute(query);	
			//currentFilter=;

			break;
		
		default:
			break;
		}
		
		// style button
		if(b!=null){
			b.setEnabled(false);
		} 	
	}
	
	/**
	 * Enables all header buttons
	 */
	private void resetButtons(){
		searchTweetsButton.setEnabled(true);
		searchUsersButton.setEnabled(true);
		
	}
	
	/**
	 * Perform the tweets search to Twitter
	 * @author pcarta
	 */
	private class PerformSearchTweetsTask extends AsyncTask<String, Void, Void> {			

		@Override
		protected void onPostExecute(Void result) {
			
			adapter = new TweetAdapter(SearchableActivity.this, c);		
			searchListView.setAdapter(adapter);	
			
			// Click listener when the user clicks on a tweet
			searchListView.setClickable(true);
			searchListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
					Cursor c = (Cursor) searchListView.getItemAtPosition(position);					
					Intent i = new Intent(getBaseContext(), ShowTweetActivity.class);
					i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
					startActivity(i);
					
				}
			});
			super.onPostExecute(result);
		}

		@Override
		protected Void doInBackground(String... query) {
			Log.i(TAG, "AsynchTask: PerformSearchTask");
			
			// TODO : check input
						Uri uri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+ "/"+Tweets.SEARCH);
						c = getContentResolver().query(uri, null, query[0], null, null);
						startManagingCursor(c); 
						return null;
			
		}
	}
	
	/**
	 * Perform the user search to Twitter
	 * @author pcarta
	 */
	private class PerformSearchUsersTask extends AsyncTask<String, Void, Void> {			

		@Override
		protected void onPostExecute(Void result) {
			
			adapter = new TwitterUserAdapter(SearchableActivity.this, c);		
			searchListView.setAdapter(adapter);	
			
			// Click listener when the user clicks on a tweet
			searchListView.setClickable(true);
			searchListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
					Cursor c = (Cursor) searchListView.getItemAtPosition(position);					
					Intent i = new Intent(getBaseContext(), ShowUserActivity.class);
					i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
					startActivity(i);
					
				}
			});
			super.onPostExecute(result);
		}

		@Override
		protected Void doInBackground(String... query) {
			Log.i(TAG, "AsynchTask: PerformSearchTask");
			
			// TODO : check input
						Uri uri = Uri.parse("content://"+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS 
								+ "/" + TwitterUsers.TWITTERUSERS_SEARCH);
						c = getContentResolver().query(uri, null, query[0], null, null);
						startManagingCursor(c); 
						return null;
			
		}
	}

}
