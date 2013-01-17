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
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.fragments.ListFragment;
import ch.ethz.twimight.fragments.TweetListFragment;
import ch.ethz.twimight.fragments.adapters.PageAdapter;
import ch.ethz.twimight.listeners.TabListener;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.util.Constants;

/**
 * The main Twimight view showing the Timeline, favorites and mentions
 * @author thossmann
 * 
 */
public class ShowTweetListActivity extends TwimightBaseActivity{

	private static final String TAG = "ShowTweetListActivity";	
	
		
	public static boolean running= false;
	
	// handler
	static Handler handler;
	
	public static final int SHOW_USERTWEETS = 4;	

	private int positionIndex;
	private int positionTop;
	
	//LOGS
	LocationHelper locHelper ;
	long timestamp;	
	ConnectivityManager cm;
	StatisticsDBHelper locDBHelper;	
	CheckLocation checkLocation;
	public static final String ON_PAUSE_TIMESTAMP = "onPauseTimestamp";
	
	//EVENTS
	public static final String APP_STARTED = "app_started";
	public static final String APP_CLOSED = "app_closed";
	public static final String LINK_CLICKED = "link_clicked";
	public static final String TWEET_WRITTEN = "tweet_written";
	
	ActionBar actionBar;
	public static final String FILTER_REQUEST = "filter_request";
	
	public static final int TIMELINE_KEY = 0;	
	public static final int FAVORITES_KEY = 1;
	public static final int MENTIONS_KEY = 2;
	ViewPager viewPager;
	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);				
		setContentView(R.layout.main);
		
		//statistics
		locDBHelper = new StatisticsDBHelper(this);
		locDBHelper.open();
		cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		timestamp = System.currentTimeMillis();
		locHelper = new LocationHelper(this);
		handler = new Handler();
		checkLocation = new CheckLocation();
		handler.postDelayed(checkLocation, 1*60*1000L);	  
		
		HashMap<Integer,? extends ListFragment> fragmentMap = createFragments();		
		PageAdapter pagAdapter = new PageAdapter(getFragmentManager(),fragmentMap);		
        viewPager = (ViewPager)  findViewById(R.id.viewpager);	
		
		viewPager.setAdapter(pagAdapter);
		viewPager.setOffscreenPageLimit(2);
		viewPager.setOnPageChangeListener(
	            new ViewPager.SimpleOnPageChangeListener() {
	                @Override
	                public void onPageSelected(int position) {
	                    // When swiping between pages, select the
	                    // corresponding tab.	                	
	                    getActionBar().setSelectedNavigationItem(position);
	                }
	            });
		
		//action bar
		actionBar = getActionBar();	
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		Tab tab = actionBar.newTab()
				.setIcon(R.drawable.ic_twimight_speech)
				.setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);

		tab = actionBar.newTab()
				.setIcon(R.drawable.ic_twimight_favorites)
				.setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);

		tab = actionBar.newTab()
				.setIcon(R.drawable.ic_twimight_mentions)
				.setTabListener(new TabListener(viewPager ));
		actionBar.addTab(tab);
		
		running = true;				

	}
	
	private HashMap<Integer,TweetListFragment> createFragments() {
		
		HashMap<Integer,TweetListFragment> map = new HashMap<Integer,TweetListFragment>();
		map.put(TIMELINE_KEY, new TweetListFragment(this,TIMELINE_KEY));
		map.put(FAVORITES_KEY, new TweetListFragment(this,FAVORITES_KEY));
		map.put(MENTIONS_KEY, new TweetListFragment(this,MENTIONS_KEY));
		
		return map;
	}

	private class CheckLocation implements Runnable {

		@Override
		public void run() {
			if (locHelper != null && locHelper.count > 0 && locDBHelper != null) {	
				Log.i(TAG,"writing log");
				locDBHelper.insertRow(locHelper.loc, cm.getActiveNetworkInfo().getTypeName(), APP_STARTED, null, timestamp);
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

		Intent intent = getIntent();

		if(intent.hasExtra(FILTER_REQUEST)) {
			viewPager.setCurrentItem(intent.getIntExtra(FILTER_REQUEST, TIMELINE_KEY));
			intent.removeExtra(FILTER_REQUEST);

		}

		Long pauseTimestamp =  getOnPauseTimestamp(this);
		if (pauseTimestamp != 0 &&  (System.currentTimeMillis()-pauseTimestamp) > 10 * 60 * 1000L ) {
			handler = new Handler();			
			handler.post(new CheckLocation());

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
		
		super.onStop();
	
		
	}
	

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		running = false;	
	
		if ((System.currentTimeMillis() - timestamp <= 1 * 60 * 1000L)&& locHelper!=null && locDBHelper != null && cm != null) {
			if (locHelper.count > 0) {			
				locHelper.unRegisterLocationListener();
				handler.removeCallbacks(checkLocation);				
				locDBHelper.insertRow(locHelper.loc, cm.getActiveNetworkInfo().getTypeName(),APP_STARTED , null, timestamp);
			} else {}
		}
		
		if ((locHelper != null && locHelper.count > 0) && locDBHelper != null && cm != null) {			
			locHelper.unRegisterLocationListener();			
			locDBHelper.insertRow(locHelper.loc, cm.getActiveNetworkInfo().getTypeName(), APP_CLOSED , null, System.currentTimeMillis());
		} else {}

			
		
		if(PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true)
			Toast.makeText(this, "Warning: The disaster mode is still running in the background ", Toast.LENGTH_LONG).show();


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
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
	  super.onRestoreInstanceState(savedInstanceState);
	  
	
	  positionIndex = savedInstanceState.getInt("positionIndex");
	  positionTop = savedInstanceState.getInt("positionTop");
	  
	  Log.i(TAG, "restoring " + positionIndex + " " + positionTop);
	}
	
	*/
	
	
	
	
	
	
	
}
