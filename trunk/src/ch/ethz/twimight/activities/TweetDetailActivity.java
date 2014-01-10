package ch.ethz.twimight.activities;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.TweetListFragment;
import ch.ethz.twimight.fragments.adapters.TweetDetailPageAdapter;
import ch.ethz.twimight.net.twitter.Tweets;

public class TweetDetailActivity extends TwimightBaseActivity  {

	public static final String EXTRA_TYPE = "type";
	public static final String EXTRA_ROW_ID = "rowId";
	
	ContentResolver resolver;
	long userId;

	// String query;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		ViewPager viewPager;
		resolver = getContentResolver();
		Intent intent = getIntent();

		long rowId = intent.getIntExtra(EXTRA_ROW_ID, 0);
		int type = intent.getIntExtra(EXTRA_TYPE, TweetListFragment.TIMELINE_KEY);
		// if (type == TweetListFragment.SEARCH_TWEETS)
		// query = intent.getStringExtra(ListFragment.SEARCH_QUERY);
		if (type == TweetListFragment.USER_TWEETS) {
			userId = intent.getLongExtra(TweetListFragment.USER_ID, -1);
		}
		

		ArrayList<Long> rowIdList = getRowIds(type);
		if (rowIdList != null) {
			TweetDetailPageAdapter pageAdapter = new TweetDetailPageAdapter(
					getFragmentManager(), rowIdList);
			viewPager = (ViewPager) findViewById(R.id.viewpager);
			viewPager.setAdapter(pageAdapter);
			// viewPager.setOffscreenPageLimit(2);
			viewPager.setCurrentItem(rowIdList.indexOf(rowId));
		}

	}

	@Override
	public void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		String title = getString(R.string.tweet);
		getActionBar().setTitle(title);
	}

	private ArrayList<Long> getRowIds(int type) {
		Cursor c;
		ArrayList<Long> list = null;

		c = performQuery(type);

		if (c != null && c.getCount() > 0) {

			c.moveToFirst();
			list = cursorToList(c);
		}
		return list;

	}

	private ArrayList<Long> cursorToList(Cursor c) {
		ArrayList<Long> list = new ArrayList<Long>();

		while (c.isAfterLast() != true) {
			long id = c.getLong(c.getColumnIndex("_id"));
			list.add(id);
			c.moveToNext();
		}
		return list;
	}

	private Cursor performQuery(int type) {
		Cursor c = null;

		switch (type) {
		case TweetListFragment.TIMELINE_KEY:

			c = resolver
					.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY
							+ "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_TIMELINE + "/"
							+ Tweets.TWEETS_SOURCE_ALL), null, null, null, null);

			break;
		case TweetListFragment.FAVORITES_KEY:
			c = resolver
					.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY
							+ "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_FAVORITES + "/"
							+ Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			break;
		case TweetListFragment.MENTIONS_KEY:
			c = resolver
					.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY
							+ "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_MENTIONS + "/"
							+ Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			break;
		case TweetListFragment.SEARCH_TWEETS:
			c = resolver.query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/"
							+ Tweets.TWEETS + "/" + Tweets.SEARCH), null,
					SearchableActivity.query, null, null);
			break;
		case TweetListFragment.USER_TWEETS:
			c = resolver.query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/"
							+ Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_USER
							+ "/" + userId), null, null, null, null);
		}
		return c;

	}

}
