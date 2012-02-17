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

import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** 
 * Cursor adapter for a cursor containing users.
 */
public class DMAdapter extends SimpleCursorAdapter {
	
	static final String[] from = {TwitterUsers.COL_SCREENNAME, DirectMessages.COL_TEXT};
	static final int[] to = {R.id.showDMScreenName, R.id.showDMText};

	/** Constructor */
	public DMAdapter(Context context, Cursor c) {
		super(context, R.layout.dmrow, c, from, to);  
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View dmrow, Context context, Cursor cursor) {
		super.bindView(dmrow, context, cursor);
		
		// Find views by id
		long createdAt = cursor.getLong(cursor.getColumnIndex(DirectMessages.COL_CREATED));
		TextView dmCreatedAt = (TextView) dmrow.findViewById(R.id.dmCreatedAt);
		dmCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));

			
		// Profile image
		ImageView picture = (ImageView) dmrow.findViewById(R.id.showDMProfileImage);
		if(!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE))){
			byte[] bb = cursor.getBlob(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE));
			picture.setImageBitmap(BitmapFactory.decodeByteArray(bb, 0, bb.length));
		} else {
			picture.setImageResource(R.drawable.default_profile);
		}
		
		// any transactional flags?
		ImageView toPostInfo = (ImageView) dmrow.findViewById(R.id.dmToPost);
		int flags = cursor.getInt(cursor.getColumnIndex(DirectMessages.COL_FLAGS));
		
		boolean toPost = (flags>0);
		if(toPost){
			toPostInfo.setImageResource(android.R.drawable.ic_dialog_alert);
			toPostInfo.getLayoutParams().height = 30;
		} else {
			toPostInfo.setImageResource(R.drawable.blank);
		}
		
		// can we delete the message
		/*
		ImageButton deleteButton = (ImageButton) dmrow.findViewById(R.id.showDMDelete);
		if(Long.toString(cursor.getLong(cursor.getColumnIndex(DirectMessages.COL_SENDER))).equals(LoginActivity.getTwitterId(context))) {
			deleteButton.setVisibility(ImageButton.VISIBLE);
		} else {
			deleteButton.setVisibility(ImageButton.GONE);
		}
		*/
		
		// DM background and disaster info
		LinearLayout rowLayout = (LinearLayout) dmrow.findViewById(R.id.dmUserRowLayout);		
		if(Long.toString(cursor.getLong(cursor.getColumnIndex(DirectMessages.COL_SENDER))).equals(LoginActivity.getTwitterId(context))) {
			
			if(cursor.getInt(cursor.getColumnIndex(DirectMessages.COL_ISDISASTER))>0)
				rowLayout.setBackgroundResource(R.drawable.disaster_tweet_background);
			else
				rowLayout.setBackgroundResource(R.drawable.own_tweet_background);
			
		} else {
			
			if(cursor.getInt(cursor.getColumnIndex(DirectMessages.COL_ISDISASTER))>0)
				rowLayout.setBackgroundResource(R.drawable.disaster_dm_background_receiver);
			else
				rowLayout.setBackgroundResource(R.drawable.normal_tweet_background);
		}
		
	}
	
}
