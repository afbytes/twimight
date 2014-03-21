package ch.ethz.twimight.activities;

import java.util.ArrayList;

import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.ViewPager;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.adapters.TweetDetailPageAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterSyncService;

/**
 * Shows a tweet with more detailed information and the rest of the conversation
 * if available. Depending on where it was launched from, the user can swipe
 * horizontally through the other tweets of that context (timeline, search, user
 * tweets etc.).
 * 
 * @author Steven Meliopoulos
 * 
 */
public class TweetDetailActivity extends TwimightBaseActivity {

	private static final String TAG = TweetDetailActivity.class.getName();

	public static final String EXTRA_KEY_CONTEXT = "EXTRA_KEY_CONTEXT";
	public static final String EXTRA_KEY_ROW_ID = "EXTRA_KEY_ROW_ID";
	public static final String EXTRA_KEY_TID = "EXTRA_KEY_TID";
	public static final String EXTRA_KEY_USER_ID = "EXTRA_KEY_USER_ID";

	public static final String EXTRA_CONTEXT_SINGLE_TWEET = "EXTRA_CONTEXT_SINGLE_TWEET";
	public static final String EXTRA_CONTEXT_SINGLE_TWEET_TID = "EXTRA_CONTEXT_SINGLE_TWEET_TID";
	public static final String EXTRA_CONTEXT_TIMELINE = "EXTRA_CONTEXT_TIMELINE";
	public static final String EXTRA_CONTEXT_FAVORITES = "EXTRA_CONTEXT_FAVORITES";
	public static final String EXTRA_CONTEXT_SEARCH = "EXTRA_CONTEXT_SEARCH";
	public static final String EXTRA_CONTEXT_MENTIONS = "EXTRA_CONTEXT_MENTIONS";
	public static final String EXTRA_CONTEXT_USER = "EXTRA_CONTEXT_USER";

	private long mUserId;
	private long mRowId;
	private long mTid;
	private String mTweetContext;
	private Cursor mCursor;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		Intent intent = getIntent();

		mTweetContext = intent.getStringExtra(EXTRA_KEY_CONTEXT);
		mRowId = intent.getLongExtra(EXTRA_KEY_ROW_ID, 0);
		mUserId = intent.getLongExtra(EXTRA_KEY_USER_ID, 0);
		mTid = intent.getLongExtra(EXTRA_KEY_TID, 0);
		initialize();
	}

	private void initialize() {
		Log.d(TAG, "initialize()");
		ArrayList<Long> rowIdList = getRowIds(mTweetContext);
		if (rowIdList != null) {
			Log.d(TAG, "rowIdList not null");
			TweetDetailPageAdapter pageAdapter = new TweetDetailPageAdapter(getFragmentManager(), rowIdList);
			ViewPager viewPager = (ViewPager) findViewById(R.id.viewpager);
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
		ArrayList<Long> list = null;

		mCursor = performQuery(tweetContext);
		if (mCursor != null) {
			// if the content is available -> collect all the row ids
			Log.d(TAG, "cursor count: " + mCursor.getCount());
			if (mCursor.getCount() > 0) {
				list = new ArrayList<Long>(mCursor.getCount());
				while (mCursor.moveToNext()) {
					long id = mCursor.getLong(mCursor.getColumnIndex(Tweets.COL_ROW_ID));
					list.add(id);
				}
				mCursor.close();
			} else {
				// tweet not in db yet -> load it!
				Intent loadTweetIntent = new Intent(this, TwitterSyncService.class);
				loadTweetIntent.putExtra(TwitterSyncService.EXTRA_KEY_ACTION,
						TwitterSyncService.EXTRA_ACTION_LOAD_TWEET_BY_TID);
				loadTweetIntent.putExtra(TwitterSyncService.EXTRA_KEY_TWEET_TID, mTid);
				startService(loadTweetIntent);
				// register observer so we can initialize the activity once the
				// content is available
				mCursor.registerContentObserver(new ContentObserver(new Handler()) {
					@Override
					public boolean deliverSelfNotifications() {
						return false;
					}

					@Override
					public void onChange(boolean selfChange) {
						super.onChange(selfChange);
						if (mCursor != null) {
							mCursor.unregisterContentObserver(this);
							mCursor.close();
							mCursor = null;
						}
						initialize();
					}
				});
			}
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
		} else if (EXTRA_CONTEXT_SINGLE_TWEET_TID.equals(tweetContext)) {
			c = getContentResolver().query(
					Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEET_TID
							+ "/" + mTid), null, null, null, null);
		}
		return c;
	}

}
