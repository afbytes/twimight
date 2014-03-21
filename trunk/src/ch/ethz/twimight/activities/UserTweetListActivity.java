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
package ch.ethz.twimight.activities;

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.os.Bundle;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.TweetListFragment;
import ch.ethz.twimight.fragments.UserTweetsFragment;
import ch.ethz.twimight.net.twitter.TwitterUsers;

/**
 * Shows the most recent tweets of a user
 * 
 * @author thossmann
 * 
 */
public class UserTweetListActivity extends TwimightBaseActivity {

	public static final String EXTRA_KEY_USER_ROW_ID = "EXTRA_KEY_USER_ROW_ID";

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!getIntent().hasExtra(EXTRA_KEY_USER_ROW_ID)) {
			finish();
		}

		setContentView(R.layout.main);

		long userId = getIntent().getLongExtra(EXTRA_KEY_USER_ROW_ID, TwitterUsers.NO_ROW_ID);
		
		TweetListFragment userTweetFragment = new UserTweetsFragment();
		Bundle bundle = new Bundle();
		bundle.putLong(TweetListFragment.USER_ID, userId);
		userTweetFragment.setArguments(bundle);

		FragmentManager fragmentManager = getFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.add(R.id.rootRelativeLayout, userTweetFragment);
		fragmentTransaction.commit();
	}

}
