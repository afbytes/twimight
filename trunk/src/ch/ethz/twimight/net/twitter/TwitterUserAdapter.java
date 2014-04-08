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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import ch.ethz.twimight.R;

/**
 * Cursor adapter for a cursor containing users.
 */
public class TwitterUserAdapter extends CursorAdapter {
	static final String[] from = { TwitterUsers.COL_SCREEN_NAME,
			TwitterUsers.COL_NAME, TwitterUsers.COL_LOCATION };
	static final int[] to = { R.id.tvUserScreenName, R.id.tvUserRealName,
			R.id.tvUserLocation };

	/** Constructor */
	public TwitterUserAdapter(Context context, Cursor c) {
		super(context, c, true);
	}

	private static class ViewHolder {
		ImageView picture;
		TextView realName;
		TextView screenName;
		TextView location;
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		// TODO Auto-generated method stub

		LayoutInflater inflater = (LayoutInflater) parent.getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.user_row, null);
		createHolder(view);

		return view;

	}

	private void createHolder(View view) {
		ViewHolder holder = new ViewHolder();
		setHolderFields(view, holder);
		view.setTag(holder);
	}

	private void setHolderFields(View userrow, ViewHolder holder) {
		holder.picture = (ImageView) userrow
				.findViewById(R.id.showUserProfileImage);
		holder.realName = (TextView) userrow
				.findViewById(R.id.tvUserRealName);
		holder.screenName = (TextView) userrow
				.findViewById(R.id.tvUserScreenName);
		holder.location = (TextView) userrow
				.findViewById(R.id.tvUserLocation);

	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View userrow, Context context, Cursor cursor) {
		ViewHolder holder = (ViewHolder) userrow.getTag();
		if (!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_SCREEN_NAME))) {
			// set text fields
			String realName = cursor.getString(cursor
					.getColumnIndex(TwitterUsers.COL_NAME));
			holder.realName.setText(realName);
			String screenName = cursor.getString(cursor
					.getColumnIndex(TwitterUsers.COL_SCREEN_NAME));
			holder.screenName.setText("@" + screenName);
			String location = cursor.getString(cursor
					.getColumnIndex(TwitterUsers.COL_LOCATION));
			holder.location.setText(location);
			// set image
			int userId = cursor.getInt(cursor.getColumnIndex("_id"));
			Uri imageUri = Uri.parse("content://"
					+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS + "/" + userId);
			InputStream is;

			try {
				is = context.getContentResolver().openInputStream(imageUri);
				if (is != null) {
					Bitmap bm = BitmapFactory.decodeStream(is);
					holder.picture.setImageBitmap(bm);
				} else
					holder.picture
							.setImageResource(R.drawable.profile_image_placeholder);
			} catch (FileNotFoundException e) {
				// Log.e(TAG,"error opening input stream");
				holder.picture
						.setImageResource(R.drawable.profile_image_placeholder);
			}

		} else {
			holder.picture
					.setImageResource(R.drawable.profile_image_placeholder);
		}

	}

}
