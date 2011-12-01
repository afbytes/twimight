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

import android.content.Context;
import android.database.Cursor;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** 
 * Cursor adapter for a cursor containing tweets.
 * TODO: How do we handle empty cursors? 
 */
public class TweetAdapter extends SimpleCursorAdapter {
	
	static final String[] from = {TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME, Tweets.TWEETS_COLUMNS_TEXT};
	static final int[] to = {R.id.textUser, R.id.textText};

	/** Constructor */
	public TweetAdapter(Context context, Cursor c) {
		super(context, R.layout.row, c, from, to);  
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		super.bindView(row, context, cursor);
		
		// Find views by id
		long createdAt = cursor.getLong(cursor.getColumnIndex(Tweets.TWEETS_COLUMNS_CREATED));
		TextView textCreatedAt = (TextView) row.findViewById(R.id.textCreatedAt);
		textCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));
		
		// TODO: Image
		ImageView picture = (ImageView) row.findViewById(R.id.imageView1);
		picture.setImageResource(R.drawable.default_profile);
		
		ImageView favoriteStar = (ImageView) row.findViewById(R.id.favorite);
		ImageView toPostInfo = (ImageView) row.findViewById(R.id.topost);
		int flags = cursor.getInt(cursor.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));
		
		boolean toPost = (flags>0);
		if(toPost){
			toPostInfo.setImageResource(android.R.drawable.ic_dialog_alert);
			toPostInfo.getLayoutParams().height = 30;
		} else {
			toPostInfo.setImageResource(R.drawable.blank);
		}
		
		boolean favorited = ((cursor.getInt(cursor.getColumnIndex(Tweets.TWEETS_COLUMNS_FAVORITED)) > 0) 
							&& ((flags & Tweets.FLAG_TO_UNFAVORITE)==0))
							|| ((flags & Tweets.FLAG_TO_FAVORITE)>0);
		if(favorited){
			favoriteStar.setImageResource(android.R.drawable.star_off);
		} else {
			favoriteStar.setImageResource(R.drawable.blank);
		}
		
		// TODO: Sending?
	}
	
}
