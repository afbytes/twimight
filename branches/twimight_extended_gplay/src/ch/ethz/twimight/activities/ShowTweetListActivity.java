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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.location.LocationHelper;
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

	// Views
	private TweetListView timelineListView;
	private ImageButton timelineButton;
	private ImageButton favoritesButton;
	private ImageButton mentionsButton;
	private ImageButton tweetButton;
	private ImageButton searchButton;

	private TweetAdapter adapter;
	private Cursor c;
	
	
	
	public static boolean running= false;
	
	// handler
	static Handler handler;

	// the menu
	private static final int OPTIONS_MENU_PROFILE = 10;
	private static final int OPTIONS_MENU_MESSAGES = 20;
	private static final int OPTIONS_MENU_SETTINGS = 40;
	private static final int OPTIONS_MENU_ABOUT = 60;
	private static final int OPTIONS_MENU_LOGOUT = 70;
	private static final int OPTIONS_MENU_FEEDBACK= 100;

	public static final int SHOW_TIMELINE = 1;
	public static final int SHOW_FAVORITES = 2;
	public static final int SHOW_MENTIONS = 3;
	public static final int SHOW_USERTWEETS = 4;
	
	private int currentFilter = SHOW_TIMELINE;
	private int positionIndex;
	private int positionTop;
	private static Context CONTEXT;
	
	//LOGS
	LocationHelper locHelper ;
	long timestamp;
	Intent intent;
	ConnectivityManager cm;
	StatisticsDBHelper statsDBHelper;	
	CheckLocation checkLocation;
	public static final String ON_PAUSE_TIMESTAMP = "onPauseTimestamp";
	
	//EVENTS
	public static final String APP_STARTED = "app_started";
	public static final String APP_CLOSED = "app_closed";
	public static final String LINK_CLICKED = "link_clicked";
	public static final String TWEET_WRITTEN = "tweet_written";

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.main);	
		CONTEXT = this;
		//statistics
		statsDBHelper = new StatisticsDBHelper(this);
		statsDBHelper.open();
		
		cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		timestamp = System.currentTimeMillis();
		
		locHelper = LocationHelper.getInstance(this);
		locHelper.registerLocationListener();
		
		handler = new Handler();
		checkLocation = new CheckLocation();
		handler.postDelayed(checkLocation, 1*60*1000L);

	    
		setTitle(getString(R.string.app_name) + " - @" + LoginActivity.getTwitterScreenname(this));
		
		running = true;
		timelineListView = (TweetListView) findViewById(R.id.tweetList);
		timelineListView.setEmptyView(findViewById(R.id.tweetListEmpty));
		
		// Header buttons
		timelineButton = (ImageButton) findViewById(R.id.headerBarTimelineButton);
		timelineButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setFilter(SHOW_TIMELINE);
			}
		});
		favoritesButton = (ImageButton) findViewById(R.id.headerBarFavoritesButton);
		favoritesButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				setFilter(SHOW_FAVORITES);
			}
		});
		mentionsButton = (ImageButton) findViewById(R.id.headerBarMentionsButton);
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
		
		

	}
	
	private class CheckLocation implements Runnable {

		@Override
		public void run() {

			if (locHelper != null && locHelper.getCount() > 0 && statsDBHelper != null && cm.getActiveNetworkInfo() != null) {	
				
				statsDBHelper.insertRow(locHelper.getLocation(), cm.getActiveNetworkInfo().getTypeName(), APP_STARTED, null, timestamp);
				locHelper.unRegisterLocationListener();

			} else {}
			
		}
		
	}
	

	@Override
	protected void onNewIntent(Intent intent) {		
		setIntent(intent);
		
	}


	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
		running = true;
		
		intent = getIntent();
		
		// if we just got logged in, we load the timeline
		if(intent.hasExtra("filter_request")) {
			
			setFilter(intent.getIntExtra("filter_request", SHOW_TIMELINE));				
			intent.removeExtra("filter_request");

		} else if(intent.hasExtra("login")){
			
			intent.removeExtra("login");
	//		AppRater.app_launched(this);
			setFilter(SHOW_TIMELINE);
		

		} else {
			setFilter(currentFilter);	
		}
		
		Long pauseTimestamp =  getOnPauseTimestamp(this);
		if (pauseTimestamp != 0 &&  (System.currentTimeMillis()-pauseTimestamp) > 10 * 60 * 1000L ) {
			handler = new Handler();			
			handler.post(new CheckLocation());
			
		}
		
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
	
	
    


	@Override
	protected void onPause() {
		
		super.onPause();
		setOnPauseTimestamp(System.currentTimeMillis(), this);
	}


	/**
	 * 
	 * @param id
	 * @param context
	 */
	private static void setOnPauseTimestamp(long timestamp, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putLong(ON_PAUSE_TIMESTAMP, timestamp);
		prefEditor.commit();
	}
	
	/**
	 * Gets the Twitter ID from shared preferences
	 * @param context
	 * @return
	 */
	public static Long getOnPauseTimestamp(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(ON_PAUSE_TIMESTAMP, 0);
	}


	@Override
	protected void onStop() {
		running=false;	
		if (locHelper!= null)
			locHelper.unRegisterLocationListener();		
		super.onStop();
	
		
	}
	

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		running = false;
		
		timelineButton.setOnClickListener(null);
		favoritesButton.setOnClickListener(null);
		mentionsButton.setOnClickListener(null);
		tweetButton.setOnClickListener(null);
		searchButton.setOnClickListener(null);

		timelineListView.setOnItemClickListener(null);
		timelineListView.setAdapter(null);	
		
	
		if ((System.currentTimeMillis() - timestamp <= 1 * 60 * 1000L)&& locHelper!=null && statsDBHelper != null && 
				cm.getActiveNetworkInfo() != null) {
			
			if (locHelper.getCount() > 0 && cm.getActiveNetworkInfo() != null ) {					
				handler.removeCallbacks(checkLocation);				
				statsDBHelper.insertRow(locHelper.getLocation(), cm.getActiveNetworkInfo().getTypeName(),APP_STARTED , null, timestamp);
			} else {}
		}
		
		if ((locHelper != null && locHelper.getCount() > 0) && statsDBHelper != null && cm.getActiveNetworkInfo() != null) {							
			statsDBHelper.insertRow(locHelper.getLocation(), cm.getActiveNetworkInfo().getTypeName(), APP_CLOSED , null, System.currentTimeMillis());
		} else {}

		if(c!=null) c.close();				
		unbindDrawables(findViewById(R.id.showTweetListRoot));
		
		if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true)
			Toast.makeText(this, getString(R.string.disastermode_running), Toast.LENGTH_LONG).show();


	}

	
	/**
	 * Populate the Options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		menu.add(1, OPTIONS_MENU_PROFILE, 1, getString(R.string.profile)).setIcon(R.drawable.ic_menu_friendslist);
		menu.add(2, OPTIONS_MENU_MESSAGES, 2, getString(R.string.messages)).setIcon(R.drawable.ic_menu_start_conversation);
		menu.add(3, OPTIONS_MENU_SETTINGS, 4, getString(R.string.settings)).setIcon(R.drawable.ic_menu_preferences);				
		menu.add(5, OPTIONS_MENU_LOGOUT, 9, getString(R.string.logout)).setIcon(R.drawable.ic_menu_close_clear_cancel);
		menu.add(6, OPTIONS_MENU_ABOUT, 8, getString(R.string.about)).setIcon(R.drawable.ic_menu_info_details);
		menu.add(7, OPTIONS_MENU_FEEDBACK, 7, getString(R.string.feedback)).setIcon(R.drawable.ic_menu_edit);

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
				Toast.makeText(this, getString(R.string.disable_disastermode), Toast.LENGTH_LONG).show();
			}
			break;
		case OPTIONS_MENU_ABOUT:
			// Launch AboutActivity
			i = new Intent(this, AboutActivity.class);
			startActivity(i);    
			break;
		
		case OPTIONS_MENU_FEEDBACK:
			// Launch FeedbacktActivity
			i = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.TDS_BASE_URL + "/bugs/new"));
			startActivity(i);
			//i = new Intent(this, FeedbackActivity.class);
			//startActivity(i);    
			break;
		default:
			return false;
		}
		return true;
	}
	


	@Override
		protected void onActivityResult(int requestCode, int resultCode, Intent data) {
			switch(requestCode) {
			case PrefsActivity.REQUEST_DISCOVERABLE:
				
				if (resultCode != Activity.RESULT_CANCELED) {
					Intent intent = new Intent(this, DeviceListActivity.class);
					startActivity(intent);
				}
						
			}
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
	  
	 
	}
	
	
	
	/**
	 * Which tweets do we show? Timeline, favorites, mentions?
	 * @param filter
	 */
	private void setFilter(int filter){
		// set all header button colors to transparent
		resetButtons();
		ImageButton b=null;
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
		
		startManagingCursor(c); 
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
		builder.setMessage(getString(R.string.logout_question))
		       .setCancelable(false)
		       .setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   LoginActivity.logout(getBaseContext());
		        	   finish();
		           }
		       })
		       .setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
}
