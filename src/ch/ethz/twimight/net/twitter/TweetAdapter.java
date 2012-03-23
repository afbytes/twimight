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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.Html;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.util.InternalStorageHelper;

/** 
 * Cursor adapter for a cursor containing tweets.
 */
public class TweetAdapter extends SimpleCursorAdapter {
	
	static final String[] from = {TwitterUsers.COL_NAME};
	static final int[] to = {R.id.textUser};
	Context context;
	private static final String TAG = "tweet adapter";

	/** Constructor */
	public TweetAdapter(Context context, Cursor c) {		
		super(context, R.layout.row, c, from, to);  
		this.context= context;
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		super.bindView(row, context, cursor);
		
		// if we don't have a real name, we use the screen name
		if(cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME))==null){
			TextView usernameTextView = (TextView) row.findViewById(R.id.textUser);
			usernameTextView.setText("@"+cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
		}
		
		long createdAt = cursor.getLong(cursor.getColumnIndex(Tweets.COL_CREATED));
		TextView textCreatedAt = (TextView) row.findViewById(R.id.tweetCreatedAt);
		textCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));		

		TextView tweetText = (TextView) row.findViewById(R.id.textText);
		// here, we don't want the entities to be clickable, so we use the standard tag handler
		tweetText.setText(Html.fromHtml(cursor.getString(cursor.getColumnIndex(Tweets.COL_TEXT))));
		
		//add the retweet message in case it is a retweet
		int col = cursor.getColumnIndex(Tweets.COL_RETWEETED_BY);
		if (col > -1) {
			String retweeted_by = cursor.getString(col);
			TextView textRetweeted_by = (TextView) row.findViewById(R.id.textRetweeted_by);
			if (retweeted_by != null) {
				textRetweeted_by.setText("retweeted by " + retweeted_by);		
				textRetweeted_by.setVisibility(View.VISIBLE);					
			}
			else {
				//textRetweeted_by.setText("");
				textRetweeted_by.setVisibility(View.GONE);		
			}
		}		
		
		// Profile image
		ImageView picture = (ImageView) row.findViewById(R.id.imageView1);
		if(!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE))){
			
			String filename = cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_SCREENNAME));
			//Uri uri = Uri.parse(Tweets.TWEET_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS_PICTURE + "/" + filename);
			//InputStream is;
			InternalStorageHelper helper = new InternalStorageHelper(context);	
		//long start = System.currentTimeMillis();
			byte[] imageByteArray = helper.readImage(filename);			
			//long end = System.currentTimeMillis();
			//Log.i(TAG,"time: " + (end-start) + " ms");
			if (imageByteArray != null) {				
				//is = context.getContentResolver().openInputStream(uri);				
				Bitmap bm = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.length);
				picture.setImageBitmap(bm);	
			} else
				picture.setImageResource(R.drawable.default_profile);

		} else {			
			picture.setImageResource(R.drawable.default_profile);
		}


		// any transactional flags?
		ImageView toPostInfo = (ImageView) row.findViewById(R.id.topost);
		int flags = cursor.getInt(cursor.getColumnIndex(Tweets.COL_FLAGS));
		
		boolean toPost = (flags>0);
		if(toPost){
			toPostInfo.setVisibility(ImageView.VISIBLE);
		} else {
			toPostInfo.setVisibility(ImageView.GONE);
		}
		
		// favorited
		ImageView favoriteStar = (ImageView) row.findViewById(R.id.favorite);

		boolean favorited = ((cursor.getInt(cursor.getColumnIndex(Tweets.COL_FAVORITED)) > 0) 
								&& ((flags & Tweets.FLAG_TO_UNFAVORITE)==0))
								|| ((flags & Tweets.FLAG_TO_FAVORITE)>0);
		if(favorited){
			favoriteStar.setVisibility(ImageView.VISIBLE);
		} else {
			favoriteStar.setVisibility(ImageView.GONE);
		}
		
		// disaster info
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
