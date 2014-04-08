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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.DmConversationAdapter;
import ch.ethz.twimight.net.twitter.DirectMessages;
import ch.ethz.twimight.net.twitter.NotificationService;
import ch.ethz.twimight.net.twitter.TwitterUsers;

/**
 * Shows the overview of direct messages. A list view with an item for each user
 * with which we have exchanged direct messages.
 * 
 * @author thossmann
 * 
 */
public class DmConversationListActivity extends TwimightBaseActivity {

	private static final String TAG = DmConversationListActivity.class.getSimpleName();

	// Views
	private ListView dmUsersListView;

	private DmConversationAdapter mAdapter;
	private Cursor mCursor;
	public static boolean running = false;

	// handler
	static Handler mHandler;

	private int mPositionIndex;
	private int mPositionTop;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.dm_conversation_list);

		dmUsersListView = (ListView) findViewById(R.id.dmUsersList);
		mCursor = getContentResolver().query(
				Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
						+ DirectMessages.DMS_USERS), null, null, null, null);

		Log.e(TAG, "Users: " + mCursor.getCount());

		mAdapter = new DmConversationAdapter(this, mCursor);
		dmUsersListView.setAdapter(mAdapter);
		dmUsersListView.setEmptyView(findViewById(R.id.dmListEmpty));
		// Click listener when the user clicks on a user
		dmUsersListView.setClickable(true);
		dmUsersListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Cursor c = (Cursor) dmUsersListView.getItemAtPosition(position);
				Intent i = new Intent(getBaseContext(), DmListActivity.class);
				i.putExtra(DmListActivity.EXTRA_KEY_USER_ROW_ID, c.getInt(c.getColumnIndex(TwitterUsers.COL_ROW_ID)));
				i.putExtra(DmListActivity.EXTRA_KEY_SCREEN_NAME, c.getString(c.getColumnIndex(TwitterUsers.COL_SCREEN_NAME)));
				startActivity(i);
			}
		});

	}

	/**
	 * On resume
	 */
	@Override
	public void onResume() {
		super.onResume();
		running = true;
		markDirectMessagesSeen();
		if (mPositionIndex != 0 | mPositionTop != 0) {
			dmUsersListView.setSelectionFromTop(mPositionIndex, mPositionTop);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		markDirectMessagesSeen();
	}

	@Override
	protected void onStop() {
		running = false;
		super.onStop();
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();

		dmUsersListView.setOnItemClickListener(null);
		dmUsersListView.setAdapter(null);

		if (mCursor != null)
			mCursor.close();

		unbindDrawables(findViewById(R.id.showDMUsersListRoot));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_write_tweet) {
			startActivity(new Intent(getBaseContext(), ComposeDmActivity.class));
		} else {
			super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void markDirectMessagesSeen() {
		Intent timelineSeenIntent = new Intent(this, NotificationService.class);
		timelineSeenIntent.putExtra(NotificationService.EXTRA_KEY_ACTION,
				NotificationService.ACTION_MARK_DIRECT_MESSAGES_SEEN);
		startService(timelineSeenIntent);
	}

	/**
	 * Saves the current selection
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

		mPositionIndex = dmUsersListView.getFirstVisiblePosition();
		View v = dmUsersListView.getChildAt(0);
		mPositionTop = (v == null) ? 0 : v.getTop();
		savedInstanceState.putInt("positionIndex", mPositionIndex);
		savedInstanceState.putInt("positionTop", mPositionTop);

		Log.i(TAG, "saving" + mPositionIndex + " " + mPositionTop);

		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Loads the current user selection
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		mPositionIndex = savedInstanceState.getInt("positionIndex");
		mPositionTop = savedInstanceState.getInt("positionTop");

		Log.i(TAG, "restoring " + mPositionIndex + " " + mPositionTop);
	}

}
