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

import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.FollowersFragment;
import ch.ethz.twimight.fragments.FriendsFragment;
import ch.ethz.twimight.fragments.ListFragment;
import ch.ethz.twimight.fragments.PeersFragment;
import ch.ethz.twimight.fragments.UserListFragment;
import ch.ethz.twimight.listeners.TabListener;

/**
 * Display a list of users (friends, followers, etc.)
 * 
 * @author thossmann
 * 
 */
public class UserListActivity extends TwimightBaseActivity {

	private static final String TAG = "ShowUsersActivity";

	private int positionIndex;
	private int positionTop;
	ViewPager viewPager;

	public static final String USER_FILTER_REQUEST = "user_filter_request";
	
	private final List<ListFragment> mFragments = new ArrayList<ListFragment>();

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(null);
		setContentView(R.layout.main);

		Intent intent = getIntent();
		// action bar
		actionBar = getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

//		Bundle bundle = new Bundle();
//		bundle.putInt(ListViewPageAdapter.BUNDLE_TYPE, ListViewPageAdapter.BUNDLE_TYPE_USERS);
//		ListViewPageAdapter pagAdapter = new ListViewPageAdapter(getFragmentManager(), bundle);
		
		mFragments.add(new FriendsFragment());
		mFragments.add(new FollowersFragment());
		mFragments.add(new PeersFragment());
		FragmentPagerAdapter pagAdapter = new FragmentListPagerAdapter(getFragmentManager());
		
		
		viewPager = (ViewPager) findViewById(R.id.viewpager);

		viewPager.setAdapter(pagAdapter);
		viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				// When swiping between pages, select the
				// corresponding tab.
				getActionBar().setSelectedNavigationItem(position);
			}
		});

		Tab tab = actionBar.newTab().setText(R.string.friends).setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);

		tab = actionBar.newTab().setText(R.string.followers).setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);

		tab = actionBar.newTab().setText(R.string.peers).setTabListener(new TabListener(viewPager));
		actionBar.addTab(tab);

		// actionBar.setSelectedNavigationItem(intent.getIntExtra(USER_FILTER_REQUEST,
		// FRIENDS_KEY));
		int selectedItemIndex;
		switch (intent.getIntExtra(USER_FILTER_REQUEST, 0)) {

		case UserListFragment.FRIENDS_KEY:
			selectedItemIndex = 0;
			break;
		case UserListFragment.FOLLOWERS_KEY:
			selectedItemIndex = 1;
			break;
		case UserListFragment.PEERS_KEY:
			selectedItemIndex = 2;
			break;
		default:
			selectedItemIndex = 0;
		}

		viewPager.setCurrentItem(selectedItemIndex);

	}

	/**
	 * Saves the current selection
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

		// positionIndex = userListView.getFirstVisiblePosition();
		// View v = userListView.getChildAt(0);
		// positionTop = (v == null) ? 0 : v.getTop();
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
	
	private class FragmentListPagerAdapter extends FragmentPagerAdapter {
		public FragmentListPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public ListFragment getItem(int position) {
			return mFragments.get(position);
		}

		@Override
		public int getCount() {
			return mFragments.size();
		}

	}

}
