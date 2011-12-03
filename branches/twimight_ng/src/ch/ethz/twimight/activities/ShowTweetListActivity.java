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
import ch.ethz.twimight.net.tds.TDSService;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

/**
 * The main Twimight view showing the Timeline, favorites and mentions
 * TODO: Refresh and load older tweets on overscroll.
 * TODO: Show a message when the cursor is empty.
 * @author thossmann
 *
 */
public class ShowTweetListActivity extends Activity{

	private static final String TAG = "BluetestActivity";
	
	private static ShowTweetListActivity instance;

	// Views
	private ListView timelineListView;
	private Button timelineButton;
	private Button favoritesButton;
	private Button mentionsButton;
	private ImageButton tweetButton;
	private ImageButton searchButton;

	private TweetAdapter adapter;
	private Cursor c;

	// handler
	static Handler handler;

	// the menu
	private static final int OPTIONS_MENU_PROFILE = 10;
	private static final int OPTIONS_MENU_MESSAGES = 20;
	private static final int OPTIONS_MENU_SETTINGS = 40;
	private static final int OPTIONS_MENU_PAIR = 50;
	private static final int OPTIONS_MENU_ABOUT = 60;
	private static final int OPTIONS_MENU_LOGOUT = 70;

	private static final int SHOW_TIMELINE = 1;
	private static final int SHOW_FAVORITES = 2;
	private static final int SHOW_MENTIONS = 3;
	private static final int SHOW_SEARCH = 5;
	
	private int currentFilter = 0;
	private int positionIndex;
	private int positionTop;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.main);
		
		timelineListView = (ListView) findViewById(R.id.tweetList); 

		// Header buttons
		timelineButton = (Button) findViewById(R.id.headerBarTimelineButton);
		timelineButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setFilter(SHOW_TIMELINE);
			}
		});
		favoritesButton = (Button) findViewById(R.id.headerBarFavoritesButton);
		favoritesButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setFilter(SHOW_FAVORITES);
			}
		});
		mentionsButton = (Button) findViewById(R.id.headerBarMentionsButton);
		mentionsButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setFilter(SHOW_MENTIONS);
			}
		});

		tweetButton = (ImageButton) findViewById(R.id.headerBarTweetButton);
		tweetButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				startActivity(new Intent(getBaseContext(), NewTweetActivity.class));
			}
		});

		searchButton = (ImageButton) findViewById(R.id.headerBarSearchButton);
		searchButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Toast.makeText(getBaseContext(), "SEARCH NOT YET AVAILABLE", Toast.LENGTH_LONG).show();
				//setFilter(SHOW_SEARCH);
			}
		});
		
		setInstance(this);
		
		Log.i(TAG, "created");

	}
	
	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
		
		// Are we in disaster mode?
		LinearLayout headerBar = (LinearLayout) findViewById(R.id.headerBar);
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			headerBar.setBackgroundResource(R.drawable.top_bar_background_disaster);
		} else {
			headerBar.setBackgroundResource(R.drawable.top_bar_background);
		}

		Log.i(TAG, "resuming");
		
		// if we just got logged in, we load the timeline
		Intent i = getIntent();
		if(i.hasExtra("login")){
			Log.i(TAG, "just logged in");
			i.removeExtra("login");
			setFilter(SHOW_TIMELINE);
		} else {
			setFilter(currentFilter);	
		}
		
		if(positionIndex != 0 | positionTop !=0){
			timelineListView.setSelectionFromTop(positionIndex, positionTop);
		}
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		setInstance(null);

	}


	/**
	 * Populate the Options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		menu.add(1, OPTIONS_MENU_PROFILE, 1, "My Profile");
		menu.add(2, OPTIONS_MENU_MESSAGES, 2, "Messages");
		menu.add(3, OPTIONS_MENU_SETTINGS, 4, "Settings");
		/*
		if(ScanningService.isDisasterModeSupported()){
			menu.add(1,OPTIONS_MENU_PAIR, 2, "Pair");
		}
		 */
		menu.add(4, OPTIONS_MENU_ABOUT, 5, "About");
		menu.add(5, OPTIONS_MENU_LOGOUT, 6, "Logout");

		return true;
	}

	/**
	 * Handle options menu selection
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item){

		Intent i;
		switch(item.getItemId()){
		
		case OPTIONS_MENU_PROFILE:
			Uri uri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS);
			Cursor c = managedQuery(uri, null, TwitterUsers.TWITTERUSERS_COLUMNS_ID+"="+LoginActivity.getTwitterId(this), null, null);
			if(c.getCount()!=1) return false;
			c.moveToFirst();
			int rowId = c.getInt(c.getColumnIndex("_id"));
			
			if(rowId>0){
				// show the local user
				i = new Intent(this, ShowUserActivity.class);
				i.putExtra("rowId", rowId);
				startActivity(i);
			}
			break;
		
		case OPTIONS_MENU_MESSAGES:
			Toast.makeText(this, "DIRECT MESSAGES NOT YET AVAILABLE", Toast.LENGTH_LONG).show();
			break;
		
		case OPTIONS_MENU_SETTINGS:
			// Launch PrefsActivity
			i = new Intent(this, PrefsActivity.class);
			startActivity(i);    
			break;

		case OPTIONS_MENU_LOGOUT:
			// In disaster mode we don't allow logging out
			if(PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON)==false){
				showLogoutDialog();
			} else {
				Toast.makeText(this, "Disable Disaster Mode first. NOTE: Once you log out Twimight will not work until you are connected to the internet again!", Toast.LENGTH_LONG).show();
			}
			break;
		case OPTIONS_MENU_ABOUT:
			// Launch AboutActivity
			i = new Intent(this, AboutActivity.class);
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
	  positionIndex = timelineListView.getFirstVisiblePosition();
	  View v = timelineListView.getChildAt(0);
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
	 * Which tweets do we show? Timeline, favorites, mentions, messages, search?
	 * @param filter
	 */
	private void setFilter(int filter){
		// set all colors to transparent
		resetBackgrounds();
		Button b=null;
		
		switch(filter) {
		case SHOW_TIMELINE: 
			b = timelineButton;
			c = managedQuery(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_TIMELINE;

			break;
		case SHOW_FAVORITES: 
			b = favoritesButton;
			c = managedQuery(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_FAVORITES;

			break;
		case SHOW_MENTIONS: 
			b = mentionsButton;
			c = managedQuery(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_MENTIONS;

			break;
		case SHOW_SEARCH: 
			
			break;
		default:
			b= timelineButton;
			c = managedQuery(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_TIMELINE;

		}
		
		// style button
		if(b!=null){
			b.setBackgroundColor(R.color.black);
			b.setTextColor(R.color.headerBarTextOn);
		}

		adapter = new TweetAdapter(this, c);		
		timelineListView.setAdapter(adapter);

		// Click listener when the user clicks on a tweet
		timelineListView.setClickable(true);
		timelineListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Cursor c = (Cursor) timelineListView.getItemAtPosition(position);
				//c.moveToFirst();
				Intent i = new Intent(getBaseContext(), ShowTweetActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				startActivity(i);
			}
		});
	}
	
	/**
	 * Set all backgrounds of header buttons to transparent.
	 */
	private void resetBackgrounds(){
		timelineButton.setBackgroundResource(R.color.transparent);
		timelineButton.setTextColor(R.color.headerBarTextOff);
		favoritesButton.setBackgroundResource(R.color.transparent);
		favoritesButton.setTextColor(R.color.headerBarTextOff);
		mentionsButton.setBackgroundResource(R.color.transparent);
		mentionsButton.setTextColor(R.color.headerBarTextOff);
	}
	
	/**
	 * Asks the user if she really want to log out
	 */
	private void showLogoutDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to log out?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   LoginActivity.logout(getBaseContext());
		        	   finish();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * @param instance the instance to set
	 */
	public static void setInstance(ShowTweetListActivity instance) {
		ShowTweetListActivity.instance = instance;
	}

	/**
	 * @return the instance
	 */
	public static ShowTweetListActivity getInstance() {
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
}
