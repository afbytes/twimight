package ch.ethz.twimight.listeners;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.util.Log;
import ch.ethz.twimight.fragments.ListFragment;
import ch.ethz.twimight.fragments.TweetListFragment;
import ch.ethz.twimight.fragments.UserListFragment;

public class TabListener<T extends Fragment> implements ActionBar.TabListener {
    private ListFragment mFragment;
    private final Activity mActivity;
    private final String mTag;
    private final Class<T> mClass;
   

    /** Constructor used each time a new tab is created.
      * @param activity  The host Activity, used to instantiate the fragment
      * @param tag  The identifier tag for the fragment
      * @param clz  The fragment's Class, used to instantiate the fragment
      */
    public TabListener(Activity activity, String tag, Class<T> clz) {
        mActivity = activity;
        mTag = tag;	
        mClass = clz;
        
    }

    /* The following are each of the ActionBar.TabListener callbacks */

    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        // Check if the fragment is already initialized
        if (mFragment == null) {        	
        	
            // If not, instantiate and add it to the activity
        	if(mClass.getName().equals("ch.ethz.twimight.fragments.TweetListFragment"))
        		mFragment = new TweetListFragment(mActivity, mTag);	 
        	else if(mClass.getName().equals("ch.ethz.twimight.fragments.UserListFragment"))
        		mFragment = new UserListFragment(mActivity, mTag);	
        	
            ft.add(android.R.id.content, mFragment, mTag);
        } else {
            // If it exists, simply attach it in order to show it
            ft.attach(mFragment);
       
        }
    }

    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        if (mFragment != null) {
            // Detach the fragment, because another one is being attached
            ft.detach(mFragment);
        }
    }

    public void onTabReselected(Tab tab, FragmentTransaction ft) {
        // User selected the already selected tab. Usually do nothing.
    }
}
