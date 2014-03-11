package ch.ethz.twimight.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ch.ethz.twimight.activities.SearchableActivity;
import ch.ethz.twimight.activities.TweetDetailActivity;
import ch.ethz.twimight.net.twitter.Tweets;

public class SearchTweetsFragment extends TweetListFragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = super.onCreateView(inflater, container, savedInstanceState);
		getListView().setOverscrollEnabled(false);
		return view;
	}
	
	@Override
	Intent getTweetClickIntent(long rowId) {
		Intent intent = new Intent(getActivity(), TweetDetailActivity.class);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_ROW_ID, rowId);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_CONTEXT, TweetDetailActivity.EXTRA_CONTEXT_SEARCH);
		return intent;
	}

	@Override
	Cursor getCursor() {
		Cursor cursor = mResolver.query(
				Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + Tweets.SEARCH), null,
				SearchableActivity.mQuery, null, null);
		return cursor;
	}

	@Override
	Intent getOverscrollIntent() {
		return null;
	}

	public void notifyNewQuery() {
		updateList();
	}

}
