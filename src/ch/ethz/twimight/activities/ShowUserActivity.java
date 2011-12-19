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

import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Display a user
 * @author thossmann
 *
 */
public class ShowUserActivity extends Activity{

	private static final String TAG = "ShowTwitterUserActivity";

	Uri uri;
	Cursor c;
	int flags;
	int rowId;

	// Views
	private ImageView profileImage;
	private TextView screenName;
	private TextView realName;
	private TextView location;
	private TextView description;
	private TextView stats;
	private Button followButton;
	private Button mentionButton;
	private Button messageButton;
	private Button showFollowersButton;
	private Button showFriendsButton;
	private LinearLayout followInfo;
	private LinearLayout unfollowInfo;
	private Button showUserTweetsButton;

	// the menu
	private static final int OPTIONS_MENU_HOME = 10;


	private boolean following;
	String userScreenName;
	Handler handler;
	ContentObserver observer;

	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.showuser);

		profileImage = (ImageView) findViewById(R.id.showUserProfileImage);
		screenName = (TextView) findViewById(R.id.showUserScreenName);
		realName = (TextView) findViewById(R.id.showUserRealName);
		location = (TextView) findViewById(R.id.showUserLocation);
		description = (TextView) findViewById(R.id.showUserDescription);
		stats = (TextView) findViewById(R.id.showUserStats);
		followButton = (Button) findViewById(R.id.showUserFollow);
		mentionButton = (Button) findViewById(R.id.showUserMention);
		messageButton = (Button) findViewById(R.id.showUserMessage);
		followInfo = (LinearLayout) findViewById(R.id.showUserTofollow);
		unfollowInfo = (LinearLayout) findViewById(R.id.showUserTounfollow);
		showFollowersButton = (Button) findViewById(R.id.showUserFollowers);
		showFriendsButton = (Button) findViewById(R.id.showUserFriends);
		showUserTweetsButton = (Button) findViewById(R.id.showUserTweetsButton);

		if(getIntent().hasExtra("rowId")){
			rowId = getIntent().getIntExtra("rowId", 0);

			// get data from local DB
			uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + rowId);
			c = getContentResolver().query(uri, null, null, null, null);

			if(c.getCount() == 0) finish();

			c.moveToFirst();

		} else if(getIntent().hasExtra("screenname")){

			Log.e(TAG, getIntent().getStringExtra("screenname"));
			
			// get data from local DB
			uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
			c = getContentResolver().query(uri, null, TwitterUsers.COL_SCREENNAME+" LIKE '"+getIntent().getStringExtra("screenname")+"'", null, null);

			if(c.getCount() == 0) {
				Log.w(TAG, "USER NOT FOUND " + getIntent().getStringExtra("screenname"));
				finish();
				return;
			}

			c.moveToFirst();
			rowId = c.getInt(c.getColumnIndex("_id"));

		} else {
			// if we don't know which user to show
			Log.w(TAG, "WHICH USER??");
			finish();
			return;
		}

		// mark the user for updating
		uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + rowId);
		ContentValues cv = new ContentValues();
		cv.put(TwitterUsers.COL_FLAGS, TwitterUsers.FLAG_TO_UPDATEIMAGE|TwitterUsers.FLAG_TO_UPDATE|c.getInt(c.getColumnIndex(TwitterUsers.COL_FLAGS)));
		getContentResolver().update(uri, cv, null, null);
		
		// register content observer to refresh when user was updated
		handler = new Handler();
		observer = new UserContentObserver(handler);
		c.registerContentObserver(observer);

		// show the views
		showUserInfo();
		
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();

		if(followButton!=null) followButton.setOnClickListener(null);
		if(mentionButton!=null) mentionButton.setOnClickListener(null);
		if(messageButton!=null) messageButton.setOnClickListener(null);
		if(showFollowersButton!=null) showFollowersButton.setOnClickListener(null);
		if(showFriendsButton!=null) showFriendsButton.setOnClickListener(null);
		if(showUserTweetsButton!=null) showUserTweetsButton.setOnClickListener(null);

		
		unbindDrawables(findViewById(R.id.showUserRoot));

		if(c!=null){
			if(observer != null) c.unregisterContentObserver(observer);
			c.close();
		}
		observer = null;
		handler = null;
		
	}


	/**
	 * Populate the Options menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		super.onCreateOptionsMenu(menu);
		menu.add(1, OPTIONS_MENU_HOME, 1, "Home");
		return true;
	}

	/**
	 * Handle options menu selection
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item){

		Intent i;
		switch(item.getItemId()){

		case OPTIONS_MENU_HOME:
			// show the timeline
			i = new Intent(this, ShowTweetListActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
			break;
		default:
			return false;
		}
		return true;
	}

	/**
	 * Compare the argument with the local user ID.
	 * @param userString
	 * @return
	 */
	private boolean isLocalUser(String userString) {
		String localUserString = LoginActivity.getTwitterId(this);
		return userString.equals(localUserString);
	}
	
	/**
	 * Fills the views
	 */
	private void showUserInfo(){
		/*
		 * User info
		 */

		// do we have a profile image?
		if(!c.isNull(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE))){
			byte[] bb = c.getBlob(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE));
			profileImage.setImageBitmap(BitmapFactory.decodeByteArray(bb, 0, bb.length));
		}
		userScreenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME)); 
		screenName.setText(userScreenName);
		realName.setText(c.getString(c.getColumnIndex(TwitterUsers.COL_NAME)));

		if(c.getColumnIndex(TwitterUsers.COL_LOCATION) >=0){
			location.setText(c.getString(c.getColumnIndex(TwitterUsers.COL_LOCATION)));
			location.setVisibility(TextView.VISIBLE);
		} else {
			location.setVisibility(TextView.GONE);
		}

		if(c.getColumnIndex(TwitterUsers.COL_DESCRIPTION) >=0){
			String tmp = c.getString(c.getColumnIndex(TwitterUsers.COL_DESCRIPTION));
			if(tmp != null){
				description.setText(tmp);
				description.setVisibility(TextView.VISIBLE);
			} else {
				description.setVisibility(TextView.GONE);
			}
		} else {
			description.setVisibility(TextView.GONE);
		}

		int tweets = c.getInt(c.getColumnIndex(TwitterUsers.COL_STATUSES));
		int favorites = c.getInt(c.getColumnIndex(TwitterUsers.COL_FAVORITES));
		int follows = c.getInt(c.getColumnIndex(TwitterUsers.COL_FRIENDS));
		int followed = c.getInt(c.getColumnIndex(TwitterUsers.COL_FOLLOWERS));

		stats.setText(Html.fromHtml("<b>@"+userScreenName+"</b> has <b>tweeted " +tweets+ "</b> times, and <b>favorited "+favorites+"</b> tweets. They <b>follow "+follows+"</b> users and are <b>followed by "+followed+"</b>."));

		// if the user we show is the local user, disable the follow button
		if(isLocalUser(Long.toString(c.getLong(c.getColumnIndex(TwitterUsers.COL_ID))))){
			showLocalUser();
		} else {
			showRemoteUser();
		}
		
		// if we have a user ID we show the recent tweets
		if(!c.isNull(c.getColumnIndex(TwitterUsers.COL_ID))){
			showUserTweetsButton.setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View v) {
					Intent i = new Intent(getBaseContext(), ShowUserTweetListActivity.class);
					i.putExtra("userId", c.getLong(c.getColumnIndex(TwitterUsers.COL_ID)));
					startActivity(i);

				}

			});
			showUserTweetsButton.setVisibility(Button.VISIBLE);
		} else {
			showUserTweetsButton.setVisibility(Button.GONE);
		}
	}

	/**
	 * Sets the user interface up to show the local user's profile
	 */
	private void showLocalUser(){
		Log.e(TAG, "LOCAL");
		// disable the normal user buttons
		LinearLayout remoteUserButtons = (LinearLayout) findViewById(R.id.showUserButtons);
		remoteUserButtons.setVisibility(LinearLayout.GONE);

		// enable the show followers and show followee's buttons
		LinearLayout localUserButtons = (LinearLayout) findViewById(R.id.showLocalUserButtons);
		localUserButtons.setVisibility(LinearLayout.VISIBLE);

		// the followers Button
		showFollowersButton.setOnClickListener(null);
		showFollowersButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Log.i(TAG, "Show followers");
				Intent i = new Intent(getBaseContext(), ShowUserListActivity.class);
				i.putExtra("filter", ShowUserListActivity.SHOW_FOLLOWERS);
				startActivity(i);

			}

		});

		// the followees Button
		showFriendsButton.setOnClickListener(null);
		showFriendsButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				Log.i(TAG, "Show friends");
				Intent i = new Intent(getBaseContext(), ShowUserListActivity.class);
				i.putExtra("filter", ShowUserListActivity.SHOW_FRIENDS);
				startActivity(i);
			}

		});
	}

	/**
	 * Sets the UI up to show a remote user (any user except for the local one)
	 */
	private void showRemoteUser(){
		flags = c.getInt(c.getColumnIndex(TwitterUsers.COL_FLAGS));
		Log.i(TAG, "flags: "+ flags);

		/*
		 * Regarding the following situation, the following cases are possible: 
		 * - the user was marked for following
		 * - the user was marked for unfollowing
		 * - we follow the user
		 * - the request to follow was sent
		 * - none of the above, we can follow the user
		 */
		following = c.getInt(c.getColumnIndex(TwitterUsers.COL_ISFRIEND))>0;
		if(following){
			followButton.setText("Unfollow");
		} else {
			followButton.setText("Follow");
		}
		// listen to clicks
		followButton.setOnClickListener(null);
		followButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				if(following){
					getContentResolver().update(uri, setUnfollowFlag(flags), null, null);
					followButton.setVisibility(Button.GONE);
					unfollowInfo.setVisibility(LinearLayout.VISIBLE);
				} else {
					getContentResolver().update(uri, setFollowFlag(flags), null, null);
					followButton.setVisibility(Button.GONE);
					followInfo.setVisibility(LinearLayout.VISIBLE);
				}
				finish();
			}

		});

		if((flags & TwitterUsers.FLAG_TO_FOLLOW)>0){
			// disable follow button
			followButton.setVisibility(Button.GONE);
			// show info that the user will be followed upon connectivity
			followInfo.setVisibility(LinearLayout.VISIBLE);
		} else if((flags & TwitterUsers.FLAG_TO_UNFOLLOW)>0){
			// disable follow button
			followButton.setVisibility(Button.GONE);
			// show info that the user will be unfollowed upon connectivity
			unfollowInfo.setVisibility(LinearLayout.VISIBLE);
		} else if(c.getInt(c.getColumnIndex(TwitterUsers.COL_FOLLOWREQUEST))>0){
			// disable follow button
			followButton.setVisibility(Button.GONE);
		}

		/*
		 * Mention button
		 */
		mentionButton.setOnClickListener(null);
		mentionButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(),NewTweetActivity.class);
				i.putExtra("text", "@"+userScreenName+" ");
				startActivity(i);
			}
		});

		/*
		 * Message button
		 */
		messageButton.setOnClickListener(null);
		messageButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(),NewDMActivity.class);
				i.putExtra("recipient", userScreenName);
				startActivity(i);
			}
		});

	}

	/**
	 * Returns content values with the to follow flag set
	 * @param flags
	 * @return
	 */
	private ContentValues setFollowFlag(int flags) {
		ContentValues cv = new ContentValues();
		// set follow flag
		cv.put(TwitterUsers.COL_FLAGS, flags | TwitterUsers.FLAG_TO_FOLLOW);
		return cv;
	}


	/**
	 * Returns content values with the to unfollow flag set
	 * @param flags
	 * @return
	 */
	private ContentValues setUnfollowFlag(int flags) {
		ContentValues cv = new ContentValues();
		// set follow flag
		cv.put(TwitterUsers.COL_FLAGS, flags | TwitterUsers.FLAG_TO_UNFOLLOW);
		return cv;
	}

	/**
	 * Clean up the views
	 * @param view
	 */
	private void unbindDrawables(View view) {
		if (view.getBackground() != null) {
			view.getBackground().setCallback(null);
		}
		if (view instanceof ViewGroup) {
			for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
				unbindDrawables(((ViewGroup) view).getChildAt(i));
			}
			try{
				((ViewGroup) view).removeAllViews();
			} catch(UnsupportedOperationException e){
				// No problem, nothing to do here
			}
		}
	}

	/**
	 * Calls showUserInfo if the user data has been updated
	 * @author thossmann
	 *
	 */
	class UserContentObserver extends ContentObserver {
		public UserContentObserver(Handler h) {
			super(h);
		}

		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}

		@Override
		public void onChange(boolean selfChange) {
			Log.d(TAG, "UserContentObserver.onChange( " + selfChange+ ")");
			super.onChange(selfChange);
			
			// close the old cursor
			if(c!=null) {
				if(observer!= null) c.unregisterContentObserver(observer);
				c.close();
			}
			
			// and get a new one
			uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + rowId);
			c = getContentResolver().query(uri, null, null, null, null);
			if(c.getCount() == 0) finish();
			c.moveToFirst();
			
			observer = new UserContentObserver(handler);
			c.registerContentObserver(observer);

			
			// update the views
			showUserInfo();

		}
	}
}
