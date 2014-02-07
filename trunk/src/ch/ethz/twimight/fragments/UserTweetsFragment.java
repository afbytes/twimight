package ch.ethz.twimight.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import ch.ethz.twimight.activities.TweetDetailActivity;
import ch.ethz.twimight.net.twitter.Tweets;

public class UserTweetsFragment extends TweetListFragment {

	private long mUserId;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mUserId = getArguments().getLong(USER_ID);
	}

	@Override
	Intent getTweetClickIntent(long rowId) {
		Intent intent = new Intent(getActivity(), TweetDetailActivity.class);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_ROW_ID, rowId);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_CONTEXT, TweetDetailActivity.EXTRA_CONTEXT_USER);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_USER_ID, mUserId);
		return intent;
	}

	@Override
	Cursor getCursor() {
		Cursor cursor = mResolver.query(
				Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_USER
						+ "/" + mUserId), null, null, null, null);
		return cursor;
	}

	@Override
	Intent getOverscrollIntent() {
		return null;
	}

}
