package ch.ethz.twimight.activities;

import java.util.ArrayList;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.adapters.TweetDetailPageAdapter;
import ch.ethz.twimight.net.twitter.Tweets;

/**
 * Shows a tweet with more detailed information and the rest of the conversation
 * if available. Depending on where it was launched from, the user can swipe
 * horizontally through the other tweets of that context (timeline, search, user tweets etc.).
 * 
 * @author Steven Meliopoulos
 * 
 */
public class TweetDetailActivity extends TwimightBaseActivity {

	public static final String EXTRA_KEY_CONTEXT = "EXTRA_KEY_CONTEXT";
	public static final String EXTRA_KEY_ROW_ID = "EXTRA_KEY_ROW_ID";
	public static final String EXTRA_KEY_USER_ID = "EXTRA_KEY_USER_ID";

	public static final String EXTRA_CONTEXT_SINGLE_TWEET = "EXTRA_CONTEXT_SINGLE_TWEET";
	public static final String EXTRA_CONTEXT_TIMELINE = "EXTRA_CONTEXT_TIMELINE";
	public static final String EXTRA_CONTEXT_FAVORITES = "EXTRA_CONTEXT_FAVORITES";
	public static final String EXTRA_CONTEXT_SEARCH = "EXTRA_CONTEXT_SEARCH";
	public static final String EXTRA_CONTEXT_MENTIONS = "EXTRA_CONTEXT_MENTIONS";
	public static final String EXTRA_CONTEXT_USER = "EXTRA_CONTEXT_USER";

	private long mUserId;
	private long mRowId;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		ViewPager viewPager;
		Intent intent = getIntent();

		mRowId = intent.getLongExtra(EXTRA_KEY_ROW_ID, 0);

		String tweetContext = intent.getStringExtra(EXTRA_KEY_CONTEXT);
		mUserId = intent.getLongExtra(EXTRA_KEY_USER_ID, 0);

		ArrayList<Long> rowIdList = getRowIds(tweetContext);
		if (rowIdList != null) {
			TweetDetailPageAdapter pageAdapter = new TweetDetailPageAdapter(getFragmentManager(), rowIdList);
			viewPager = (ViewPager) findViewById(R.id.viewpager);
			viewPager.setAdapter(pageAdapter);
			viewPager.setCurrentItem(rowIdList.indexOf(mRowId));
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		String title = getString(R.string.tweet);
		getActionBar().setTitle(title);
	}

	private ArrayList<Long> getRowIds(String tweetContext) {
		Cursor c;
		ArrayList<Long> list = null;

		c = performQuery(tweetContext);

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

	private Cursor performQuery(String tweetContext) {
		Cursor c = null;

		if (EXTRA_CONTEXT_TIMELINE.equals(tweetContext)) {
			c = getContentResolver().query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
		} else if (EXTRA_CONTEXT_FAVORITES.equals(tweetContext)) {
			c = getContentResolver().query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
		} else if (EXTRA_CONTEXT_MENTIONS.equals(tweetContext)) {
			c = getContentResolver().query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
		} else if (EXTRA_CONTEXT_SEARCH.equals(tweetContext)) {
			c = getContentResolver().query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.SEARCH), null,
					SearchableActivity.mQuery, null, null);
		} else if (EXTRA_CONTEXT_USER.equals(tweetContext)) {
			c = getContentResolver().query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
							+ Tweets.TWEETS_TABLE_USER + "/" + mUserId), null, null, null, null);
		} else if (EXTRA_CONTEXT_SINGLE_TWEET.equals(tweetContext)) {
			c = getContentResolver().query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + mRowId), null, null,
					null, null);
		}
		return c;
	}

}
