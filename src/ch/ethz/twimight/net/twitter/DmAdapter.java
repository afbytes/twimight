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

import java.io.FileNotFoundException;
import java.io.InputStream;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;

/**
 * Cursor adapter for a cursor containing users.
 */
public class DmAdapter extends CursorAdapter {

	Context context;
	int flags;

	public static final String TAG = "DMAdapter";

	/** Constructor */
	public DmAdapter(Context context, Cursor c) {
		super(context, c, true);
		this.context = context;
	}

	private static class ViewHolder {
		View modeStripe;
		TextView tvScreenname;
		TextView tvCreatedAt;
		TextView tvMessageText;
		ImageView ivProfileImage;
		ImageView ivPendingIcon;

		private ViewHolder(View row) {
			modeStripe = row.findViewById(R.id.modeStripe);
			ivProfileImage = (ImageView) row
					.findViewById(R.id.showDMProfileImage);
			tvScreenname = (TextView) row.findViewById(R.id.showDMScreenName);
			tvCreatedAt = (TextView) row.findViewById(R.id.dmCreatedAt);
			tvMessageText = (TextView) row.findViewById(R.id.showDMText);
			ivPendingIcon = (ImageView) row.findViewById(R.id.dmToPost);
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
		holder.tvScreenname.setText(screenname);

		// Find views by id
		long createdAt = cursor.getLong(cursor
				.getColumnIndex(DirectMessages.COL_CREATED));
		holder.tvCreatedAt.setText(DateUtils
				.getRelativeTimeSpanString(createdAt));

		String message = cursor.getString(cursor
				.getColumnIndex(DirectMessages.COL_TEXT));
		holder.tvMessageText.setText(message);

		// Profile image
		if (!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_SCREEN_NAME))) {
			int userId = cursor.getInt(cursor.getColumnIndex(TweetsContentProvider.COL_USER_ROW_ID));
			Uri imageUri = Uri.parse("content://"
					+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS + "/" + userId);
			InputStream is;

			try {
				is = context.getContentResolver().openInputStream(imageUri);
				if (is != null) {
					Bitmap bm = BitmapFactory.decodeStream(is);
					holder.ivProfileImage.setImageBitmap(bm);
				} else
					holder.ivProfileImage
							.setImageResource(R.drawable.profile_image_placeholder);
			} catch (FileNotFoundException e) {
				Log.e(TAG, "error opening input stream", e);
				holder.ivProfileImage
						.setImageResource(R.drawable.profile_image_placeholder);
			}
		} else {
			holder.ivProfileImage
					.setImageResource(R.drawable.profile_image_placeholder);
		}

		// any transactional flags?
		flags = cursor.getInt(cursor.getColumnIndex(DirectMessages.COL_FLAGS));

		boolean toPost = (flags > 0);
		if (toPost) {
			holder.ivPendingIcon.setVisibility(View.VISIBLE);
		} else {
			holder.ivPendingIcon.setVisibility(View.GONE);
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
			holder.tvScreenname.setTextColor(accentColor);
		} else {
			holder.tvScreenname.setTextColor(context.getResources().getColor(
					R.color.dark_text));
		}

	}


}
