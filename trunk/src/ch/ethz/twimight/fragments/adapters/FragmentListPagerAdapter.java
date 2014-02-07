package ch.ethz.twimight.fragments.adapters;

import java.util.ArrayList;
import java.util.List;

import android.app.FragmentManager;
import android.support.v13.app.FragmentPagerAdapter;
import ch.ethz.twimight.fragments.ListFragment;

/**
 * A fragment pager adapter that serves up a fixed list of fragments.
 * 
 * @author Steven Meliopoulos
 * 
 */
public class FragmentListPagerAdapter extends FragmentPagerAdapter {

	private final List<ListFragment> mFragments = new ArrayList<ListFragment>();

	public FragmentListPagerAdapter(FragmentManager fragmentManager) {
		super(fragmentManager);
	}

	public void addFragment(ListFragment fragment) {
		mFragments.add(fragment);
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