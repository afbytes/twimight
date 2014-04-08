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
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;

import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Cursor adapter for a cursor containing users.
 */
public class DmAdapter extends CursorAdapter {

	Context context;
	int flags;

	public static final String TAG = DmAdapter.class.getSimpleName();

	/** Constructor */
	public DmAdapter(Context context, Cursor c) {
		super(context, c, true);
		this.context = context;
	}

	private static class ViewHolder {
		View modeStripe;
		TextView mTvScreenname;
		TextView mTvCreatedAt;
		TextView mTvMessageText;
		ImageView mIvProfileImage;
		ImageView mIvPendingIcon;

		private ViewHolder(View row) {
			modeStripe = row.findViewById(R.id.modeStripe);
			mIvProfileImage = (ImageView) row
					.findViewById(R.id.showDMProfileImage);
			mTvScreenname = (TextView) row.findViewById(R.id.showDMScreenName);
			mTvCreatedAt = (TextView) row.findViewById(R.id.dmCreatedAt);
			mTvMessageText = (TextView) row.findViewById(R.id.showDMText);
			mIvPendingIcon = (ImageView) row.findViewById(R.id.dmToPost);
		}
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) parent.getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.dm_row, null);
		ViewHolder viewHolder = new ViewHolder(view);
		view.setTag(viewHolder);
		return view;
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(final View dmrow, Context context, Cursor cursor) {
		ViewHolder holder = (ViewHolder) dmrow.getTag();

		String screenname = cursor.getString(cursor
				.getColumnIndex(TwitterUsers.COL_SCREEN_NAME));
		holder.mTvScreenname.setText(screenname);

		// Find views by id
		long createdAt = cursor.getLong(cursor
				.getColumnIndex(DirectMessages.COL_CREATED));
		holder.mTvCreatedAt.setText(DateUtils
				.getRelativeTimeSpanString(createdAt));

		String message = cursor.getString(cursor
				.getColumnIndex(DirectMessages.COL_TEXT));
		holder.mTvMessageText.setText(message);

		// Profile image
		String profileImageUri = cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_PROFILE_IMAGE_URI));
		ImageLoader.getInstance().displayImage(profileImageUri, holder.mIvProfileImage);
		
		// any transactional flags?
		flags = cursor.getInt(cursor.getColumnIndex(DirectMessages.COL_FLAGS));

		boolean toPost = (flags > 0);
		if (toPost) {
			holder.mIvPendingIcon.setVisibility(View.VISIBLE);
		} else {
			holder.mIvPendingIcon.setVisibility(View.GONE);
		}

		// set color for own screenname and mode indicator stripe
		int accentColor;
		if (cursor.getInt(cursor.getColumnIndex(DirectMessages.COL_ISDISASTER)) > 0) {

			accentColor = context.getResources().getColor(
					R.color.accent_disastermode_2);
		} else {
			accentColor = context.getResources().getColor(
					R.color.accent_normalmode_2);
		}
		holder.modeStripe.setBackgroundColor(accentColor);
		boolean ownTweet = Long
				.toString(
						cursor.getLong(cursor
								.getColumnIndex(DirectMessages.COL_SENDER)))
				.equals(LoginActivity.getTwitterId(context));
		if (ownTweet) {
			holder.mTvScreenname.setTextColor(accentColor);
		} else {
			holder.mTvScreenname.setTextColor(context.getResources().getColor(
					R.color.dark_text));
		}

	}


}
