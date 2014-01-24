/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/

package ch.ethz.twimight.net.twitter;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import ch.ethz.twimight.util.AsyncImageLoader;
import ch.ethz.twimight.views.TweetView;

/**
 * Cursor adapter for a cursor containing tweets.
 */
public class TweetAdapter extends CursorAdapter {

	private static final String TAG = TweetAdapter.class.getName();
	private static final long NO_ITEM_SELECTED = -1;


	private final AsyncImageLoader mImageLoader;


	private long mSelectedId;

	public void setSelectedId(long selectedId) {
		if (selectedId != mSelectedId) {
			mSelectedId = selectedId;
			notifyDataSetChanged();
		} else if (selectedId!=NO_ITEM_SELECTED){
			clearSelectedId();
		}
	}

	public void clearSelectedId() {
		setSelectedId(NO_ITEM_SELECTED);
	}

	/** Constructor */
	public TweetAdapter(Context context, Cursor c) {
		super(context, c, true);
		mImageLoader = new AsyncImageLoader(context);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		return new TweetView(context);
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		TweetView tweetView = (TweetView) row;
		long rowId = cursor.getLong(cursor.getColumnIndex(Tweets.COL_ROW_ID));
		boolean showButtonBar = rowId==mSelectedId;
		tweetView.update(cursor, showButtonBar, true, mImageLoader);
	}
}
