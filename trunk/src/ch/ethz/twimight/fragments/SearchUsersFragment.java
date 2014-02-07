package ch.ethz.twimight.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import ch.ethz.twimight.activities.SearchableActivity;
import ch.ethz.twimight.net.twitter.TwitterUsers;

public class SearchUsersFragment extends UserListFragment {

	@Override
	Cursor getCursor() {
		Cursor cursor = mResolver.query(
				Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS
						+ "/" + TwitterUsers.TWITTERUSERS_SEARCH), null, SearchableActivity.mQuery, null, null);
		return cursor;
	}

	@Override
	Intent getOverscrollIntent() {
		return null;
	}

	public void notifyNewQuery(){
		updateList();
	}

}
