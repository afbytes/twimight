package ch.ethz.twimight.fragments.adapters;

import java.util.HashMap;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.util.Log;
import ch.ethz.twimight.fragments.ListFragment;

public class PageAdapter extends FragmentPagerAdapter {
    
	
	HashMap<Integer, ? extends ListFragment> fragmentsMap;
	
	public PageAdapter(FragmentManager fm, HashMap<Integer, ? extends ListFragment> map) {
		super(fm);
		this.fragmentsMap=map;
		
	}

	@Override
	public Fragment getItem(int pos) {
		Log.i("PageAdapter","inside getItem");
		return fragmentsMap.get(pos);
	}

	@Override
	public int getCount() {
		
		return fragmentsMap.size();
	}

}
