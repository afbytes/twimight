package ch.ethz.twimight.fragments.adapters;

import java.util.HashMap;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.util.Log;
import ch.ethz.twimight.fragments.ListFragment;
import ch.ethz.twimight.fragments.TweetListFragment;
import ch.ethz.twimight.fragments.UserListFragment;

public class ListViewPageAdapter extends FragmentPagerAdapter {
	
	public static final String BUNDLE_TYPE = "bundle_type";
	public static final int BUNDLE_TYPE_TWEETS = 0;
	public static final int BUNDLE_TYPE_USERS = 1;
	public static final int BUNDLE_TYPE_SEARCH_RESULTS = 2;

	Bundle bundle;
	HashMap<Integer,ListFragment> map;
	
	public ListViewPageAdapter( FragmentManager fm, Bundle bundle) {
		super(fm);		
		this.bundle=bundle;
		Log.i("ListViewPageAdapter","creating adapter");
		switch (bundle.getInt(BUNDLE_TYPE)) {
			
		case BUNDLE_TYPE_TWEETS:
			map = createTweetListFragments();
			break;
		case BUNDLE_TYPE_USERS:
			map = createUserListFragments();
			break;
		case BUNDLE_TYPE_SEARCH_RESULTS:			
			map = createSearchListFragments();
			
			break;
		}
		
	}
	

	@Override
	public Fragment getItem(int pos) {
		return map.get(pos);

	}

	@Override
	public int getCount() {
		return map.size();

	}
	

	
	private HashMap<Integer,ListFragment> createSearchListFragments() {

		HashMap<Integer,ListFragment> map = new HashMap<Integer,ListFragment>();
		ListFragment searchTweetsFrag = new TweetListFragment(TweetListFragment.SEARCH_TWEETS);
		ListFragment searchUserFrag = new UserListFragment(UserListFragment.SEARCH_USERS);		
		map.put(0,searchTweetsFrag);
		map.put(1,searchUserFrag);

		return map;
	}

	private HashMap<Integer,ListFragment> createUserListFragments() {

		HashMap<Integer,ListFragment> map = new HashMap<Integer,ListFragment>();
		map.put(0, new UserListFragment(UserListFragment.FRIENDS_KEY));
		map.put(1, new UserListFragment(UserListFragment.FOLLOWERS_KEY));
		map.put(2, new UserListFragment(UserListFragment.PEERS_KEY));

		return map;
	}

	private HashMap<Integer,ListFragment> createTweetListFragments() {

		HashMap<Integer,ListFragment> map = new HashMap<Integer,ListFragment>();
		map.put(0, new TweetListFragment(TweetListFragment.TIMELINE_KEY));
		map.put(1, new TweetListFragment(TweetListFragment.FAVORITES_KEY));
		map.put(2, new TweetListFragment(TweetListFragment.MENTIONS_KEY));

		return map;
	}

}
