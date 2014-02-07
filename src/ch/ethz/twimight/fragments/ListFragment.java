package ch.ethz.twimight.fragments;

import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.TwitterSyncService;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.views.PullToRefreshListView;
import ch.ethz.twimight.views.PullToRefreshListView.PullToRefreshListener;

public abstract class ListFragment extends Fragment implements PullToRefreshListener {

	Intent overscrollIntent;
	int mType;
	ContentResolver mResolver;
	CursorAdapter mListAdapter;
	Cursor mCursor;

	PullToRefreshListView mListView;
	protected static final String TAG = ListFragment.class.getName();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mResolver = getActivity().getContentResolver();
	}

	void updateList() {
		mCursor = getCursor();
		overscrollIntent = getOverscrollIntent();
		Cursor oldCursor = mListAdapter.swapCursor(mCursor);
		mListAdapter.notifyDataSetChanged();
		if (oldCursor != null) {
			oldCursor.close();
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// Inflate the layout for this fragment
		View view = inflater.inflate(R.layout.fragment_layout, container, false);
		mListView = (PullToRefreshListView) view.findViewById(R.id.tweetListView);

		mListView.registerListener(this);

		mListAdapter = getListAdapter();
		mListView.setAdapter(mListAdapter);
		updateList();
		return mListView;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		mListView.unregisterListener(this);
	}

	abstract Cursor getCursor();
	abstract Intent getOverscrollIntent();

	abstract CursorAdapter getListAdapter();

	@Override
	public void onTopRefresh() {
		if (overscrollIntent != null) {
			overscrollIntent.putExtra(TwitterSyncService.EXTRA_TIMELINE_UPDATE_DIRECTION,
					TwitterSyncService.TIMELINE_UPDATE_DIRECTION_UP);
//			if (Constants.TIMELINE_BUFFER_SIZE >= 150) {
//				Constants.TIMELINE_BUFFER_SIZE -= 50;
//			}
			Log.i(TAG, "BUFFER_SIZE =  " + Constants.TIMELINE_BUFFER_SIZE);
		}
		Log.d(TAG, "ListFragment sending TimelineSyncService intent dir=UP");
		getActivity().startService(overscrollIntent);
	}

	@Override
	public void onBottomRefresh() {
		if (overscrollIntent != null) {
			overscrollIntent.putExtra(TwitterSyncService.EXTRA_TIMELINE_UPDATE_DIRECTION,
					TwitterSyncService.TIMELINE_UPDATE_DIRECTION_DOWN);
//			if (Constants.TIMELINE_BUFFER_SIZE >= 150) {
//				Constants.TIMELINE_BUFFER_SIZE -= 50;
//			}
			Log.i(TAG, "BUFFER_SIZE =  " + Constants.TIMELINE_BUFFER_SIZE);
		}
		Log.d(TAG, "ListFragment sending TimelineSyncService intent dir=DOWN");
		getActivity().startService(overscrollIntent);
	}

}
