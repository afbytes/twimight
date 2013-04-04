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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
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
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.Html.HtmlPage;





/** 
 * Cursor adapter for a cursor containing tweets.
 */
public class TweetAdapter extends SimpleCursorAdapter {
	
	static final String[] from = {TwitterUsers.COL_NAME};
	static final int[] to = {R.id.textUser};
	Context context;
	private static final String TAG = "tweet adapter";
	private HtmlPagesDbHelper htmlDbHelper;

	/** Constructor */
	public TweetAdapter(Context context, Cursor c) {		
		super(context, R.layout.row, c, from, to);  
		this.context= context;
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		super.bindView(row, context, cursor);
		htmlDbHelper = new HtmlPagesDbHelper(context);
		htmlDbHelper.open();
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
		
		boolean retweeted = false;
		//add the retweet message in case it is a retweet
		int col = cursor.getColumnIndex(Tweets.COL_RETWEETED_BY);
		if (col > -1) {
			String retweeted_by = cursor.getString(col);
			TextView textRetweeted_by = (TextView) row.findViewById(R.id.textRetweeted_by);
			if (retweeted_by != null) {
				textRetweeted_by.setText("retweeted by " + retweeted_by);		
				textRetweeted_by.setVisibility(View.VISIBLE);	
				retweeted = true;
			}
			else {
				//textRetweeted_by.setText("");
				textRetweeted_by.setVisibility(View.GONE);		
			}
		}
		//add the download status message in case it has a link
		boolean downloaded = false;
		boolean downloading = false;
		boolean downloadfailed = false;
		String userId = String.valueOf(cursor.getLong(cursor.getColumnIndex(Tweets.COL_USER)));

		int colTid = cursor.getColumnIndex(Tweets.COL_TID);
		String tweetId = "0";
		if(colTid > -1){
			tweetId = String.valueOf(cursor.getLong(cursor.getColumnIndex(Tweets.COL_TID)));
		}
		
		

		String substr = Html.fromHtml(cursor.getString(cursor.getColumnIndex(Tweets.COL_TEXT))).toString();

		String[] strarr = substr.split(" ");

		//check the urls of the tweet
		for(String subStrarr : strarr){

			if(subStrarr.indexOf("http://") >= 0 || subStrarr.indexOf("https://") >= 0){
				String subUrl = null;
				if(subStrarr.indexOf("http://") >= 0){
					subUrl = subStrarr.substring(subStrarr.indexOf("http://"));
				}else if(subStrarr.indexOf("https://") >= 0){
					subUrl = subStrarr.substring(subStrarr.indexOf("https://"));
				}
				ContentValues htmlCV = htmlDbHelper.getPageInfo(subUrl, tweetId, userId);

				if(htmlCV!=null){
					if(htmlCV.getAsInteger(HtmlPage.COL_DOWNLOADED) == 1){
						downloaded = true;
					}
					else if(htmlCV.getAsInteger(HtmlPage.COL_TRIES) < HtmlPage.DOWNLOAD_LIMIT){
						downloading = true;
					}
					else{
						downloadfailed = true;
					}
				}
			}
		}
		
		
		int col_html = cursor.getColumnIndex(Tweets.COL_HTMLS);
		if (col_html > -1) {
			int hasHtml = cursor.getInt(col_html);
			TextView textHtml = (TextView) row.findViewById(R.id.linkDownloaded);
			TextView splitBar = (TextView) row.findViewById(R.id.split_bar);
			splitBar.setVisibility(View.GONE);
			if(hasHtml == 1){
				
				//if webpages have been downloaded
				String text = null;
				if(retweeted){
					if(downloaded){
						splitBar.setText("|");
						splitBar.setVisibility(View.VISIBLE);
						text = "downloaded";
						textHtml.setTextColor(Color.parseColor("#1d8a04"));
						
					}
					else if(downloading){
						splitBar.setText("|");
						splitBar.setVisibility(View.VISIBLE);
						text = "downloading";
						textHtml.setTextColor(Color.parseColor("#9ea403"));
					}
					else if(downloadfailed){
						splitBar.setText("|");
						splitBar.setVisibility(View.VISIBLE);
						text = "download failed";
						textHtml.setTextColor(Color.parseColor("#9E3B33"));
					}
					else{
						splitBar.setText("|");
						splitBar.setVisibility(View.VISIBLE);
						text = "not downloaded";
						textHtml.setTextColor(Color.parseColor("#9E3B33"));
					}
					
				}
				else{
					if(downloaded){
						text = "downloaded";
						textHtml.setTextColor(Color.parseColor("#1d8a04"));
					}
					else if(downloading){
						text = "downloading";
						textHtml.setTextColor(Color.parseColor("#9ea403"));
					}
					else if(downloadfailed){
						text = "download failed";
						textHtml.setTextColor(Color.parseColor("#9E3B33"));
					}
					else{
						text = "not downloaded";
						textHtml.setTextColor(Color.parseColor("#9E3B33"));
					}
					
				}
				textHtml.setText(text);
				textHtml.setVisibility(View.VISIBLE);
				
			}
			else {
				textHtml.setVisibility(View.GONE);	
			}
		}
		// Profile image
		ImageView picture = (ImageView) row.findViewById(R.id.imageView1);
		if(!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH))){			
			

			//InternalStorageHelper helper = new InternalStorageHelper(context);			
			//byte[] imageByteArray = helper.readImage(filename);	
			int userRowId = cursor.getInt(cursor.getColumnIndex("userRowId"));
			Uri imageUri = Uri.parse("content://" +TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + userRowId);
			InputStream is;
			try {
				is = context.getContentResolver().openInputStream(imageUri);
				if (is != null) {						
					Bitmap bm = BitmapFactory.decodeStream(is);
					picture.setImageBitmap(bm);	
					
				} else
					picture.setImageResource(R.drawable.default_profile);
			} catch (Exception e) {
				Log.e(TAG,"error opening input stream",e);
				picture.setImageResource(R.drawable.default_profile);
			}				

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
