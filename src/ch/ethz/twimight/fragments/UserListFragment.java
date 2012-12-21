package ch.ethz.twimight.fragments;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import ch.ethz.twimight.net.twitter.TwitterUserAdapter;
import ch.ethz.twimight.net.twitter.TwitterUsers;

public class UserListFragment extends ListFragment {
	
	public static final int SHOW_FRIENDS = 0;
	public static final int SHOW_FOLLOWERS = 1;
	public static final int SHOW_DISASTER_PEERS = 2;
	
	Cursor c;
	
	public UserListFragment(Activity activity, String tag) {
		super();
		
		type = SHOW_FRIENDS;
		
		this.mActivity = activity;
		if (tag.equals("Followers"))
			type = this.SHOW_FOLLOWERS;
		else if (tag.equals("Peers"))
			type = this.SHOW_DISASTER_PEERS;
	}
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
		
       return super.onCreateView(inflater, container, savedInstanceState);
        
    }	

	@Override
	ListAdapter getData(int filter) {
		if(c!=null) c.close();

		switch(filter) {

		case SHOW_FRIENDS: 
			
			c = resolver.query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + 
					"/" + TwitterUsers.TWITTERUSERS_FRIENDS), null, null, null, null);
			

			break;
		case SHOW_FOLLOWERS: 
			
			c = resolver.query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS 
					+ "/" + TwitterUsers.TWITTERUSERS_FOLLOWERS), null, null, null, null);
			

			break;
		case SHOW_DISASTER_PEERS:
			
			c = resolver.query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS 
					+ "/" + TwitterUsers.TWITTERUSERS_DISASTER), null, null, null, null);
			
			break;
		
		}



		return new TwitterUserAdapter(getActivity(), c);		




	}

}
