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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** 
 * Cursor adapter for a cursor containing tweets.
 * TODO: How do we handle empty cursors? 
 */
public class TweetAdapter extends SimpleCursorAdapter {
	
	static final String[] from = {TwitterUsers.COL_SCREENNAME, Tweets.COL_TEXT};
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
		long createdAt = cursor.getLong(cursor.getColumnIndex(Tweets.COL_CREATED));
		TextView textCreatedAt = (TextView) row.findViewById(R.id.textCreatedAt);
		textCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));
		
		// Profile image
		ImageView picture = (ImageView) row.findViewById(R.id.imageView1);
		if(!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE))){
			byte[] bb = cursor.getBlob(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE));
			picture.setImageBitmap(BitmapFactory.decodeByteArray(bb, 0, bb.length));
		} else {
			picture.setImageResource(R.drawable.default_profile);
		}

		
		// any transactional flags?
		ImageView toPostInfo = (ImageView) row.findViewById(R.id.topost);
		int flags = cursor.getInt(cursor.getColumnIndex(Tweets.COL_FLAGS));
		
		boolean toPost = (flags>0);
		if(toPost){
			toPostInfo.setImageResource(android.R.drawable.ic_dialog_alert);
			toPostInfo.getLayoutParams().height = 30;
		} else {
			toPostInfo.setImageResource(R.drawable.blank);
		}
		
		// favorited
		ImageView favoriteStar = (ImageView) row.findViewById(R.id.favorite);

		boolean favorited = ((cursor.getInt(cursor.getColumnIndex(Tweets.COL_FAVORITED)) > 0) 
							&& ((flags & Tweets.FLAG_TO_UNFAVORITE)==0))
							|| ((flags & Tweets.FLAG_TO_FAVORITE)>0);
		if(favorited){
			favoriteStar.setImageResource(android.R.drawable.star_off);
		} else {
			favoriteStar.setImageResource(R.drawable.blank);
		}
		
		// tweet background and disaster info
		LinearLayout rowLayout = (LinearLayout) row.findViewById(R.id.rowLayout);
		ImageView verifiedImage = (ImageView) row.findViewById(R.id.showTweetVerified);
		if(cursor.getInt(cursor.getColumnIndex(Tweets.COL_ISDISASTER))>0){
			rowLayout.setBackgroundResource(R.drawable.disaster_tweet_background);
			verifiedImage = (ImageView) row.findViewById(R.id.showTweetVerified);
			verifiedImage.setVisibility(ImageView.VISIBLE);
			if(cursor.getInt(cursor.getColumnIndex(Tweets.COL_ISVERIFIED))>0){
				verifiedImage.setImageResource(android.R.drawable.ic_secure);
			} else {
				verifiedImage.setImageResource(android.R.drawable.ic_partial_secure);
			}
		} else if(Long.toString(cursor.getLong(cursor.getColumnIndex(Tweets.COL_USER))).equals(LoginActivity.getTwitterId(context))) {
			rowLayout.setBackgroundResource(R.drawable.own_tweet_background);
			verifiedImage.setVisibility(ImageView.GONE);
		} else if((cursor.getColumnIndex(Tweets.COL_MENTIONS)>=0) && (cursor.getInt(cursor.getColumnIndex(Tweets.COL_MENTIONS))>0)){
			rowLayout.setBackgroundResource(R.drawable.mention_tweet_background);
			verifiedImage.setVisibility(ImageView.GONE);			
		} else {
			rowLayout.setBackgroundResource(R.drawable.normal_tweet_background);
			verifiedImage.setVisibility(ImageView.GONE);
		}
		
	}
	
}
