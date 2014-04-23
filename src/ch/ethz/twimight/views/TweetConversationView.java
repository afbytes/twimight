package ch.ethz.twimight.views;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.TweetDetailActivity;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterSyncService;

/**
 * Displays a single tweet detailed in a big view. If there are previous tweets
 * in the conversation they will be loaded an displayed above the original
 * tweet. The tweet views are contained in a ListView because it provides better
 * control over the scroll state than a ScrollView.
 * 
 * @author Steven Meliopoulos
 * 
 */
public class TweetConversationView extends FrameLayout {

	private int mHeight;

	private long mRowId;
	private final ListView mListView;
	private TweetDetailView mMainTweetView;
	private final List<View> mTweetViews = new ArrayList<View>();
	private final BaseAdapter mListAdapter = new ConversationAdapter();
	private ContentObserver mTweetLoadedObserver;

	public TweetConversationView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		mListView = new ListView(context);
		mListView.setSmoothScrollbarEnabled(false);
		addView(mListView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		// OnLayoutChangeListener is needed to get the actual height of the view
		// as soon as it is known
		addOnLayoutChangeListener(new OnLayoutChangeListener() {

			@Override
			public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop,
					int oldRight, int oldBottom) {
				// make sure that the original tweet fills the whole view so
				// that we can insert the previous tweets above it without
				// pushing the original tweet down abruptly
				mHeight = getHeight();
				mMainTweetView.setMinimumHeight(mHeight);
				mListView.setSelectionFromTop(mListView.getCount(), 20);
				removeOnLayoutChangeListener(this);
			}
		});
	}

	/**
	 * Initializes the complete view to the content of the tweet with the given
	 * row ID.
	 * 
	 * @param rowId
	 *            the row ID of the tweet to be displayed
	 */
	public void setRowId(long rowId) {
		mRowId = rowId;
		mMainTweetView = new TweetDetailView(getContext(), mRowId);
		mTweetViews.add(mMainTweetView);
		mListView.setAdapter(mListAdapter);
		loadConversation();
	}

	/**
	 * Initiates the recursive process to load the previous tweets in the
	 * conversation.
	 */
	private void loadConversation() {
		Uri originalTweetUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + mRowId);
		Cursor originalTweetCursor = getContext().getContentResolver().query(originalTweetUri, null, null, null, null);
		if (originalTweetCursor != null && originalTweetCursor.getCount() > 0) {
			originalTweetCursor.moveToFirst();
			long replyToTweetId = originalTweetCursor.getLong(originalTweetCursor
					.getColumnIndex(Tweets.COL_REPLY_TO_TWEET_TID));
			if (replyToTweetId != 0) {
				loadPreviousConversationTweet(replyToTweetId);
			}
		}
	}

	/**
	 * Tries to load the tweet with the given id. If it is not found in the
	 * content provider, we send a synch intent to load it from twitter and
	 * register a content observer to get notified when it is loaded. If it is
	 * found in the content provider, a view is created and added to the layout.
	 * If the loaded tweet is also a reply to another tweet, the method calls
	 * itself to repeat the process for that other tweet and so forth.
	 * 
	 * @param tid
	 *            the twitter ID of the tweet to load
	 */
	private void loadPreviousConversationTweet(long tid) {
		Uri uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEET_TID + "/"
				+ tid);
		Cursor c = getContext().getContentResolver().query(uri, null, null, null, null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			addPreviousConversationTweet(c);
			long replyToTweetId = c.getLong(c.getColumnIndex(Tweets.COL_REPLY_TO_TWEET_TID));
			if (replyToTweetId != 0) {
				loadPreviousConversationTweet(replyToTweetId);
			}
			c.close();
		} else {
			Intent loadTweetIntent = new Intent(getContext(), TwitterSyncService.class);
			loadTweetIntent.putExtra(TwitterSyncService.EXTRA_KEY_ACTION,
					TwitterSyncService.EXTRA_ACTION_LOAD_TWEET_BY_TID);
			loadTweetIntent.putExtra(TwitterSyncService.EXTRA_KEY_TWEET_TID, tid);
			getContext().startService(loadTweetIntent);
			mTweetLoadedObserver = new ConversationTweetContentObserver(new Handler(), tid, c);
			c.registerContentObserver(mTweetLoadedObserver);
		}
	}

	/**
	 * Inflates a layout for the tweet in the given cursor and add it to the
	 * layout.
	 * 
	 * @param c
	 *            a cursor that is initialized to point at the tweet to be added
	 */
	private synchronized void addPreviousConversationTweet(Cursor c) {
		TweetView tweetView = new TweetView(getContext());
		tweetView.update(c, false, false, true);
		long rowId = c.getLong(c.getColumnIndex(Tweets.COL_ROW_ID));
		tweetView.setOnClickListener(new ConversationTweetClickListener(rowId));
		tweetView.setBackgroundResource(R.drawable.borderless_button_background_faded);
		int index = mListView.getFirstVisiblePosition();
		View topView = mListView.getChildAt(0);
		int top = (topView == null) ? 0 : topView.getTop();

		mTweetViews.add(0, tweetView);
		mListAdapter.notifyDataSetChanged();

		mListView.setSelectionFromTop(index + 1, top);
		mListView.smoothScrollBy(-20, 500);
	}

	/**
	 * A click listener to launch a new TweetDetailActivity when a tweet in the
	 * previous conversation is clicked.
	 * 
	 * @author Steven Meliopoulos
	 * 
	 */
	private class ConversationTweetClickListener implements OnClickListener {
		private final long mRowId;

		public ConversationTweetClickListener(long rowId) {
			mRowId = rowId;
		}

		@Override
		public void onClick(View v) {
			Intent i = new Intent(getContext(), TweetDetailActivity.class);
			i.putExtra(TweetDetailActivity.EXTRA_KEY_ROW_ID, mRowId);
			i.putExtra(TweetDetailActivity.EXTRA_KEY_CONTEXT, TweetDetailActivity.EXTRA_CONTEXT_SINGLE_TWEET);
			getContext().startActivity(i);
		}

	}

	/**
	 * A content observer to continue the process of loading the conversation
	 * after a missing tweet is loaded.
	 * 
	 * @author Steven Meliopoulos
	 * 
	 */
	class ConversationTweetContentObserver extends ContentObserver {
		private final long mTid;
		private final Cursor mCursor;
		private boolean mHasBeenCalled = false;

		public ConversationTweetContentObserver(Handler h, long tid, Cursor cursor) {
			super(h);
			mTid = tid;
			mCursor = cursor;
		}

		@Override
		public boolean deliverSelfNotifications() {
			return false;
		}

		@Override
		public synchronized void onChange(boolean selfChange) {
			if (!mHasBeenCalled) {
				mHasBeenCalled = true;
				mCursor.unregisterContentObserver(this);
				mCursor.close();
				loadPreviousConversationTweet(mTid);
			}
		}
	}

	/**
	 * An adapter that serves the tweet views to the ListView.
	 * 
	 * @author Steven Meliopoulos
	 * 
	 */
	private class ConversationAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return mTweetViews.size();
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = mTweetViews.get(position);
			return view;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

	}

}
