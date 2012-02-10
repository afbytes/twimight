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
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * Shows the most recent tweets of a user
 * @author thossmann
 *
 */
public class ShowUserTweetListActivity extends TwimightBaseActivity{

	private static final String TAG = "ShowUserTweetListActivity";
	
	// Views
	private ListView userTweetListView;

	private TweetAdapter adapter;
	private Cursor c;
	
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
		new QueryUserTimelineTask().execute(userId);

		// Click listener when the user clicks on a tweet
		userTweetListView.setClickable(true);
		userTweetListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Cursor c = (Cursor) userTweetListView.getItemAtPosition(position);				
				Intent i = new Intent(getBaseContext(), ShowTweetActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
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
	 * Queries the content provider to obtain user timeline
	 * @author pcarta
	 */
	private class QueryUserTimelineTask extends AsyncTask<Long, Void, Void> {		

		@Override
		protected Void doInBackground(Long... userId) {	
			Log.i(TAG,"inside asynch task");
			c = getContentResolver().query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS +"/" + Tweets.TWEETS_TABLE_USER + "/" + userId[0]), null, null, null, null);
			return null;
			
		}
		
		@Override
		protected void onPostExecute(Void result) {
			adapter = new TweetAdapter(ShowUserTweetListActivity.this, c);		
			userTweetListView.setAdapter(adapter);
			
		}
	}
	
}
