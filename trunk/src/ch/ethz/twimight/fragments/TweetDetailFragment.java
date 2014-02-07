/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. activity program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies activity distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/
package ch.ethz.twimight.fragments;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import ch.ethz.twimight.R;
import ch.ethz.twimight.views.TweetButtonBar;
import ch.ethz.twimight.views.TweetConversationView;

/**
 * Dispays the details of a tweet. Everything is delegated to the contained
 * TweetConversationView and TweetButtonBar. They only need to be initialized
 * with the row id of the regarding tweet.
 * 
 * @author Steven Meliopoulos
 * 
 */
public class TweetDetailFragment extends Fragment {

	private static final String ARG_KEY_ROWID = "ARG_KEY_ROWID";

	private long mRowId;

	private View mView;

	public static TweetDetailFragment newInstance(long rowId) {
		TweetDetailFragment instance = new TweetDetailFragment();
		Bundle args = new Bundle();
		args.putLong(ARG_KEY_ROWID, rowId);
		instance.setArguments(args);
		return instance;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		mView = inflater.inflate(R.layout.tweet_detail_fragment, container, false);

		TweetConversationView tweetConversationView = (TweetConversationView) mView.findViewById(R.id.conversationView);
		tweetConversationView.setRowId(mRowId);

		TweetButtonBar tweetButtonBar = (TweetButtonBar) mView.findViewById(R.id.tweetButtonBar);
		tweetButtonBar.setRowId(mRowId);

		return mView;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		// if we are creating a new instance, get row id from arguments,
		// otherwise from saved instance state
		if (savedInstanceState == null) {
			mRowId = getArguments().getLong(ARG_KEY_ROWID);
		} else {
			mRowId = savedInstanceState.getLong(ARG_KEY_ROWID);
		}
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putLong(ARG_KEY_ROWID, mRowId);
	}

}
