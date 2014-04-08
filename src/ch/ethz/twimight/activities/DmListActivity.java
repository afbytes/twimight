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

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.DirectMessages;
import ch.ethz.twimight.net.twitter.DmAdapter;
import ch.ethz.twimight.net.twitter.Tweets;

/**
 * Shows the overview of direct messages. A list view with an item for each user
 * with which we have exchanged direct messages.
 * 
 * @author thossmann
 * 
 */
public class DmListActivity extends TwimightBaseActivity implements ActionMode.Callback {

	private static final String TAG = DmListActivity.class.getSimpleName();

	public static final String EXTRA_KEY_USER_ROW_ID = "EXTRA_KEY_USER_ROW_ID";
	public static final String EXTRA_KEY_SCREEN_NAME = "EXTRA_KEY_SCREEN_NAME";

	// Views
	private ListView mListView;

	private DmAdapter mAdapter;
	private Cursor mCursor;

	private int mUserRowId;
	private String mScreenname;
	public static boolean running = false;
	int mSelectedPosition;

	// handler
	static Handler mHandler;

	private int mPositionIndex;
	private int mPositionTop;

	private ActionMode mActionMode;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.dm_list);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		mUserRowId = getIntent().getIntExtra(EXTRA_KEY_USER_ROW_ID, 0);
		mScreenname = getIntent().getStringExtra(EXTRA_KEY_SCREEN_NAME);

		// If we don't know which user to show, we stop the activity
		if (mUserRowId == 0 || mScreenname == null) {
			finish();
		}

		setTitle(getString(R.string.direct_messages));
		getActionBar().setSubtitle(getString(R.string.with) + " @" + mScreenname);

		mListView = (ListView) findViewById(R.id.dmUserList);
		mCursor = getContentResolver().query(
				Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
						+ DirectMessages.DMS_USER + "/" + mUserRowId), null, null, null, null);

		mAdapter = new DmAdapter(this, mCursor);
		mListView.setAdapter(mAdapter);
		mListView.setEmptyView(findViewById(R.id.dmListEmpty));
		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {

			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				mSelectedPosition = position;
				mActionMode = startActionMode(DmListActivity.this);
				view.setSelected(true);
				return true;
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

		if (mPositionIndex != 0 | mPositionTop != 0) {
			mListView.setSelectionFromTop(mPositionIndex, mPositionTop);
		}
	}

	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		running = false;
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();

		mListView.setAdapter(null);

		if (mCursor != null)
			mCursor.close();

		unbindDrawables(findViewById(R.id.showDMUserListRoot));

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_write_tweet:
			Intent i = new Intent(getBaseContext(), ComposeDmActivity.class);
			i.putExtra(ComposeDmActivity.EXTRA_KEY_RECIPIENT_SCREEN_NAME, mScreenname);
			startActivity(i);
			break;
		case android.R.id.home:
			NavUtils.navigateUpFromSameTask(this);
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	/**
	 * Saves the current selection
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

		mPositionIndex = mListView.getFirstVisiblePosition();
		View v = mListView.getChildAt(0);
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

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		switch (item.getItemId()) {
		case R.id.dm_context_delete:
			deleteSelectedMessage();
			mActionMode.finish();
			return true;
		default:
			return false;
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		MenuInflater inflater = mode.getMenuInflater();
		inflater.inflate(R.menu.dm_context, menu);
		return true;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		mActionMode = null;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false; // nothing is done
	}

	private void deleteSelectedMessage() {
		mCursor.moveToPosition(mSelectedPosition);
		int flags = mCursor.getInt(mCursor.getColumnIndex(DirectMessages.COL_FLAGS));
		Long tid = mCursor.getLong(mCursor.getColumnIndex(DirectMessages.COL_DMID));
		Long rowId = mCursor.getLong(mCursor.getColumnIndex("_id"));
		if ((flags & Tweets.FLAG_TO_DELETE) == 0) {

			if (tid != null) {
				Log.i(TAG, "msg was published online");
				showDeleteDialog(tid, rowId);
			} else {
				Log.i(TAG, "msg was NOT published online");
				showDeleteDialog(0, rowId);
			}
		}
	}

	/**
	 * Asks the user if she wants to delete a dm.
	 */
	private void showDeleteDialog(final long tid, final long rowId) {

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to delete your Direct Message?").setCancelable(false)
				.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						Uri uri = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
								+ rowId);

						if (tid != 0) {
							mCursor.moveToPosition(mSelectedPosition);
							int flags = mCursor.getInt(mCursor.getColumnIndex(DirectMessages.COL_FLAGS));
							getContentResolver().update(uri, setDeleteFlag(flags), null, null);
						} else {
							getContentResolver().delete(uri, null, null);
						}

					}
				}).setNegativeButton("No", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Adds the delete flag and returns the flags in a content value structure
	 * to send to the content provider
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues setDeleteFlag(final int flags) {
		ContentValues cv = new ContentValues();
		cv.put(DirectMessages.COL_FLAGS, flags | DirectMessages.FLAG_TO_DELETE);
		return cv;
	}
}
