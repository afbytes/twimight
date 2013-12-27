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
import android.widget.CursorAdapter;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.SearchableActivity;
import ch.ethz.twimight.activities.TweetDetailActivity;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterSyncService;
import ch.ethz.twimight.net.twitter.TwitterSyncService.FavoritesSyncService;
import ch.ethz.twimight.net.twitter.TwitterSyncService.MentionsSyncService;
import ch.ethz.twimight.net.twitter.TwitterSyncService.TimelineSyncService;
import ch.ethz.twimight.ui.PullToRefreshListView;

@SuppressLint("ValidFragment")
public class TweetListFragment extends ListFragment {

	long userId;
	public static final int TIMELINE_KEY = 10;
	public static final int FAVORITES_KEY = 11;
	public static final int MENTIONS_KEY = 12;
	public static final int SEARCH_TWEETS = 13;
	public static final int USER_TWEETS = 14;

	public static final String USER_ID = "USER_ID";
	


//	// Container Activity must implement this interface
//	public interface OnInitCompletedListener {
//		public void onInitCompleted();
//	}
//
//	OnInitCompletedListener listener;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (mType == USER_TWEETS) {
			userId = getArguments().getLong(USER_ID);
			Log.i("TEST", "userId: " + userId);
		}
	}

//	@Override
//	public void onAttach(Activity activity) {
//		super.onAttach(activity);
//		try {
//			listener = (OnInitCompletedListener) activity;
//		} catch (ClassCastException e) {
//			e.printStackTrace();
//		}
//	}

	public TweetListFragment() {

	};

	public TweetListFragment(int type) {
		this.mType = type;
		Log.i(TAG, "creating instance of tweet list frag");
	}


	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
//		listener = null;

		super.onDestroy();
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

	@Override
	void setActionBarTitles() {
		String title = null;
		String subtitle = null;
		switch (mType) {
		case TIMELINE_KEY:
			title = getString(R.string.timeline);
			subtitle = "@" + LoginActivity.getTwitterScreenname(getActivity());
			break;
		case FAVORITES_KEY:
			title = getString(R.string.favorites);
			subtitle = "@" + LoginActivity.getTwitterScreenname(getActivity());
			break;
		case MENTIONS_KEY:
			title = getString(R.string.mentions);
			subtitle = "@" + LoginActivity.getTwitterScreenname(getActivity());
			break;
		case SEARCH_TWEETS:
			title = getString(R.string.search_results);
			subtitle = "\"" + SearchableActivity.query + "\"";
			break;
		case USER_TWEETS:
			title = getString(R.string.timeline);
			break;
		}
		ActionBar actionBar = getActivity().getActionBar();
		actionBar.setTitle(title);
		actionBar.setSubtitle(subtitle);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		PullToRefreshListView list = (PullToRefreshListView) super.onCreateView(inflater,
				container, savedInstanceState);
		// Click listener when the user clicks on a tweet
		list.setClickable(true);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1,
					int position, long id) {
				Cursor c = (Cursor) arg0.getItemAtPosition(position);
				Intent i = new Intent(getActivity(), TweetDetailActivity.class);
				i.putExtra(TweetDetailActivity.EXTRA_ROW_ID, c.getInt(c.getColumnIndex("_id")));
				i.putExtra(TweetDetailActivity.EXTRA_TYPE, mType);
				if (mType == USER_TWEETS) {
					i.putExtra(USER_ID, userId);
				}
				startActivity(i);
			}
		});

		return list;
	}
	
	/**
	 * Which tweets do we show? Timeline, favorites, mentions?
	 * 
	 * @param filter
	 */
	@Override
	Cursor getCursor(int filter) {
		// set all header button colors to transparent
		Cursor c = null;
		overscrollIntent = new Intent(getActivity(), TwitterSyncService.class);

		switch (filter) {
		case TIMELINE_KEY:

			overscrollIntent = new Intent(getActivity(), TimelineSyncService.class);
			overscrollIntent.putExtra(TimelineSyncService.EXTRA_FORCE_SYNC, true);
			c = mResolver
					.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY
							+ "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_TIMELINE + "/"
							+ Tweets.TWEETS_SOURCE_ALL), null, null, null, null);

			break;
		case FAVORITES_KEY:

			overscrollIntent = new Intent(getActivity(), FavoritesSyncService.class);
			overscrollIntent.putExtra(TwitterSyncService.EXTRA_FORCE_SYNC, true);
			c = mResolver
					.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY
							+ "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_FAVORITES + "/"
							+ Tweets.TWEETS_SOURCE_ALL), null, null, null, null);

			break;
		case MENTIONS_KEY:

			overscrollIntent = new Intent(getActivity(), MentionsSyncService.class);
			overscrollIntent.putExtra(TwitterSyncService.EXTRA_FORCE_SYNC, true);
			c = mResolver
					.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY
							+ "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_MENTIONS + "/"
							+ Tweets.TWEETS_SOURCE_ALL), null, null, null, null);

			break;
		case SEARCH_TWEETS:
			c = mResolver.query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/"
							+ Tweets.TWEETS + "/" + Tweets.SEARCH), null,
					SearchableActivity.query, null, null);

			break;

		case USER_TWEETS:
			c = mResolver.query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/"
							+ Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_USER
							+ "/" + userId), null, null, null, null);
			Log.i("TEST", "QUERY PERFORMED	");

			break;

		}
		return c;

	}
	
	@Override
	CursorAdapter getListAdapter() {
		return new TweetAdapter(getActivity(), null);
	}
	
}
