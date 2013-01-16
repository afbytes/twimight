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
import ch.ethz.twimight.activities.ShowUserListActivity;
import ch.ethz.twimight.net.twitter.TwitterUserAdapter;
import ch.ethz.twimight.net.twitter.TwitterUsers;

public class UserListFragment extends ListFragment {
	
	
	
	Cursor c;
	
	public UserListFragment(){};
	
	public UserListFragment(Activity activity, int type) {
		super();		
		this.type = type;		
		
		
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

		case ShowUserListActivity.FRIENDS_KEY: 
			
			c = resolver.query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + 
					"/" + TwitterUsers.TWITTERUSERS_FRIENDS), null, null, null, null);
			

			break;
		case ShowUserListActivity.FOLLOWERS_KEY: 
			
			c = resolver.query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS 
					+ "/" + TwitterUsers.TWITTERUSERS_FOLLOWERS), null, null, null, null);
			

			break;
		case ShowUserListActivity.PEERS_KEY:
			
			c = resolver.query(Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS 
					+ "/" + TwitterUsers.TWITTERUSERS_DISASTER), null, null, null, null);
			
			break;
		
		}



		return new TwitterUserAdapter(getActivity(), c);		




	}

}
