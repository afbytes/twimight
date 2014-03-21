package ch.ethz.twimight.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import ch.ethz.twimight.activities.TweetDetailActivity;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterSyncService;

public class TimelineFragment extends TweetListFragment {

	@Override
	Cursor getCursor() {
		Cursor cursor = mResolver.query(
				Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
						+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
		return cursor;
	}

	Intent getOverscrollIntent() {
		Intent overscrollIntent = new Intent(getActivity(), TwitterSyncService.class);
		overscrollIntent = new Intent(getActivity(), TwitterSyncService.class);
		overscrollIntent.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_TIMELINE);
		overscrollIntent.putExtra(TwitterSyncService.EXTRA_KEY_FORCE_SYNC, true);
		return overscrollIntent;
	}

	Intent getTweetClickIntent(long rowId) {
		Intent intent = new Intent(getActivity(), TweetDetailActivity.class);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_ROW_ID, rowId);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_CONTEXT, TweetDetailActivity.EXTRA_CONTEXT_TIMELINE);
		return intent;
	}

}
