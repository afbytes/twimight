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

import java.io.InputStream;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;


/** 
 * Cursor adapter for a cursor containing tweets.
 */
public class TweetAdapter extends SimpleCursorAdapter {
	
	static final String[] from = {TwitterUsers.COL_NAME};
	static final int[] to = {R.id.textUser};	
	private static final String TAG = "tweet adapter";
	private HtmlPagesDbHelper htmlDbHelper;
	
	
	private static class ViewHolder {
		TextView usernameTextView;
		TextView textCreatedAt; 
		TextView tweetText ;
		TextView textRetweeted_by ;
		TextView textHtml ;
		TextView splitBar;
		ImageView picture ;
		ImageView toPostInfo;
		ImageView favoriteStar ;
		LinearLayout rowLayout;
		ImageView verifiedImage ;
	
		 
		}

	/** Constructor */
	public TweetAdapter(Context context, Cursor c) {		
		super(context, R.layout.row, c, from, to);  
		
	}
	
	

  


	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		// TODO Auto-generated method stub

		LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.row, null);		
		createHolder(view);	
		
		return view;

	}

	private void createHolder(View view) {
		ViewHolder holder = new ViewHolder();
		setHolderFields(view,holder);
		view.setTag(holder);
	}
	
	private void setHolderFields(View row, ViewHolder holder) {
		holder.usernameTextView = (TextView) row.findViewById(R.id.textUser);
		holder.textCreatedAt = (TextView) row.findViewById(R.id.tweetCreatedAt);
		holder.tweetText = (TextView) row.findViewById(R.id.textText);
		holder.textRetweeted_by = (TextView) row.findViewById(R.id.textRetweeted_by);
		holder.textHtml = (TextView) row.findViewById(R.id.linkDownloaded);
		holder.splitBar = (TextView) row.findViewById(R.id.split_bar);
		holder.picture = (ImageView) row.findViewById(R.id.imageView1);
		holder.toPostInfo = (ImageView) row.findViewById(R.id.topost);
		holder.favoriteStar = (ImageView) row.findViewById(R.id.favorite);
		holder.rowLayout = (LinearLayout) row.findViewById(R.id.rowLayout);
		holder. verifiedImage = (ImageView) row.findViewById(R.id.showTweetVerified);
		
		
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		super.bindView(row, context, cursor);
		
		
		ViewHolder holder = (ViewHolder) row.getTag();			
		
		htmlDbHelper = new HtmlPagesDbHelper(context.getApplicationContext());
		htmlDbHelper.open();
		
		// if we don't have a real name, we use the screen name
		if(cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME))==null){			
			holder.usernameTextView.setText("@"+cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
		}
		
		long createdAt = cursor.getLong(cursor.getColumnIndex(Tweets.COL_CREATED));
		
		holder.textCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));		
		// here, we don't want the entities to be clickable, so we use the standard tag handler
		
		holder.tweetText.setText(Html.fromHtml(cursor.getString(cursor.getColumnIndex(Tweets.COL_TEXT))));
		
		boolean retweeted = false;
		//add the retweet message in case it is a retweet
		int col = cursor.getColumnIndex(Tweets.COL_RETWEETED_BY);
		if (col > -1) {
			String retweeted_by = cursor.getString(col);
			
			if (retweeted_by != null) {
				holder.textRetweeted_by.setText(context.getString(R.string.retweeted_by) + retweeted_by);		
				holder.textRetweeted_by.setVisibility(View.VISIBLE);	
				retweeted = true;
			}
			else {
				//textRetweeted_by.setText("");
				holder.textRetweeted_by.setVisibility(View.GONE);		
			}
		}
		
			
		int col_html = cursor.getColumnIndex(Tweets.COL_HTML_PAGES);
		if (col_html > -1) {
			int hasHtml = cursor.getInt(col_html);
			
			holder.splitBar.setVisibility(View.GONE);
			
			if(hasHtml == 1){	
				
				int colTid = cursor.getColumnIndex(Tweets.COL_TID);
				long tweetId = 0;				
				if(colTid > -1){
					tweetId = cursor.getLong(cursor.getColumnIndex(Tweets.COL_TID));
				}
				TODO:
				
				
				//if webpages have been downloaded
				int text =0;
				if(retweeted){
					
					holder.splitBar.setText("|");
					holder.splitBar.setVisibility(View.VISIBLE);
					text = R.string.downloading;
					holder.textHtml.setTextColor(Color.parseColor("#9ea403"));
				}
				else{
					text = R.string.downloading;
					holder.textHtml.setTextColor(Color.parseColor("#9ea403"));
				} 
				
				if (text != 0) {
					holder.textHtml.setText(context.getString(text));
					holder.textHtml.setVisibility(View.VISIBLE);
				}			
				
			}
			else {
				holder.textHtml.setVisibility(View.GONE);
			}
		}
		// Profile image		
		if(!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH))){			
			
			int userRowId = cursor.getInt(cursor.getColumnIndex("userRowId"));
			Uri imageUri = Uri.parse("content://" +TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + userRowId);
			InputStream is;
			try {
				is = context.getContentResolver().openInputStream(imageUri);
				if (is != null) {						
					Bitmap bm = BitmapFactory.decodeStream(is);
					holder.picture.setImageBitmap(bm);	
					
				} else
					holder.picture.setImageResource(R.drawable.default_profile);
			} catch (Exception e) {
				//Log.e(TAG,"error opening input stream",e);
				holder.picture.setImageResource(R.drawable.default_profile);
			}				

		} else {			
			holder.picture.setImageResource(R.drawable.default_profile);
		}


		// any transactional flags?		
		int flags = cursor.getInt(cursor.getColumnIndex(Tweets.COL_FLAGS));
		
		boolean toPost = (flags>0);
		if(toPost){
			holder.toPostInfo.setVisibility(ImageView.VISIBLE);
		} else {
			holder.toPostInfo.setVisibility(ImageView.GONE);
		}
		
		// favorited
		boolean favorited = ((cursor.getInt(cursor.getColumnIndex(Tweets.COL_FAVORITED)) > 0) 
								&& ((flags & Tweets.FLAG_TO_UNFAVORITE)==0))
								|| ((flags & Tweets.FLAG_TO_FAVORITE)>0);
		if(favorited){
			holder.favoriteStar.setVisibility(ImageView.VISIBLE);
		} else {
			holder.favoriteStar.setVisibility(ImageView.GONE);
		}
		
		// disaster info		
		if(cursor.getInt(cursor.getColumnIndex(Tweets.COL_ISDISASTER))>0){
			
			holder.rowLayout.setBackgroundResource(R.drawable.disaster_tweet_background);
		
			holder.verifiedImage.setVisibility(ImageView.VISIBLE);
			if(cursor.getInt(cursor.getColumnIndex(Tweets.COL_ISVERIFIED))>0){
				holder.verifiedImage.setImageResource(android.R.drawable.ic_secure);
			} else {
				holder.verifiedImage.setImageResource(android.R.drawable.ic_partial_secure);
			}
		} else if(Long.toString(cursor.getLong(cursor.getColumnIndex(Tweets.COL_USER))).equals(LoginActivity.getTwitterId(context))) {
			
			holder.rowLayout.setBackgroundResource(R.drawable.own_tweet_background);
			holder.verifiedImage.setVisibility(ImageView.GONE);
			
		} else if((cursor.getColumnIndex(Tweets.COL_MENTIONS)>=0) && (cursor.getInt(cursor.getColumnIndex(Tweets.COL_MENTIONS))>0)){
			
			holder.rowLayout.setBackgroundResource(R.drawable.mention_tweet_background);
			holder.verifiedImage.setVisibility(ImageView.GONE);			
		} else {
			holder.rowLayout.setBackgroundResource(R.drawable.normal_tweet_background);
			holder.verifiedImage.setVisibility(ImageView.GONE);
		}
		
	}
	
}
