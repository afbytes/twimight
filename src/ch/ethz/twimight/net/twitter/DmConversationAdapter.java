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
public class DmConversationAdapter extends CursorAdapter {
	Context context;

	/** Constructor */
	public DmConversationAdapter(Context context, Cursor c) {
		super(context, c, true);
		this.context = context;
	}

	private static class ViewHolder {
		ImageView picture;
		TextView realName;
		TextView screenName;
		TextView lastMessage;

		private ViewHolder(View row){
			picture = (ImageView) row.findViewById(R.id.showMDUserProfileImage);
			realName= (TextView) row.findViewById(R.id.showUserRealName);
			screenName= (TextView) row.findViewById(R.id.showUserScreenName);
			lastMessage= (TextView) row.findViewById(R.id.showDMText);
		}
	}
	
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) parent.getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.dm_conversation_row, null);
		ViewHolder viewHolder = new ViewHolder(view);
		view.setTag(viewHolder);
		return view;
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View userrow, Context context, Cursor cursor) {
		ViewHolder holder = (ViewHolder) userrow.getTag();
		// set text fields

		// Profile image
		if (!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_SCREENNAME))) {
			// text fields
			String realName = cursor.getString(cursor
					.getColumnIndex(TwitterUsers.COL_NAME));
			holder.realName.setText(realName);
			String screenName = cursor.getString(cursor
					.getColumnIndex(TwitterUsers.COL_SCREENNAME));
			holder.screenName.setText("@" + screenName);
			String lastMessage = cursor.getString(cursor
					.getColumnIndex(DirectMessages.COL_TEXT));
			holder.lastMessage.setText(lastMessage);
			// profile image
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
					holder.picture.setImageResource(R.drawable.profile_image_placeholder);
			} catch (FileNotFoundException e) {
				holder.picture.setImageResource(R.drawable.profile_image_placeholder);
			}
		} else {
			holder.picture.setImageResource(R.drawable.profile_image_placeholder);
		}
	}

}
