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
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.DmAdapter;
import ch.ethz.twimight.net.twitter.DirectMessages;
import ch.ethz.twimight.net.twitter.Tweets;
import android.view.ActionMode;

/**
 * Shows the overview of direct messages. A list view with an item for each user
 * with which we have exchanged direct messages.
 * 
 * @author thossmann
 * 
 */
public class DmListActivity extends TwimightBaseActivity implements
		ActionMode.Callback {

	private static final String TAG = "ShowDMListActivity";

	// Views
	private ListView dmUserListView;

	private DmAdapter adapter;
	private Cursor c;

	private int rowId;
	private String screenname;
	public static boolean running = false;
	int mSelectedPosition;

	// handler
	static Handler handler;

	private int positionIndex;
	private int positionTop;

	private ActionMode mActionMode;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.dm_list);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		rowId = getIntent().getIntExtra("rowId", 0);
		screenname = getIntent().getStringExtra("screenname");

		// If we don't know which user to show, we stop the activity
		if (rowId == 0 || screenname == null)
			finish();

		setTitle(getString(R.string.direct_messages));
		getActionBar()
				.setSubtitle(getString(R.string.with) + " @" + screenname);

		dmUserListView = (ListView) findViewById(R.id.dmUserList);
		c = getContentResolver().query(
				Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/"
						+ DirectMessages.DMS + "/" + DirectMessages.DMS_USER
						+ "/" + rowId), null, null, null, null);

		adapter = new DmAdapter(this, c);
		dmUserListView.setAdapter(adapter);
		dmUserListView.setEmptyView(findViewById(R.id.dmListEmpty));
		dmUserListView
				.setOnItemLongClickListener(new OnItemLongClickListener() {

					@Override
					public boolean onItemLongClick(AdapterView<?> parent,
							View view, int position, long id) {
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

		if (positionIndex != 0 | positionTop != 0) {
			dmUserListView.setSelectionFromTop(positionIndex, positionTop);
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

		dmUserListView.setAdapter(null);

		if (c != null)
			c.close();

		unbindDrawables(findViewById(R.id.showDMUserListRoot));

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		if (item.getItemId() == R.id.menu_write_tweet) {
			Intent i = new Intent(getBaseContext(), ComposeDmActivity.class);
			i.putExtra("recipient", screenname);
			startActivity(i);

		} else
			super.onOptionsItemSelected(item);

		return true;

	}

	/**
	 * Saves the current selection
	 */
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {

		positionIndex = dmUserListView.getFirstVisiblePosition();
		View v = dmUserListView.getChildAt(0);
		positionTop = (v == null) ? 0 : v.getTop();
		savedInstanceState.putInt("positionIndex", positionIndex);
		savedInstanceState.putInt("positionTop", positionTop);

		Log.i(TAG, "saving" + positionIndex + " " + positionTop);

		super.onSaveInstanceState(savedInstanceState);
	}

	/**
	 * Loads the current user selection
	 */
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);

		positionIndex = savedInstanceState.getInt("positionIndex");
		positionTop = savedInstanceState.getInt("positionTop");

		Log.i(TAG, "restoring " + positionIndex + " " + positionTop);
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
		c.moveToPosition(mSelectedPosition);
		int flags = c.getInt(c.getColumnIndex(DirectMessages.COL_FLAGS));
		Long tid = c.getLong(c.getColumnIndex(DirectMessages.COL_DMID));
		Long rowId = c.getLong(c.getColumnIndex("_id"));
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
		builder.setMessage(
				"Are you sure you want to delete your Direct Message?")
				.setCancelable(false)
				.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								Uri uri = Uri.parse("content://"
										+ DirectMessages.DM_AUTHORITY + "/"
										+ DirectMessages.DMS + "/" + rowId);

								if (tid != 0) {
									c.moveToPosition(mSelectedPosition);
									int flags = c.getInt(c
											.getColumnIndex(DirectMessages.COL_FLAGS));
									getContentResolver().update(uri,
											setDeleteFlag(flags), null, null);
								} else {
									getContentResolver()
											.delete(uri, null, null);
								}

							}
						})
				.setNegativeButton("No", new DialogInterface.OnClickListener() {
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
