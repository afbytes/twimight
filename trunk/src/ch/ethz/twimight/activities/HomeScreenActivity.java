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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.fragments.FavoritesFragment;
import ch.ethz.twimight.fragments.MentionsFragment;
import ch.ethz.twimight.fragments.TimelineFragment;
import ch.ethz.twimight.fragments.adapters.FragmentListPagerAdapter;
import ch.ethz.twimight.listeners.TabListener;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.twitter.NotificationService;
import ch.ethz.twimight.util.Constants;

/**
 * The main Twimight view showing the Timeline, favorites and mentions
 * 
 * @author thossmann
 * 
 */
public class HomeScreenActivity extends TwimightBaseActivity {

	private static final String TAG = HomeScreenActivity.class.getName();

	public static boolean running = false;
	// handler
	static Handler handler;

	// LOGS
	LocationHelper locHelper;
	long timestamp;
	ConnectivityManager cm;
	StatisticsDBHelper locDBHelper;
	CheckLocation checkLocation;
	public static final String ON_PAUSE_TIMESTAMP = "onPauseTimestamp";

	ActionBar actionBar;
	public static final String EXTRA_KEY_INITIAL_TAB = "filter_request";
	public static final String EXTRA_INITIAL_TAB_TIMELINE = "EXTRA_INITIAL_TAB_TIMELINE";
	public static final String EXTRA_INITIAL_TAB_FAVORITES = "EXTRA_INITIAL_TAB_FAVORITES";
	public static final String EXTRA_INITIAL_TAB_MENTIONS = "EXTRA_INITIAL_TAB_MENTIONS";

	ViewPager mViewPager;
	FragmentListPagerAdapter mPagerAdapter;

	private String[] mFragmentTitles;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(null);
		setContentView(R.layout.main);

		// reduces overdraw of whole screen by 1
		getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.transparent));

		// statistics
		locDBHelper = new StatisticsDBHelper(getApplicationContext());
		locDBHelper.open();

		cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		timestamp = System.currentTimeMillis();

		locHelper = LocationHelper.getInstance(this);
		locHelper.registerLocationListener();

		handler = new Handler();
		checkLocation = new CheckLocation();
		handler.postDelayed(checkLocation, 1 * 60 * 1000L);

		getActionBar().setSubtitle("@" + LoginActivity.getTwitterScreenname(this));
		mFragmentTitles = new String[] { getString(R.string.timeline), getString(R.string.favorites),
				getString(R.string.mentions) };

		mPagerAdapter = new FragmentListPagerAdapter(getFragmentManager());
		mPagerAdapter.addFragment(new TimelineFragment());
		mPagerAdapter.addFragment(new FavoritesFragment());
		mPagerAdapter.addFragment(new MentionsFragment());

		mViewPager = (ViewPager) findViewById(R.id.viewpager);

		mViewPager.setAdapter(mPagerAdapter);
		mViewPager.setOffscreenPageLimit(2);
		mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				// When swiping between pages, select the
				// corresponding tab.
				setFragment(position);
			}
		});

		// action bar
		actionBar = getActionBar();
		actionBar.setHomeButtonEnabled(false);
		actionBar.setDisplayHomeAsUpEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

		Tab timelineTab = actionBar.newTab().setIcon(R.drawable.ic_timeline)
				.setTabListener(new TabListener(mViewPager));
		actionBar.addTab(timelineTab);

		Tab favoritesTab = actionBar.newTab().setIcon(R.drawable.ic_favorites)
				.setTabListener(new TabListener(mViewPager));
		actionBar.addTab(favoritesTab);

		Tab mentionsTab = actionBar.newTab().setIcon(R.drawable.ic_mentions)
				.setTabListener(new TabListener(mViewPager));
		actionBar.addTab(mentionsTab);

		int initialPosition = 0;
		Intent intent = getIntent();
		if (intent.hasExtra(EXTRA_KEY_INITIAL_TAB)) {
			String initialTab = intent.getStringExtra(EXTRA_KEY_INITIAL_TAB);

			if (EXTRA_INITIAL_TAB_TIMELINE.equals(initialTab)) {
				initialPosition = 0;
			} else if (EXTRA_INITIAL_TAB_FAVORITES.equals(initialTab)) {
				initialPosition = 1;
			} else if (EXTRA_INITIAL_TAB_MENTIONS.equals(initialTab)) {
				initialPosition = 2;
			}
			intent.removeExtra(EXTRA_KEY_INITIAL_TAB);
		}
		mViewPager.setCurrentItem(initialPosition);
		setFragment(initialPosition);
	}

	private void setFragment(int position) {
		getActionBar().setTitle(mFragmentTitles[position]);
		getActionBar().setSelectedNavigationItem(position);
	}

	private class CheckLocation implements Runnable {

		@Override
		public void run() {
			if (locHelper != null && locHelper.getCount() > 0 && locDBHelper != null
					&& cm.getActiveNetworkInfo() != null) {
				Log.i(TAG, "writing log");
				locDBHelper.insertRow(locHelper.getLocation(), cm.getActiveNetworkInfo().getTypeName(),
						StatisticsDBHelper.APP_STARTED, null, timestamp);
				locHelper.unRegisterLocationListener();
			}
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
	public void onResume() {
		super.onResume();
		running = true;
		markTimelineSeen();
		markMentionsSeen();
		Long pauseTimestamp = getOnPauseTimestamp(this);
		if (pauseTimestamp != 0 && (System.currentTimeMillis() - pauseTimestamp) > 10 * 60 * 1000L) {
			handler = new Handler();
			handler.post(new CheckLocation());
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		setOnPauseTimestamp(System.currentTimeMillis(), this);
		markTimelineSeen();
		markMentionsSeen();
	}

	private void markTimelineSeen(){
		Intent timelineSeenIntent = new Intent(this, NotificationService.class);
		timelineSeenIntent.putExtra(NotificationService.EXTRA_KEY_ACTION, NotificationService.ACTION_MARK_TIMELINE_SEEN);
		startService(timelineSeenIntent);
	}
	
	private void markMentionsSeen(){
		Intent mentionsSeenIntent = new Intent(this, NotificationService.class);
		mentionsSeenIntent.putExtra(NotificationService.EXTRA_KEY_ACTION, NotificationService.ACTION_MARK_MENTIONS_SEEN);
		startService(mentionsSeenIntent);
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
	 * 
	 * @param context
	 * @return
	 */
	public static Long getOnPauseTimestamp(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(ON_PAUSE_TIMESTAMP, 0);
	}

	@Override
	protected void onStop() {
		running = false;
		locHelper.unRegisterLocationListener();
		super.onStop();

	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		running = false;

		mPagerAdapter = null;
		mViewPager = null;

		actionBar = null;

		Log.i(TAG, "destroying main activity");
		if ((System.currentTimeMillis() - timestamp <= 1 * 60 * 1000L) && locHelper != null && locDBHelper != null
				&& cm.getActiveNetworkInfo() != null) {

			if (locHelper.getCount() > 0 && cm.getActiveNetworkInfo() != null) {
				handler.removeCallbacks(checkLocation);
				locDBHelper.insertRow(locHelper.getLocation(), cm.getActiveNetworkInfo().getTypeName(),
						StatisticsDBHelper.APP_STARTED, null, timestamp);
			} else {
			}
		}

		if ((locHelper != null && locHelper.getCount() > 0) && locDBHelper != null && cm.getActiveNetworkInfo() != null) {
			locDBHelper.insertRow(locHelper.getLocation(), cm.getActiveNetworkInfo().getTypeName(),
					StatisticsDBHelper.APP_CLOSED, null, System.currentTimeMillis());
		} else {
		}

		TwimightBaseActivity.unbindDrawables(findViewById(R.id.rootRelativeLayout));

		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode",
				Constants.DISASTER_DEFAULT_ON) == true)
			Toast.makeText(this, getString(R.string.disastermode_running), Toast.LENGTH_LONG).show();

	}

}
