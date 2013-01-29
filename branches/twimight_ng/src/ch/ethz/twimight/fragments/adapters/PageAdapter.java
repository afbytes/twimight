package ch.ethz.twimight.fragments.adapters;

import java.util.HashMap;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
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
	
	Bundle bundle;
	
	public PageAdapter( FragmentManager fm, HashMap<Integer, ? extends ListFragment> map, Bundle bundle) {
		super(fm);
		this.fragmentsMap=map;
		this.bundle=bundle;
		Log.i("PageAdapter","creating new page adapter");
	}
	
	

	@Override
	public Fragment getItem(int pos) {
		Log.i("PageAdapter","getting item");
		if (fragmentsMap != null)
			return fragmentsMap.get(pos );
		else if(pos == 0) {
			//the fragments are created here because we can't get an homogeneous list
			TweetListFragment tlf = new TweetListFragment( SearchableActivity.SHOW_SEARCH_TWEETS);
			tlf.setArguments(bundle);
			return tlf;
		}
		else {
			UserListFragment ulf =  new UserListFragment( SearchableActivity.SHOW_SEARCH_USERS);
			ulf.setArguments(bundle);
			return ulf;
		}
		
	}

	@Override
	public int getCount() {
		
		if (fragmentsMap != null)
			return fragmentsMap.size();
		else return 2;
	}

}
