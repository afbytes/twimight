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

import java.text.NumberFormat;

import android.content.ContentValues;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.TwitterSyncService;
import ch.ethz.twimight.net.twitter.TwitterUsers;

import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Display a user
 * 
 * @author thossmann
 * 
 */
public class UserProfileActivity extends TwimightBaseActivity {

	private static final String TAG = UserProfileActivity.class.getName();

	private static final int NO_ROW_ID = -1;

	public static final String EXTRA_KEY_TYPE = "EXTRA_KEY_TYPE";
	public static final String EXTRA_KEY_ROW_ID = "EXTRA_KEY_ROW_ID";
	public static final String EXTRA_KEY_SCREEN_NAME = "EXTRA_KEY_SCREEN_NAME";

	private Cursor mCursor;
	private Intent mRequestIntent;
	private final ContentObserver mObserver = new UserObserver(new Handler());

	// Views
	private View mContentRoot;
	private ImageView mIvProfileImage;
	private TextView mTvScreenName;
	private TextView mTvRealName;
	private TextView mTvLocation;
	private TextView mTvDescription;
	private TextView mTvStatsTweets;
	private TextView mTvStatsFavorites;
	private TextView mTvStatsFriends;
	private TextView mTvStatsFollowers;
	private Button mBtnFollow;
	private Button mBtnMention;
	private Button mBtnMessage;
	private Button mBtnShowFollowers;
	private Button mBtnShowFriends;
	private Button mBtnShowDisPeers;
	private LinearLayout mLlFollowInfo;
	private LinearLayout mLlUnfollowInfo;
	private Button mBtnShowUserTweets;

	public static boolean running = false;
	String userScreenName;
	Handler handler;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.user_profile);
		captureViews();
		mRequestIntent = getIntent();
	}

	@Override
	public void onResume() {
		super.onResume();
		initialize();
		UserProfileActivity.running = true;
	}

	@Override
	public void onPause() {
		super.onPause();
		discardCursor();
	}

	private void captureViews() {
		mContentRoot = findViewById(R.id.userProfileRootView);
		mIvProfileImage = (ImageView) findViewById(R.id.showUserProfileImage);
		mTvScreenName = (TextView) findViewById(R.id.showUserScreenName);
		mTvRealName = (TextView) findViewById(R.id.showUserRealName);
		mTvLocation = (TextView) findViewById(R.id.showUserLocation);
		mTvDescription = (TextView) findViewById(R.id.showUserDescription);
		mTvStatsTweets = (TextView) findViewById(R.id.statsTweets);
		mTvStatsFavorites = (TextView) findViewById(R.id.statsFavorites);
		mTvStatsFriends = (TextView) findViewById(R.id.statsFriends);
		mTvStatsFollowers = (TextView) findViewById(R.id.statsFollowers);
		mBtnFollow = (Button) findViewById(R.id.showUserFollow);
		mBtnMention = (Button) findViewById(R.id.showUserMention);
		mBtnMessage = (Button) findViewById(R.id.showUserMessage);
		mLlFollowInfo = (LinearLayout) findViewById(R.id.showUserToFollow);
		mLlUnfollowInfo = (LinearLayout) findViewById(R.id.showUserToUnfollow);
		mBtnShowFollowers = (Button) findViewById(R.id.showUserFollowers);
		mBtnShowFriends = (Button) findViewById(R.id.showUserFriends);
		mBtnShowDisPeers = (Button) findViewById(R.id.showUserDisasterPeers);
		mBtnShowUserTweets = (Button) findViewById(R.id.showUserTweetsButton);
	}

	/**
	 * Initiates the setup of the activity for the user specified in the given
	 * intent.
	 * 
	 * @param intent
	 *            intent with extras that specify the requested user either by
	 *            row id or screen name
	 */
	private void initialize() {
		showContent(false);
		removeOnClickListeners();
		updateCursor();
		requestUserRefresh();
	}

	/**
	 * Shows or hides the content of the activity. This is used to hide the
	 * placeholder content in case the requested user has to be loaded first.
	 * 
	 * @param show
	 */
	private void showContent(boolean show) {
		mContentRoot.setVisibility(show ? View.VISIBLE : View.GONE);
	}

	/**
	 * Tries to load a cursor for the user based on the provided information. If
	 * successful, a content update is triggered.
	 * 
	 */
	private void updateCursor() {
		discardCursor();
		long mRequestedRowId = mRequestIntent.getLongExtra(EXTRA_KEY_ROW_ID, NO_ROW_ID);
		String mRequestedScreenName = mRequestIntent.getStringExtra(EXTRA_KEY_SCREEN_NAME);
		if (mRequestedRowId != NO_ROW_ID) {
			// load by row id
			Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS
					+ "/" + mRequestedRowId);
			mCursor = getContentResolver().query(uri, null, null, null, null);

		} else if (mRequestedScreenName != null) {
			// load by screen name
			Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
			mCursor = getContentResolver().query(uri, null, TwitterUsers.COL_SCREEN_NAME + " like ?",
					new String[] { mRequestedScreenName }, null);
			// user not in DB? -> load from twitter
			if (mCursor != null && mCursor.getCount() == 0) {
				Intent loadUserIntent = new Intent(this, TwitterSyncService.class);
				loadUserIntent.putExtra(TwitterSyncService.EXTRA_KEY_ACTION,
						TwitterSyncService.EXTRA_ACTION_LOAD_USER_BY_SCREEN_NAME);
				loadUserIntent.putExtra(TwitterSyncService.EXTRA_KEY_SCREEN_NAME, mRequestedScreenName);
				startService(loadUserIntent);
			}
		}
		if (mCursor != null) {
			mCursor.registerContentObserver(mObserver);
			updateContent();
		} else {
			finish();
		}
	}

	private void discardCursor() {
		if (mCursor != null) {
			mCursor.unregisterContentObserver(mObserver);
			mCursor.close();
			mCursor = null;
		}
	}

	/**
	 * Triggers a refresh of the requested user. This is done once at the start
	 * of the activity if the user is already in the DB (i.e. if the user is
	 * requested by row id as opposed to screen name) to make sure that the
	 * latest info is shown eventually.
	 */
	private void requestUserRefresh() {
		if (mCursor != null && mCursor.getCount() > 0) {
			mCursor.moveToFirst();
			long rowId = mCursor.getLong(mCursor.getColumnIndex(TwitterUsers.COL_ROW_ID));
			Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS
					+ "/" + rowId);
			ContentValues cv = new ContentValues();
			cv.put(TwitterUsers.COL_FLAGS,
					TwitterUsers.FLAG_TO_UPDATE | mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_FLAGS)));
			getContentResolver().update(uri, cv, null, null);

			// trigger the update
			Intent i = new Intent(getBaseContext(), TwitterSyncService.class);
			i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_USER);
			i.putExtra(TwitterSyncService.EXTRA_KEY_USER_ROW_ID, rowId);
			startService(i);
		}
	}

	/**
	 * Recreates this activity when it is launched from itself. This is needed
	 * so that we can go from another user's profile to our own while using the
	 * singleTop launch mode. Standard launch mode is not suitable because then
	 * the user could create an infinite stack of own profile activities.
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		mRequestIntent = intent;
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		TwimightBaseActivity.unbindDrawables(findViewById(R.id.userProfileRootView));
	}

	private void removeOnClickListeners() {
		if (mBtnFollow != null)
			mBtnFollow.setOnClickListener(null);
		if (mBtnMention != null)
			mBtnMention.setOnClickListener(null);
		if (mBtnMessage != null)
			mBtnMessage.setOnClickListener(null);
		if (mBtnShowFollowers != null)
			mBtnShowFollowers.setOnClickListener(null);
		if (mBtnShowFriends != null)
			mBtnShowFriends.setOnClickListener(null);
		if (mBtnShowUserTweets != null)
			mBtnShowUserTweets.setOnClickListener(null);
	}

	@Override
	protected void onStop() {
		super.onStop();
		running = false;
	}

	/**
	 * Compare the argument with the local user ID.
	 * 
	 * @param userString
	 * @return
	 */
	private boolean isLocalUser(String userString) {
		String localUserString = LoginActivity.getTwitterId(this);
		return userString.equals(localUserString);
	}

	/**
	 * Fills the views according to the data from the cursor.
	 */
	private void updateContent() {
		if (mCursor != null && mCursor.getCount() > 0) {
			showContent(true);
			mCursor.moveToFirst();
			// set image
			mIvProfileImage.setImageResource(R.drawable.profile_image_placeholder);
			String imageUri = mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_PROFILE_IMAGE_URI));
			ImageLoader.getInstance().displayImage(imageUri, mIvProfileImage);
			// set names
			userScreenName = mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_SCREEN_NAME));
			mTvScreenName.setText("@" + userScreenName);
			mTvRealName.setText(mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_NAME)));

			if (mCursor.getColumnIndex(TwitterUsers.COL_LOCATION) >= 0) {
				mTvLocation.setText(mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_LOCATION)));
				mTvLocation.setVisibility(TextView.VISIBLE);
			} else {
				mTvLocation.setVisibility(TextView.GONE);
			}

			if (mCursor.getColumnIndex(TwitterUsers.COL_DESCRIPTION) >= 0) {
				String tmp = mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_DESCRIPTION));
				if (tmp != null) {
					mTvDescription.setText(tmp);
					mTvDescription.setVisibility(TextView.VISIBLE);
				} else {
					mTvDescription.setVisibility(TextView.GONE);
				}
			} else {
				mTvDescription.setVisibility(TextView.GONE);
			}

			int tweets = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_STATUSES));
			int favorites = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_FAVORITES));
			int follows = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_FRIENDS));
			int followed = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_FOLLOWERS));

			NumberFormat numberFormat = NumberFormat.getInstance();
			mTvStatsTweets.setText(numberFormat.format(tweets));
			mTvStatsFavorites.setText(numberFormat.format(favorites));
			mTvStatsFriends.setText(numberFormat.format(follows));
			mTvStatsFollowers.setText(numberFormat.format(followed));

			// if the user we show is the local user, disable the follow button
			if (isLocalUser(Long.toString(mCursor.getLong(mCursor.getColumnIndex(TwitterUsers.COL_TWITTER_USER_ID))))) {
				showLocalUser();
			} else {
				showRemoteUser();
			}

			// if we have a user ID we show the recent tweets
			if (!mCursor.isNull(mCursor.getColumnIndex(TwitterUsers.COL_TWITTER_USER_ID))) {
				mBtnShowUserTweets.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						Intent i = new Intent(getBaseContext(), UserTweetListActivity.class);
						mCursor.moveToFirst();
						int index = mCursor.getColumnIndex(TwitterUsers.COL_TWITTER_USER_ID);
						if (index != -1) {
							i.putExtra(UserTweetListActivity.EXTRA_KEY_USER_ROW_ID, mCursor.getLong(index));
							startActivity(i);
						}
					}
				});
				mBtnShowUserTweets.setVisibility(Button.VISIBLE);
			} else {
				mBtnShowUserTweets.setVisibility(Button.GONE);
			}
		}
	}

	/**
	 * Sets the user interface up to show the local user's profile
	 */
	private void showLocalUser() {

		// hide the normal user buttons
		mBtnFollow.setVisibility(View.GONE);
		mBtnMention.setVisibility(View.GONE);
		mBtnMessage.setVisibility(View.GONE);

		// hide info messages
		mLlFollowInfo.setVisibility(LinearLayout.GONE);
		mLlUnfollowInfo.setVisibility(LinearLayout.GONE);

		// the followers Button
		mBtnShowFollowers.setVisibility(View.VISIBLE);
		mBtnShowFollowers.setOnClickListener(null);
		mBtnShowFollowers.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(), UserListActivity.class);
				i.putExtra(UserListActivity.EXTRA_KEY_INITIAL_TAB, UserListActivity.EXTRA_INITIAL_TAB_FOLLOWERS);
				startActivity(i);

			}

		});

		// the followees Button
		mBtnShowFriends.setVisibility(View.VISIBLE);
		mBtnShowFriends.setOnClickListener(null);
		mBtnShowFriends.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(), UserListActivity.class);
				i.putExtra(UserListActivity.EXTRA_KEY_INITIAL_TAB, UserListActivity.EXTRA_INITIAL_TAB_FOLLOWING);
				startActivity(i);
			}

		});

		mBtnShowDisPeers.setVisibility(View.VISIBLE);
		mBtnShowDisPeers.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {

				Intent i = new Intent(getBaseContext(), UserListActivity.class);
				i.putExtra(UserListActivity.EXTRA_KEY_INITIAL_TAB, UserListActivity.EXTRA_INITIAL_TAB_PEERS);
				startActivity(i);

			}

		});
	}

	/**
	 * Sets the UI up to show a remote user (any user except for the local one)
	 */
	private void showRemoteUser() {
		// hide buttons specific to local use
		mBtnShowFollowers.setVisibility(View.GONE);
		mBtnShowFriends.setVisibility(View.GONE);
		mBtnShowDisPeers.setVisibility(View.GONE);

		int flags = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_FLAGS));
		/*
		 * The following cases are possible: - the user was marked for following
		 * - the user was marked for unfollowing - we follow the user - the
		 * request to follow was sent - none of the above, we can follow the
		 * user
		 */
		boolean following = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_IS_FRIEND)) > 0;
		if (following) {
			mBtnFollow.setText(R.string.unfollow);
		} else {
			mBtnFollow.setText(R.string.follow);
		}
		// listen to clicks
		mBtnFollow.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				long rowId = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_ROW_ID));
				Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
						+ TwitterUsers.TWITTERUSERS + "/" + rowId);
				int flags = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_FLAGS));
				boolean following = mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_IS_FRIEND)) > 0;
				if (following) {
					getContentResolver().update(uri, setUnfollowFlag(flags), null, null);
					mBtnFollow.setVisibility(Button.GONE);
					mLlUnfollowInfo.setVisibility(LinearLayout.VISIBLE);
					following = false;
				} else {
					getContentResolver().update(uri, setFollowFlag(flags), null, null);
					mBtnFollow.setVisibility(Button.GONE);
					mLlFollowInfo.setVisibility(LinearLayout.VISIBLE);
					following = true;
				}

				// trigger the update
				Intent i = new Intent(getBaseContext(), TwitterSyncService.class);
				i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_USER);
				i.putExtra(TwitterSyncService.EXTRA_KEY_USER_ROW_ID, rowId);
				startService(i);
			}
		});

		if ((flags & TwitterUsers.FLAG_TO_FOLLOW) > 0) {
			// disable follow button
			mBtnFollow.setVisibility(Button.GONE);
			// show info that the user will be followed upon connectivity
			mLlFollowInfo.setVisibility(LinearLayout.VISIBLE);
		} else {
			// disable follow button
			mBtnFollow.setVisibility(Button.VISIBLE);
			// show info that the user will be followed upon connectivity
			mLlFollowInfo.setVisibility(LinearLayout.GONE);
		}

		if ((flags & TwitterUsers.FLAG_TO_UNFOLLOW) > 0) {
			// disable follow button
			mBtnFollow.setVisibility(Button.GONE);
			// show info that the user will be unfollowed upon connectivity
			mLlUnfollowInfo.setVisibility(LinearLayout.VISIBLE);
		} else {
			// disable follow button
			mBtnFollow.setVisibility(Button.VISIBLE);
			// show info that the user will be unfollowed upon connectivity
			mLlUnfollowInfo.setVisibility(LinearLayout.GONE);
		}

		if (mCursor.getInt(mCursor.getColumnIndex(TwitterUsers.COL_FOLLOW_REQUEST)) > 0) {
			// disable follow button
			mBtnFollow.setVisibility(Button.GONE);
		}

		/*
		 * Mention button
		 */
		mBtnMention.setOnClickListener(null);
		mBtnMention.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(), ComposeTweetActivity.class);
				i.putExtra(ComposeTweetActivity.EXTRA_KEY_TEXT, "@" + userScreenName + " ");
				startActivity(i);
			}
		});

		/*
		 * Message button
		 */
		mBtnMessage.setOnClickListener(null);
		mBtnMessage.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(), ComposeDmActivity.class);
				i.putExtra(ComposeDmActivity.EXTRA_KEY_RECIPIENT_SCREEN_NAME, userScreenName);
				startActivity(i);
			}
		});

	}

	/**
	 * Returns content values with the to follow flag set
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues setFollowFlag(int flags) {
		ContentValues cv = new ContentValues();
		flags = flags & (~TwitterUsers.FLAG_TO_UNFOLLOW);
		// set follow flag
		cv.put(TwitterUsers.COL_FLAGS, flags | TwitterUsers.FLAG_TO_FOLLOW);
		return cv;
	}

	/**
	 * Returns content values with the to unfollow flag set
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues setUnfollowFlag(int flags) {
		ContentValues cv = new ContentValues();
		flags = flags & (~TwitterUsers.FLAG_TO_FOLLOW);
		// set follow flag
		cv.put(TwitterUsers.COL_FLAGS, flags | TwitterUsers.FLAG_TO_UNFOLLOW);
		return cv;
	}

	/**
	 * Calls showUserInfo if the user data has been updated
	 * 
	 * @author thossmann
	 * 
	 */
	class UserObserver extends ContentObserver {
		public UserObserver(Handler h) {
			super(h);
		}

		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			updateCursor();
		}
	}
}
