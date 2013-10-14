package ch.ethz.twimight.fragments;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.SearchableActivity;
import ch.ethz.twimight.activities.UserProfileActivity;
import ch.ethz.twimight.net.twitter.TwitterUserAdapter;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.ui.PullToRefreshListView;

@SuppressLint("ValidFragment")
public class UserListFragment extends ListFragment {

	public static final int SEARCH_USERS = 10;

	public static final int FRIENDS_KEY = 20;
	public static final int FOLLOWERS_KEY = 21;
	public static final int PEERS_KEY = 22;

	Cursor c;

	public UserListFragment() {
	};

	public UserListFragment(int type) {
		this.type = type;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		mlistAdapter = getData(type);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		PullToRefreshListView list = (PullToRefreshListView) super
				.onCreateView(inflater, container, savedInstanceState);
		list.setOverscrollEnabled(false);
		// Click listener when the user clicks on a tweet
		list.setClickable(true);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1,
					int position, long id) {
				Cursor c = (Cursor) arg0.getItemAtPosition(position);
				Intent i = new Intent(getActivity(), UserProfileActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				i.putExtra("type", type);
				startActivity(i);
			}
		});

		return list;

	}

	/**
	 * Updates the action bar title with a description of the fragment when it
	 * becomes visible.
	 */
	@Override
	public void setUserVisibleHint(boolean isVisibleToUser) {
		super.setUserVisibleHint(isVisibleToUser);
		if (isVisibleToUser) {
			setActionBarTitles();
		}
	}

	private void setActionBarTitles() {
		String title = null;
		String subtitle = null;
		switch (type) {
		case FRIENDS_KEY:
			title = "@" + LoginActivity.getTwitterScreenname(getActivity());
			break;
		case FOLLOWERS_KEY:
			title = "@" + LoginActivity.getTwitterScreenname(getActivity());
			break;
		case PEERS_KEY:
			title = "@" + LoginActivity.getTwitterScreenname(getActivity());
			break;
		case SEARCH_USERS:
			title = getString(R.string.search_results);
			subtitle = "\"" + SearchableActivity.query + "\"";
			break;
		}
		ActionBar actionBar = getActivity().getActionBar();
		actionBar.setTitle(title);
		actionBar.setSubtitle(subtitle);
	}

	@Override
	ListAdapter getData(int filter) {
		if (c != null)
			c.close();

		switch (filter) {

		case FRIENDS_KEY:

			c = resolver.query(
					Uri.parse("content://"
							+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
							+ TwitterUsers.TWITTERUSERS + "/"
							+ TwitterUsers.TWITTERUSERS_FRIENDS), null, null,
					null, null);

			break;
		case FOLLOWERS_KEY:

			c = resolver.query(
					Uri.parse("content://"
							+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
							+ TwitterUsers.TWITTERUSERS + "/"
							+ TwitterUsers.TWITTERUSERS_FOLLOWERS), null, null,
					null, null);

			break;
		case PEERS_KEY:

			c = resolver.query(
					Uri.parse("content://"
							+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
							+ TwitterUsers.TWITTERUSERS + "/"
							+ TwitterUsers.TWITTERUSERS_DISASTER), null, null,
					null, null);

			break;

		case SEARCH_USERS:
			Log.i("UserListFragment", "query");
			c = resolver.query(
					Uri.parse("content://"
							+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
							+ TwitterUsers.TWITTERUSERS + "/"
							+ TwitterUsers.TWITTERUSERS_SEARCH), null,
					SearchableActivity.query, null, null);

			break;

		}

		return new TwitterUserAdapter(getActivity(), c);

	}

}
