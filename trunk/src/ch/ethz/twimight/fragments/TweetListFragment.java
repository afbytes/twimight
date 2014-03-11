package ch.ethz.twimight.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CursorAdapter;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.views.PullToRefreshListView;

public abstract class TweetListFragment extends ListFragment {

	public static final String USER_ID = "USER_ID";
	
	private PullToRefreshListView mPullToRefreshListView;

	public TweetListFragment() {

	};

	public TweetListFragment(int type) {
		this.mType = type;
		Log.i(TAG, "creating instance of tweet list frag");
	}

	abstract Intent getTweetClickIntent(long rowId);
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		mPullToRefreshListView = (PullToRefreshListView) super.onCreateView(inflater,
				container, savedInstanceState);
		// Click listener when the user clicks on a tweet
		mPullToRefreshListView.setClickable(true);
		mPullToRefreshListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Cursor c = (Cursor) parent.getItemAtPosition(position);
				long rowId = c.getLong(c.getColumnIndex(Tweets.COL_ROW_ID));
				Intent i = getTweetClickIntent(rowId);
				startActivity(i);
			}
		});
		
		mPullToRefreshListView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				Log.d(TAG, "long click id: " + id);
				((TweetAdapter)mListAdapter).setSelectedId(id);
				return true;
			}
		});

		return mPullToRefreshListView;
	}
	
	PullToRefreshListView getListView(){
		return mPullToRefreshListView;
	}
	
	@Override
	CursorAdapter getListAdapter() {
		return new TweetAdapter(getActivity(), null);
	}
	
}
