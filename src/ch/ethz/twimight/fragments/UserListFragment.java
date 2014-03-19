package ch.ethz.twimight.fragments;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import ch.ethz.twimight.activities.UserProfileActivity;
import ch.ethz.twimight.net.twitter.UserAdapter;
import ch.ethz.twimight.views.PullToRefreshListView;

@SuppressLint("ValidFragment")
public abstract class UserListFragment extends ListFragment {

	public static final int SEARCH_USERS = 10;

//	public static final int FRIENDS_KEY = 20;
//	public static final int FOLLOWERS_KEY = 21;
//	public static final int PEERS_KEY = 22;

	public UserListFragment() {
	};

	public UserListFragment(int type) {
		this.mType = type;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

		PullToRefreshListView list = (PullToRefreshListView) super
				.onCreateView(inflater, container, savedInstanceState);
		list.setOverscrollEnabled(false);
		// Click listener when the user clicks on a tweet
		list.setClickable(true);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
				Cursor c = (Cursor) arg0.getItemAtPosition(position);
				Intent i = new Intent(getActivity(), UserProfileActivity.class);
				i.putExtra(UserProfileActivity.EXTRA_ROW_ID, c.getInt(c.getColumnIndex("_id")));
				i.putExtra(UserProfileActivity.EXTRA_TYPE, mType);
				startActivity(i);
			}
		});

		return list;

	}

	/*
	 * @Override void getActionBarTitle() { String title = null; String subtitle
	 * = null; switch (mType) { case FRIENDS_KEY: title = "@" +
	 * LoginActivity.getTwitterScreenname(getActivity()); break; case
	 * FOLLOWERS_KEY: title = "@" +
	 * LoginActivity.getTwitterScreenname(getActivity()); break; case PEERS_KEY:
	 * title = "@" + LoginActivity.getTwitterScreenname(getActivity()); break;
	 * case SEARCH_USERS: title = getString(R.string.search_results); subtitle =
	 * "\"" + SearchableActivity.query + "\""; break; } ActionBar actionBar =
	 * getActivity().getActionBar(); actionBar.setTitle(title);
	 * actionBar.setSubtitle(subtitle); }
	 */

	/*
	 * @Override Cursor getCursor(int filter) { Cursor c = null;
	 * 
	 * switch (filter) { case FRIENDS_KEY:
	 * 
	 * c = mResolver.query( Uri.parse("content://" +
	 * TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS +
	 * "/" + TwitterUsers.TWITTERUSERS_FRIENDS), null, null, null, null);
	 * 
	 * break; case FOLLOWERS_KEY:
	 * 
	 * c = mResolver.query( Uri.parse("content://" +
	 * TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS +
	 * "/" + TwitterUsers.TWITTERUSERS_FOLLOWERS), null, null, null, null);
	 * 
	 * break; case PEERS_KEY:
	 * 
	 * c = mResolver.query( Uri.parse("content://" +
	 * TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS +
	 * "/" + TwitterUsers.TWITTERUSERS_DISASTER), null, null, null, null);
	 * 
	 * break;
	 * 
	 * case SEARCH_USERS: Log.i("UserListFragment", "query"); c =
	 * mResolver.query( Uri.parse("content://" +
	 * TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS +
	 * "/" + TwitterUsers.TWITTERUSERS_SEARCH), null, SearchableActivity.query,
	 * null, null);
	 * 
	 * break; } return c; }
	 */

	@Override
	CursorAdapter getListAdapter() {
		return new UserAdapter(getActivity(), null);
	}

}
