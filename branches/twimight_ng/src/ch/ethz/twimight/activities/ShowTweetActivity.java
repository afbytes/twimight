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
package ch.ethz.twimight.activities;

import java.util.Date;

import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Display a tweet
 * @author thossmann
 *
 */
public class ShowTweetActivity extends Activity{

	private static final String TAG = "ShowTweetActivity";
	
	Cursor c;
	
	// Views
	private ImageView profileImage;
	private TextView screenNameView;
	private TextView realNameView;
	private TextView tweetTextView;
	private TextView createdTextView;
	private TextView createdWithView;
	
	private LinearLayout userInfoView;
	Uri uri;
	
	private boolean favorited;
	int flags;
	int userRowId;
	int rowId;
	String text;
	String screenName;
	
		
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.showtweet);
		
		profileImage = (ImageView) findViewById(R.id.showTweetProfileImage);
		screenNameView = (TextView) findViewById(R.id.showTweetScreenName);
		realNameView = (TextView) findViewById(R.id.showTweetRealName);
		
		tweetTextView = (TextView) findViewById(R.id.showTweetText);
		createdTextView = (TextView) findViewById(R.id.showTweetCreatedAt);
		createdWithView = (TextView) findViewById(R.id.showTweetCreatedWith);
		
		rowId = getIntent().getIntExtra("rowId", 0);
		
		// If we don't know which tweet to show, we stop the activity
		if(rowId == 0) finish();
		
		// get data from local DB and mark for update
		uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);
		c = managedQuery(uri, null, null, null, null);
		
		if(c.getCount() == 0) finish();
		
		c.moveToFirst();
		// If there are any flags, schedule the Tweet for synch
		if(c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS)) >0){
			Intent i = new Intent(TwitterService.SYNCH_ACTION);
			i.putExtra("synch_request", TwitterService.SYNCH_TWEET);
			i.putExtra("rowId", new Long(uri.getLastPathSegment()));
			startService(i);
		}

		// The tweet info
		screenName = c.getString(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME));
		screenNameView.setText(screenName);
		realNameView.setText(c.getString(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_NAME)));
		text = c.getString(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TEXT));
		tweetTextView.setText(text);
		createdTextView.setText(new Date(c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_CREATED))).toString());
		if(c.getString(c.getColumnIndex(Tweets.TWEETS_COLUMNS_SOURCE))!=null){
			createdWithView.setText(Html.fromHtml(c.getString(c.getColumnIndex(Tweets.TWEETS_COLUMNS_SOURCE))));
		} else {
			createdWithView.setVisibility(TextView.GONE);
		}
		
		// The user info
		userRowId = c.getInt(c.getColumnIndex("userRowId"));
		userInfoView = (LinearLayout) findViewById(R.id.showTweetUserInfo);
		
		userInfoView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				userInfoView.setBackgroundResource(android.R.drawable.list_selector_background);
				Intent i = new Intent(getBaseContext(), ShowUserActivity.class);
				i.putExtra("rowId", userRowId);
				startActivity(i);
			}
			
		});
		
		// Profile image
		if(!c.isNull(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE))){
			ImageView picture = (ImageView) findViewById(R.id.showTweetProfileImage);			
			byte[] bb = c.getBlob(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE));
			picture.setImageBitmap(BitmapFactory.decodeByteArray(bb, 0, bb.length));
		}
		
		flags = c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));

		// Notifications
		if((flags & Tweets.FLAG_TO_INSERT) ==0){
			LinearLayout toSendNotification = (LinearLayout) findViewById(R.id.showTweetTosend);
			toSendNotification.setVisibility(LinearLayout.GONE);
		}
		
		if((flags & Tweets.FLAG_TO_DELETE) ==0){
			LinearLayout toDeleteNotification = (LinearLayout) findViewById(R.id.showTweetTodelete);
			toDeleteNotification.setVisibility(LinearLayout.GONE);
		}
		
		if((flags & Tweets.FLAG_TO_FAVORITE) ==0){
			LinearLayout toFavoriteNotification = (LinearLayout) findViewById(R.id.showTweetTofavorite);
			toFavoriteNotification.setVisibility(LinearLayout.GONE);
		}

		if((flags & Tweets.FLAG_TO_UNFAVORITE) ==0){
			LinearLayout toUnfavoriteNotification = (LinearLayout) findViewById(R.id.showTweetTounfavorite);
			toUnfavoriteNotification.setVisibility(LinearLayout.GONE);
		}

		if((flags & Tweets.FLAG_TO_RETWEET) ==0){
			LinearLayout toRetweetNotification = (LinearLayout) findViewById(R.id.showTweetToretweet);
			toRetweetNotification.setVisibility(LinearLayout.GONE);
		}

			
		String userString = Long.toString(c.getLong(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_ID)));
		String localUserString = LoginActivity.getTwitterId(this);

		/*
		 *  Buttons
		 */
		// Retweet Button
		ImageButton retweetButton = (ImageButton) findViewById(R.id.showTweetRetweet);
		// we do not show the retweet button for (1) tweets from the local user, (2) tweets which have been flagged to retweeted and (3) tweets which have been marked as retweeted 
		if(userString.equals(localUserString) | ((flags & Tweets.FLAG_TO_RETWEET) > 0) | (c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_RETWEETED))>0)){
			retweetButton.setVisibility(ImageButton.GONE);
		} else {
			retweetButton.setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View v) {
					v.setBackgroundResource(android.R.drawable.list_selector_background);
					showRetweetDialog();
				}
				
			});
		}
		
		// Delete Button
		ImageButton deleteButton = (ImageButton) findViewById(R.id.showTweetDelete);
		if(userString.equals(localUserString)){
			if((flags & Tweets.FLAG_TO_DELETE) == 0){
				deleteButton.setOnClickListener(new OnClickListener(){
					@Override
					public void onClick(View v) {
						v.setBackgroundResource(android.R.drawable.list_selector_background);
						showDeleteDialog();
						
					}
				});
			} else {
				deleteButton.setVisibility(ImageButton.GONE);
			}
		} else {
			deleteButton.setVisibility(ImageButton.GONE);
		}
		
		// Reply button: we show it only if we have a Tweet ID!
		ImageButton replyButton = (ImageButton) findViewById(R.id.showTweetReply);
		if(c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)) != 0){
			replyButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(getBaseContext(), NewTweetActivity.class);
					i.putExtra("isReplyTo", c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)));
					i.putExtra("text", "@"+c.getString(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME))+ " ");
					startActivity(i);
				}
			});
		} else {
			replyButton.setVisibility(ImageButton.GONE);
		}
		
		// Favorite button
		favorited = (c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FAVORITED)) > 0) || ((flags & Tweets.FLAG_TO_FAVORITE)>0);
		ImageButton favoriteButton = (ImageButton) findViewById(R.id.showTweetFavorite);
		if(favorited){
			favoriteButton.setImageResource(android.R.drawable.btn_star_big_on);
		}
		favoriteButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				v.setBackgroundResource(android.R.drawable.list_selector_background);
				if(favorited){
					// unfavorite
					getContentResolver().update(uri, clearFavoriteFlag(flags), null, null);
					((ImageButton) v).setImageResource(android.R.drawable.btn_star_big_off);
					favorited=false;
					// TODO: remove from favorites table
				} else {
					// favorite
					getContentResolver().update(uri, setFavoriteFlag(flags), null, null);
					((ImageButton) v).setImageResource(android.R.drawable.btn_star_big_on);
					favorited=true;
					// TODO: insert to favorites table
				}
				
			}
			
		});
		
		
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		
	}


	/**
	 * Populate the Options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);

		return true;
	}

	/**
	 * Handle options menu selection
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item){

		switch(item.getItemId()){

		default:
			return true;
		}
	}
	
	/**
	 * Asks the user if she wants to delete a tweet.
	 */
	private void showDeleteDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to delete your Tweet?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   getContentResolver().update(uri, setDeleteFlag(flags), null, null);
		        	   finish();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	/**
	 * Asks the user how to retweet a tweet (old or new style)
	 */
	private void showRetweetDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Would you like to modify the tweet before retweeting?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   Intent i = new Intent(getBaseContext(), NewTweetActivity.class);
		        	   i.putExtra("text", "RT @"+screenName+" " +text);
		        	   startActivity(i);
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   getContentResolver().update(uri, setRetweetFlag(flags), null, null);
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	/**
	 * Adds the delete flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues setDeleteFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags | Tweets.FLAG_TO_DELETE);
		return cv;
	}
	
	/**
	 * Adds the to retweet flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues setRetweetFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags | Tweets.FLAG_TO_RETWEET);
		return cv;
	}
	
	/**
	 * Adds the favorite flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues setFavoriteFlag(int flags) {
		ContentValues cv = new ContentValues();
		// set favorite flag und clear unfavorite flag
		cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags | Tweets.FLAG_TO_FAVORITE & (~Tweets.FLAG_TO_UNFAVORITE));
		return cv;
	}
	
	/**
	 * Clears the favorite flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues clearFavoriteFlag(int flags) {
		ContentValues cv = new ContentValues();
		// clear favorite flag and set unfavorite flag
		cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags & (~Tweets.FLAG_TO_FAVORITE) | Tweets.FLAG_TO_UNFAVORITE);
		return cv;
	}
}
