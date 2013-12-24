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
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.DBOpenHelper;
import ch.ethz.twimight.util.AsyncImageLoader;

/**
 * Cursor adapter for a cursor containing users.
 */
public class UserAdapter extends CursorAdapter {

	private final AsyncImageLoader mImageLoader;

	private static class ViewHolder {
		private final TextView tvUserRealName;
		private final TextView tvUserScreenName;
		private final TextView tvUserLocation;
		private final ImageView ivProfileImage;

		private ViewHolder(View row) {
			tvUserRealName = (TextView) row.findViewById(R.id.showUserRealName);
			tvUserScreenName = (TextView) row.findViewById(R.id.showUserScreenName);
			tvUserLocation = (TextView) row.findViewById(R.id.showUserLocation);
			ivProfileImage = (ImageView) row.findViewById(R.id.showUserProfileImage);
		}
	}

	/** Constructor */
	public UserAdapter(Context context, Cursor c) {
		super(context, c, true);
		mImageLoader = new AsyncImageLoader(context);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) parent.getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View row = inflater.inflate(R.layout.user_row, null);
		ViewHolder viewHolder = new ViewHolder(row);
		row.setTag(viewHolder);
		return row;
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		ViewHolder holder = (ViewHolder) row.getTag();
		// set real name
		String realName = cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME));
		holder.tvUserRealName.setText(realName);
		// set screen name
		String screenName = "@" + cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_SCREENNAME));
		holder.tvUserScreenName.setText(screenName);
		// set location
		String location = cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_LOCATION));
		holder.tvUserLocation.setText(location);
		// profile image
		holder.ivProfileImage.setBackgroundResource(R.drawable.profile_image_placeholder);
		holder.ivProfileImage.setImageDrawable(null);
		if (!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH))) {
			int userRowId = cursor.getInt(cursor.getColumnIndex(DBOpenHelper.COL_ROW_ID));
			Uri imageUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS + "/" + userRowId);
			mImageLoader.loadImage(imageUri, holder.ivProfileImage);
		}
	}

}
