package ch.ethz.twimight.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import ch.ethz.twimight.activities.TweetDetailActivity;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterSyncService;

public class FavoritesFragment extends TweetListFragment {

	@Override
	Intent getTweetClickIntent(long rowId) {
		Intent intent = new Intent(getActivity(), TweetDetailActivity.class);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_ROW_ID, rowId);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_CONTEXT, TweetDetailActivity.EXTRA_CONTEXT_FAVORITES);
		return intent;
	}

	@Override
	Cursor getCursor() {
		Cursor cursor = mResolver
				.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY
						+ "/" + Tweets.TWEETS + "/"
						+ Tweets.TWEETS_TABLE_FAVORITES + "/"
						+ Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
		return cursor;
	}

	@Override
	Intent getOverscrollIntent() {
		Intent overscrollIntent = new Intent(getActivity(), TwitterSyncService.class);
		overscrollIntent.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_FAVORITES);
		overscrollIntent.putExtra(TwitterSyncService.EXTRA_FORCE_SYNC, true);
		return overscrollIntent;
	}

}
