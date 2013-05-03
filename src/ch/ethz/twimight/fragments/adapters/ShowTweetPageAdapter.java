package ch.ethz.twimight.fragments.adapters;

import java.util.ArrayList;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentStatePagerAdapter;
import ch.ethz.twimight.fragments.ShowTweetFragment;

public class ShowTweetPageAdapter extends FragmentStatePagerAdapter {
    
	ArrayList<Long> list;
	
	public ShowTweetPageAdapter(FragmentManager fm, ArrayList<Long> list){
		super(fm);
		this.list = list;
	}
	
	@Override
	public Fragment getItem(int pos) {
		long rowId = list.get(pos);
		return new ShowTweetFragment(rowId);
	}

	@Override
	public int getCount() {
		// TODO Auto-generated method stub
		return list.size();
	}

}
