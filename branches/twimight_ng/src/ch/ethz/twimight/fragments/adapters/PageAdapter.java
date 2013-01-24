package ch.ethz.twimight.fragments.adapters;

import java.util.HashMap;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.util.Log;
import ch.ethz.twimight.activities.SearchableActivity;
import ch.ethz.twimight.fragments.ListFragment;
import ch.ethz.twimight.fragments.TweetListFragment;
import ch.ethz.twimight.fragments.UserListFragment;

public class PageAdapter extends FragmentPagerAdapter {
    
	public static final int POS_ZERO = 0;	
	public static final int POS_ONE = 1;
	public static final int POS_TWO = 2;
	HashMap<Integer, ? extends ListFragment> fragmentsMap;
	Activity act;
	
	public PageAdapter(Activity act, FragmentManager fm, HashMap<Integer, ? extends ListFragment> map) {
		super(fm);
		this.fragmentsMap=map;
		this.act = act;
		Log.i("PageAdapter","creating new page adapter");
	}
	
	

	@Override
	public Fragment getItem(int pos) {
		Log.i("PageAdapter","getting item");
		if (fragmentsMap != null)
			return fragmentsMap.get(pos );
		else if(pos == 0) 
			return 	new TweetListFragment(act, SearchableActivity.SHOW_SEARCH_TWEETS);
		else 
			return new UserListFragment(act, SearchableActivity.SHOW_SEARCH_USERS);
		
	}

	@Override
	public int getCount() {
		
		if (fragmentsMap != null)
			return fragmentsMap.size();
		else return 2;
	}

}
