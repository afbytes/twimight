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
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.net.twitter.TwitterSyncService.TimelineSyncService;
import ch.ethz.twimight.ui.PullToRefreshListView;
import ch.ethz.twimight.ui.PullToRefreshListView.PullToRefreshListener;
import ch.ethz.twimight.util.Constants;

public abstract class ListFragment extends Fragment implements PullToRefreshListener {

	Intent overscrollIntent;
	int mType;
	ContentResolver mResolver;
	CursorAdapter mListAdapter;
	Cursor mCursor;

	PullToRefreshListView mListView;
	protected static final String TAG = "ListFragment";

	public static final String FRAGMENT_TYPE = "fragment_type";
	public static final String SEARCH_QUERY = "search_query";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mResolver = getActivity().getContentResolver();
	}

	private void updateList() {
		mCursor = getCursor(mType);
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

	public void newQueryText() {
		// Called when the action bar search text has changed. Update
		// the search filter
		updateList();
		setActionBarTitles();
	}

	abstract Cursor getCursor(int filter);
	
	abstract void setActionBarTitles();

	abstract CursorAdapter getListAdapter();
	
	@Override
	public void onTopRefresh() {
		if (overscrollIntent != null) {
			overscrollIntent.putExtra(TimelineSyncService.EXTRA_UPDATE_DIRECTION, TimelineSyncService.UPDATE_DIRECTION_UP);
			if (Constants.TIMELINE_BUFFER_SIZE >= 150)
				Constants.TIMELINE_BUFFER_SIZE -= 50;
			Log.i(TAG, "BUFFER_SIZE =  " + Constants.TIMELINE_BUFFER_SIZE);
		}
		getActivity().startService(overscrollIntent);

	}

	@Override
	public void onBottomRefresh() {
		if (overscrollIntent != null) {
			overscrollIntent.putExtra(TimelineSyncService.EXTRA_UPDATE_DIRECTION, TimelineSyncService.UPDATE_DIRECTION_DOWN);
			if (Constants.TIMELINE_BUFFER_SIZE >= 150){
				Constants.TIMELINE_BUFFER_SIZE -= 50;
			}
			Log.i(TAG, "BUFFER_SIZE =  " + Constants.TIMELINE_BUFFER_SIZE);
		}
		getActivity().startService(overscrollIntent);
	}

}
