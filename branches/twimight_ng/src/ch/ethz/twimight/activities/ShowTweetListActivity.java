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

import java.lang.Thread.UncaughtExceptionHandler;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.TweetListView;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;

/**
 * The main Twimight view showing the Timeline, favorites and mentions
 * @author thossmann
 *
 */
public class ShowTweetListActivity extends TwimightBaseActivity{

	private static final String TAG = "ShowTweetListActivity";
	
	// Views
	private TweetListView timelineListView;
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
	private static final int OPTIONS_MENU_ABOUT = 60;
	private static final int OPTIONS_MENU_LOGOUT = 70;
	private static final int OPTIONS_MENU_PAIR= 80;

	public static final int SHOW_TIMELINE = 1;
	public static final int SHOW_FAVORITES = 2;
	public static final int SHOW_MENTIONS = 3;
	public static final int SHOW_USERTWEETS = 4;
	
	private int currentFilter = SHOW_TIMELINE;
	private int positionIndex;
	private int positionTop;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
				
		setContentView(R.layout.main);
		setTitle("Twimight - @" + LoginActivity.getTwitterScreenname(this));
		
		timelineListView = (TweetListView) findViewById(R.id.tweetList);
		timelineListView.setEmptyView(findViewById(R.id.tweetListEmpty));
		
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
				onSearchRequested();
			}
		});
		
		// if we just got logged in, we load the timeline
				Intent i = getIntent();
				if(i.hasExtra("filter_request")) {
					Log.i(TAG,"filter request: " + i.getIntExtra("filter_request", SHOW_TIMELINE));
					setFilter(i.getIntExtra("filter_request", SHOW_TIMELINE));				
					i.removeExtra("filter_request");
				} else if(i.hasExtra("login")){
					i.removeExtra("login");
					setFilter(SHOW_TIMELINE);
				} else {
					setFilter(currentFilter);	
				}
		
		Log.v(TAG, "created");		
	    Thread.setDefaultUncaughtExceptionHandler(new CustomExceptionHandler()); 


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

		
		
		
		if(positionIndex != 0 | positionTop !=0){
			timelineListView.setSelectionFromTop(positionIndex, positionTop);
		}
	}
	
	/**
	 * On pause
	 */
	@Override
	public void onPause(){
		Log.w(TAG,"onPause called");
		super.onPause();
				
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		Log.w(TAG,"onDestroy called");
		timelineButton.setOnClickListener(null);
		favoritesButton.setOnClickListener(null);
		mentionsButton.setOnClickListener(null);
		tweetButton.setOnClickListener(null);
		searchButton.setOnClickListener(null);

		timelineListView.setOnItemClickListener(null);
		timelineListView.setAdapter(null);

		if(c!=null) c.close();
				
		unbindDrawables(findViewById(R.id.showTweetListRoot));
		
		if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true)
			Toast.makeText(this, "Warning: The disaster mode is still running in the background ", Toast.LENGTH_LONG).show();


	}


	/**
	 * Populate the Options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		menu.add(1, OPTIONS_MENU_PROFILE, 1, "My Profile").setIcon(R.drawable.ic_menu_friendslist);
		menu.add(2, OPTIONS_MENU_MESSAGES, 2, "Messages").setIcon(R.drawable.ic_menu_start_conversation);
		menu.add(3, OPTIONS_MENU_SETTINGS, 4, "Settings").setIcon(R.drawable.ic_menu_preferences);				
		menu.add(1,OPTIONS_MENU_PAIR, 3, "Pair").setIcon(R.drawable.ic_menu_mark);				 
		menu.add(4, OPTIONS_MENU_ABOUT, 5, "About").setIcon(R.drawable.ic_menu_info_details);
		menu.add(5, OPTIONS_MENU_LOGOUT, 6, "Logout").setIcon(R.drawable.ic_menu_close_clear_cancel);

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
			Cursor c = getContentResolver().query(uri, null, TwitterUsers.COL_ID+"="+LoginActivity.getTwitterId(this), null, null);
			if(c.getCount()!=1) return false;
			c.moveToFirst();
			int rowId = c.getInt(c.getColumnIndex("_id"));
			
			if(rowId>0){
				// show the local user
				i = new Intent(this, ShowUserActivity.class);
				i.putExtra("rowId", rowId);
				startActivity(i);
			}
			c.close();
			break;
		
		case OPTIONS_MENU_MESSAGES:
			// Launch User Messages
			i = new Intent(this, ShowDMUsersListActivity.class);
			startActivity(i);    
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
		case OPTIONS_MENU_PAIR:
			//TODO: handle pairing
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
	
	
	public class CustomExceptionHandler implements UncaughtExceptionHandler {

		@Override
		public void uncaughtException(Thread t, Throwable e) {		
			 Log.e(TAG, "error ", e);
		}
	}
	
	/**
	 * Which tweets do we show? Timeline, favorites, mentions?
	 * @param filter
	 */
	private void setFilter(int filter){
		// set all header button colors to transparent
		resetButtons();
		Button b=null;
		Intent overscrollIntent = null;
		
		if(c!=null) c.close();
		
		switch(filter) {
		case SHOW_TIMELINE: 
			b = timelineButton;
			overscrollIntent = new Intent(this, TwitterService.class); 
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = getContentResolver().query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
											+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_TIMELINE;

			break;
		case SHOW_FAVORITES: 
			b = favoritesButton;
			overscrollIntent = new Intent(this, TwitterService.class); 
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = getContentResolver().query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
											+ Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_FAVORITES;

			break;
		case SHOW_MENTIONS: 
			Log.i(TAG,"show mentions");
			b = mentionsButton;
			overscrollIntent = new Intent(this, TwitterService.class); 
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = getContentResolver().query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
											+ Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_MENTIONS;

			break;
		default:
			b= timelineButton;
			overscrollIntent = new Intent(this, TwitterService.class); 
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = getContentResolver().query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
											+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			currentFilter=SHOW_TIMELINE;

		}
		
		// style button
		if(b!=null){
			b.setEnabled(false);
		}
		
		startManagingCursor(c); // @author pcarta
		adapter = new TweetAdapter(this, c);		
		timelineListView.setAdapter(adapter);		
		timelineListView.setOverscrollIntent(overscrollIntent);


		// Click listener when the user clicks on a tweet
		timelineListView.setClickable(true);
		timelineListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Cursor c = (Cursor) timelineListView.getItemAtPosition(position);
				Intent i = new Intent(getBaseContext(), ShowTweetActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				startActivity(i);
			}
		});
	}
	
	/**
	 * Enables all header buttons
	 */
	private void resetButtons(){
		timelineButton.setEnabled(true);
		favoritesButton.setEnabled(true);
		mentionsButton.setEnabled(true);
		searchButton.setEnabled(true);
		tweetButton.setEnabled(true);
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
	
}
