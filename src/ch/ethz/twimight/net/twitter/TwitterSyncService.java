package ch.ethz.twimight.net.twitter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import twitter4j.DirectMessage;
import twitter4j.GeoLocation;
import twitter4j.PagableResponseList;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.bluetest.credentials.Obfuscator;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.TweetListActivity;
import ch.ethz.twimight.activities.TwimightBaseActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.InternalStorageHelper;
import ch.ethz.twimight.util.Serialization;

public class TwitterSyncService extends IntentService {
	private static final String TAG = "TwitterSyncService";

	static final int MAX_LOAD_ATTEMPTS = 2;

	public static final String EXTRA_ACTION = "EXTRA_ACTION";
	public static final String EXTRA_ACTION_LOGIN = "EXTRA_ACTION_LOGIN";
	public static final String EXTRA_ACTION_SYNC_TIMELINE = "EXTRA_ACTION_SYNC_TIMELINE";
	public static final String EXTRA_ACTION_SYNC_FAVORITES = "EXTRA_ACTION_SYNC_FAVORITES";
	public static final String EXTRA_ACTION_SYNC_TWEET = "EXTRA_ACTION_SYNC_TWEET";
	public static final String EXTRA_ACTION_SYNC_MENTIONS = "EXTRA_ACTION_SYNC_MENTIONS";
	public static final String EXTRA_ACTION_SYNC_MESSAGES = "EXTRA_ACTION_SYNC_MESSAGES";
	public static final String EXTRA_ACTION_SYNC_TRANSACTIONAL_MESSAGES = "EXTRA_ACTION_SYNC_TRANSACTIONAL_MESSAGES";
	public static final String EXTRA_ACTION_SYNC_FRIENDS = "EXTRA_ACTION_SYNC_FRIENDS";
	public static final String EXTRA_ACTION_SYNC_FOLLOWERS = "EXTRA_ACTION_SYNC_FOLLOWERS";
	public static final String EXTRA_ACTION_SYNC_USER = "EXTRA_ACTION_SYNC_USER";
	public static final String EXTRA_ACTION_SYNC_USER_TWEETS = "EXTRA_ACTION_SYNC_USER_TWEETS";
	public static final String EXTRA_ACTION_SEARCH_TWEET = "EXTRA_ACTION_SEARCH_TWEET";
	public static final String EXTRA_ACTION_SEARCH_USER = "EXTRA_ACTION_SEARCH_USER";
	public static final String EXTRA_ACTION_SYNC_ALL_TRANSACTIONAL = "EXTRA_ACTION_SYNC_ALL_TRANSACTIONAL";

	public static final String EXTRA_TWEET_SEARCH_QUERY = "tweet_search_query";
	private static final int MAX_USER_SEARCH_PAGES = 5;
	public static final String EXTRA_USER_SEARCH_QUERY = "user_search_query";
	public static final String EXTRA_USER_ROW_ID = "user_row_id";
	public static final String EXTRA_TWEET_ROW_ID = "tweet_row_id";
	public static final String EXTRA_SCREEN_NAME = "screen_name";

	public static final String EXTRA_FORCE_SYNC = "force_sync";

	public static final String EXTRA_TIMELINE_UPDATE_DIRECTION = "update_direction";
	public static final int TIMELINE_UPDATE_DIRECTION_UP = 1;
	public static final int TIMELINE_UPDATE_DIRECTION_DOWN = 2;

	private static final String PREF_LAST_TIMELINE_UPDATE = "last_timeline_update";
	private static final String PREF_TIMELINE_SINCE_ID = "timeline_since_id";
	private static final String PREF_LAST_MENTIONS_UPDATE = "last_mentions_update";
	private static final String PREF_MENTIONS_SINCE_ID = "mentions_since_id";
	private static final String PREF_LAST_FAVORITES_UPDATE = "last_favorites_update";
	private static final String PREF_FAVORITES_SINCE_ID = "favorites_since_id";
	private static final String PREF_LAST_FRIENDS_UPDATE = "last_friends_update";
	private static final int FRIENDS_PAGES_TO_LOAD = 5;
	private static final String PREF_LAST_FOLLOWERS_UPDATE = "last_followers_update";
	private static final int FOLLOWERS_PAGES_TO_LOAD = 5;
	private static final String PREF_LAST_INCOMING_DMS_UPDATE = "last_incoming_dms_update";
	private static final String PREF_LAST_OUTGOING_DMS_UPDATE = "last_outgoing_dms_update";
	private static final String PREF_INCOMING_DMS_SINCE_ID = "incoming_dms_since_id";
	private static final String PREF_OUTGOING_DMS_SINCE_ID = "outgoing_dms_since_id";
	private static final long DMS_MIN_SYNCH_INTERVAL = 20 * 1000L;

	/**
	 * Main thread handler for posting toasts
	 */
	private Handler mHandler;

	Twitter mTwitter;
	Intent mStartIntent;

	public TwitterSyncService() {
		super(TwitterSyncService.class.getCanonicalName());
	}

	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
	}

	@Override
	public void onHandleIntent(Intent intent) {
		// if we have no connectivity -> do nothing
		if (isDisconnected()) {
			return;
		}
		// initialize Ttwitter object
		mTwitter = getConfiguredTwitter();
		if (mTwitter == null) {
			return;
		}
		mStartIntent = intent;
		TwimightBaseActivity.setLoading(true);
		String action = intent.getExtras().getString(EXTRA_ACTION);
		Log.d(TAG, "TwitterSyncService onHandleIntent() ACTION=" + action);
		if (EXTRA_ACTION_LOGIN.equals(action)) {
			login();
		} else if (EXTRA_ACTION_SYNC_TIMELINE.equals(action)) {
			syncTimeline();
		} else if (EXTRA_ACTION_SYNC_TWEET.equals(action)) {
			syncTweet();
		} else if (EXTRA_ACTION_SYNC_MENTIONS.equals(action)) {
			syncMentions();
		} else if (EXTRA_ACTION_SYNC_FAVORITES.equals(action)) {
			syncFavorites();
		} else if (EXTRA_ACTION_SYNC_MESSAGES.equals(action)) {
			syncMessages();
		} else if (EXTRA_ACTION_SYNC_TRANSACTIONAL_MESSAGES.equals(action)) {
			syncTransactionalMessages();
		} else if (EXTRA_ACTION_SYNC_FRIENDS.equals(action)) {
			syncFriends();
		} else if (EXTRA_ACTION_SYNC_FOLLOWERS.equals(action)) {
			syncFollowers();
		} else if (EXTRA_ACTION_SYNC_USER.equals(action)) {
			syncUser();
		} else if (EXTRA_ACTION_SYNC_USER_TWEETS.equals(action)) {
			syncUserTweets();
		} else if (EXTRA_ACTION_SEARCH_TWEET.equals(action)) {
			searchTweet();
		} else if (EXTRA_ACTION_SEARCH_USER.equals(action)) {
			searchUser();
		} else if (EXTRA_ACTION_SYNC_ALL_TRANSACTIONAL.equals(action)) {
			syncAllTransactional();
		} else {
			Log.e(TAG, "TwitterSyncService started with no valid action!");
		}
		TwimightBaseActivity.setLoading(false);
		Log.d(TAG, "TwitterSyncService onHandleIntent() done");
	}

	private boolean isDisconnected() {
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo currentNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return (currentNetworkInfo == null || !currentNetworkInfo.isConnected());
	}

	/**
	 * A runnable for creating toasts on the main thread.
	 */
	private class DisplayToast implements Runnable {
		private final Context mContext;
		private final String mText;

		private DisplayToast(Context context, String text) {
			mContext = context;
			mText = text;
		}

		@Override
		public void run() {
			Toast.makeText(mContext, mText, Toast.LENGTH_SHORT).show();
		}
	}

	void makeToast(String text) {
		mHandler.post(new DisplayToast(this, text));
	}

	/**
	 * Reads the timestamp of the last update of the given preference from
	 * shared preferences.
	 * 
	 * @param preferenceName
	 *            the name of the preference to look up
	 * @return the timestamp of the last mentions update or 0 if no value is
	 *         found for the given preference name
	 */
	long getLastUpdate(String preferenceName) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		return prefs.getLong(preferenceName, 0);
	}

	public static void clearAllPreferences(Context context) {
		setLastUpdate(context, PREF_FAVORITES_SINCE_ID, null);
		setSinceId(context, PREF_FAVORITES_SINCE_ID, null);

		setLastUpdate(context, PREF_LAST_MENTIONS_UPDATE, null);
		setSinceId(context, PREF_MENTIONS_SINCE_ID, null);

		setLastUpdate(context, PREF_LAST_TIMELINE_UPDATE, null);
		setSinceId(context, PREF_TIMELINE_SINCE_ID, null);

		setLastUpdate(context, PREF_LAST_FAVORITES_UPDATE, null);
		setSinceId(context, PREF_FAVORITES_SINCE_ID, null);

		setLastUpdate(context, PREF_LAST_FRIENDS_UPDATE, null);

		setLastUpdate(context, PREF_LAST_FOLLOWERS_UPDATE, null);

		setLastUpdate(context, PREF_LAST_INCOMING_DMS_UPDATE, null);
		setLastUpdate(context, PREF_LAST_OUTGOING_DMS_UPDATE, null);
		setSinceId(context, PREF_INCOMING_DMS_SINCE_ID, null);
		setSinceId(context, PREF_OUTGOING_DMS_SINCE_ID, null);

	}

	private static long getSinceId(Context context, String preferenceName) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(preferenceName, 1);
	}

	/**
	 * Stores the given ID as the since ID
	 */
	private static void setSinceId(Context context, String preferenceName, Long sinceId) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		if (sinceId != null) {
			prefEditor.putLong(preferenceName, sinceId);
		} else {
			prefEditor.remove(preferenceName);
		}
		prefEditor.commit();
	}

	/**
	 * Stores the given timestamp as the time of the last update of the given
	 * preference.
	 * 
	 * @param timestamp
	 *            the timestamp
	 * @param context
	 *            a context
	 */
	private static void setLastUpdate(Context context, String preferenceName, Long timestamp) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		if (timestamp != null) {
			prefEditor.putLong(preferenceName, timestamp);
		} else {
			prefEditor.remove(preferenceName);
		}
		prefEditor.commit();
	}

	/**
	 * Creates a Twitter object initialized with our keys, secrets, and tokens.
	 * 
	 * @return the initialized Twitter object or null if token or secret are
	 *         missing
	 */
	private Twitter getConfiguredTwitter() {
		String token = LoginActivity.getAccessToken(this);
		String secret = LoginActivity.getAccessTokenSecret(this);
		Twitter twitter = null;
		if (token != null || secret != null) {
			ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
			configurationBuilder.setOAuthConsumerKey(Obfuscator.getKey());
			configurationBuilder.setOAuthConsumerSecret(Obfuscator.getSecret());
			configurationBuilder.setOAuthAccessToken(token);
			configurationBuilder.setOAuthAccessTokenSecret((secret));
			Configuration configuration = configurationBuilder.build();
			twitter = new TwitterFactory(configuration).getInstance();
		}
		return twitter;
	}

	/**
	 * Creates content values for a tweet from Twitter
	 * 
	 * @param tweet
	 *            the original tweet object
	 * @param ret_screenName
	 * @return
	 */
	private ContentValues getTweetContentValues(Status tweet, int buffer) {
		ContentValues cv = new ContentValues();

		if (tweet == null || tweet.getText() == null) {
			return null;
		}

		if (tweet.isRetweet()) {
			cv.put(Tweets.COL_RETWEETED_BY, tweet.getUser().getName());
			tweet = tweet.getRetweetedStatus();
		}

		// prepare a version of the tweet text that has all the urls replaced
		// with their display versions
		EntityQueue allUrlEntities = new EntityQueue(tweet.getURLEntities(), tweet.getMediaEntities());
		StringBuilder augmentedTweetText = new StringBuilder(tweet.getText());
		while (!allUrlEntities.isEmpty()) {
			URLEntity urlEntity = (URLEntity) allUrlEntities.remove();
			augmentedTweetText.replace(urlEntity.getStart(), urlEntity.getEnd(), urlEntity.getDisplayURL());
		}
		cv.put(Tweets.COL_TEXT, augmentedTweetText.toString());
		// the original version
		cv.put(Tweets.COL_TEXT_PLAIN, tweet.getText());

		cv.put(Tweets.COL_HASHTAG_ENTITIES, Serialization.serialize(tweet.getHashtagEntities()));
		cv.put(Tweets.COL_MEDIA_ENTITIES, Serialization.serialize(tweet.getMediaEntities()));
		cv.put(Tweets.COL_URL_ENTITIES, Serialization.serialize(tweet.getURLEntities()));
		cv.put(Tweets.COL_USER_MENTION_ENTITIES, Serialization.serialize(tweet.getUserMentionEntities()));

		// if there are urls to this tweet, change the status of html field to 1
		if (tweet.getURLEntities().length > 0) {
			cv.put(Tweets.COL_HTML_PAGES, 1);

			boolean isOfflineActive = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
					this.getString(R.string.pref_offline_mode), false);
			if (isOfflineActive) {
				new CacheUrlTask(tweet).execute();
			}
		}
		// if there are mentions we load the users
		for (UserMentionEntity userMentionEntity : tweet.getUserMentionEntities()) {
			ContentValues mentionedUserValues = new ContentValues();
			mentionedUserValues.put(TwitterUsers.COL_NAME, userMentionEntity.getName());
			mentionedUserValues.put(TwitterUsers.COL_SCREENNAME, userMentionEntity.getScreenName());
			mentionedUserValues.put(TwitterUsers.COL_FLAGS, TwitterUsers.FLAG_TO_UPDATE
					| TwitterUsers.FLAG_TO_UPDATEIMAGE);
			Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS);
			getContentResolver().insert(insertUri, mentionedUserValues);
			Log.d(TAG,
					"mention: inserted @" + userMentionEntity.getScreenName() + " name: " + userMentionEntity.getName());
		}

		cv.put(Tweets.COL_CREATED, tweet.getCreatedAt().getTime());
		cv.put(Tweets.COL_SOURCE, tweet.getSource());

		cv.put(Tweets.COL_TID, tweet.getId());

		if (tweet.isFavorited())
			buffer = buffer | Tweets.BUFFER_FAVORITES;

		// TODO: How do we know if we have retweeted the tweet?
		cv.put(Tweets.COL_RETWEETED, 0);
		cv.put(Tweets.COL_RETWEETCOUNT, tweet.getRetweetCount());
		if (tweet.getInReplyToStatusId() != -1) {
			cv.put(Tweets.COL_REPLYTO, tweet.getInReplyToStatusId());
			cv.put(Tweets.COL_REPLY_TO_USER_ID, tweet.getInReplyToUserId());
			cv.put(Tweets.COL_REPLYTO, tweet.getInReplyToScreenName());
		}
		cv.put(Tweets.COL_TWITTERUSER, tweet.getUser().getId());
		cv.put(Tweets.COL_SCREENNAME, tweet.getUser().getScreenName());
		cv.put(Tweets.COL_BUFFER, buffer);

		return cv;
	}

	/**
	 * Creates content values for a user from Twitter. Flags the user for
	 * updating their profile picture.
	 * 
	 * @param user
	 * @return
	 */
	private ContentValues getUserContentValues(User user) {
		ContentValues userContentValues = null;
		if (user != null && user.getScreenName() != null) {
			userContentValues = new ContentValues();
			userContentValues.put(TwitterUsers.COL_TWITTERUSER_ID, user.getId());
			userContentValues.put(TwitterUsers.COL_SCREENNAME, user.getScreenName());
			userContentValues.put(TwitterUsers.COL_NAME, user.getName());
			if (user.getDescription() != null)
				userContentValues.put(TwitterUsers.COL_DESCRIPTION, user.getDescription());
			if (user.getLocation() != null)
				userContentValues.put(TwitterUsers.COL_LOCATION, user.getLocation());
			userContentValues.put(TwitterUsers.COL_FAVORITES, user.getFavouritesCount());
			userContentValues.put(TwitterUsers.COL_FRIENDS, user.getFriendsCount());
			userContentValues.put(TwitterUsers.COL_FOLLOWERS, user.getFollowersCount());
			userContentValues.put(TwitterUsers.COL_LISTED, user.getListedCount());
			userContentValues.put(TwitterUsers.COL_TIMEZONE, user.getTimeZone());
			userContentValues.put(TwitterUsers.COL_STATUSES, user.getStatusesCount());
			userContentValues.put(TwitterUsers.COL_VERIFIED, user.isVerified());
			userContentValues.put(TwitterUsers.COL_PROTECTED, user.isProtected());
			if (user.getProfileImageURL() != null) {
				ProfileImageVariant desiredVariant = ProfileImageVariant.BIGGER;
				String receivedUrl = user.getProfileImageURL().toString();
				String desiredUrl = ProfileImageVariant.getVariantUrl(receivedUrl, desiredVariant);
				userContentValues.put(TwitterUsers.COL_IMAGEURL, desiredUrl);
				// we flag the user for updating their profile image
				userContentValues.put(TwitterUsers.COL_FLAGS, TwitterUsers.FLAG_TO_UPDATEIMAGE);
			}
		}
		return userContentValues;
	}

	/**
	 * Updates the given user in the databse
	 * 
	 * @param user
	 *            the user to update
	 * @param insertAsFriend
	 *            will set the friend flag if true
	 */
	private void storeUser(ContentValues userValues) {
		if (userValues != null) {
			Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS);
			getContentResolver().insert(insertUri, userValues);
		}
	}

	/**
	 * Bulk updates a tweets in the DB (or inserts it if the tweet is new to
	 * us).
	 * 
	 * @param tweetsValues
	 * @return the number of updated entries
	 */
	private int storeTweets(ContentValues[] tweetsValues) {
		int updateCount = 0;
		if (tweetsValues != null) {
			Uri insertUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_NORMAL);
			updateCount = getContentResolver().bulkInsert(insertUri, tweetsValues);
		}
		getContentResolver().notifyChange(Tweets.TABLE_TIMELINE_URI, null);
		return updateCount;
	}

	/**
	 * Updates the user profile in the DB.
	 * 
	 * @param contentValues
	 * @param user
	 */
	private long storeUsers(ContentValues[] userValues) {
		int updateCount = 0;
		if (userValues != null) {
			Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS);
			updateCount = getContentResolver().bulkInsert(insertUri, userValues);
		}
		return updateCount;
	}

	/**
	 * Looks up all the users that have a flag set and triggers the necessary
	 * sync actions for each.
	 */
	private void syncTransactionalUsers() {
		Log.d(TAG, "syncTransactionalUsers()");
		// get the flagged users
		Uri queryUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
		Cursor c = getContentResolver().query(queryUri, null, TwitterUsers.COL_FLAGS + "!=0", null, null);
		if (c != null && c.getCount() > 0) {
			boolean profileImageUpdateNotificationNeeded = false;
			ExecutorService executorService = Executors.newCachedThreadPool();
			List<Future<ContentValues>> futureResults = new LinkedList<Future<ContentValues>>();
			while (c.moveToNext()) {
				Callable<ContentValues> syncTask = getUserSyncTask(c, false);
				if (syncTask != null) {
					Future<ContentValues> futureResult = executorService.submit(syncTask);
					futureResults.add(futureResult);
				}
				if (syncTask instanceof ProfileImageUpdateTask) {
					profileImageUpdateNotificationNeeded = true;
				}
			}
			executorService.shutdown();
			try {
				// wait for tasks to finish
				executorService.awaitTermination(60, TimeUnit.SECONDS);
				// gathe results and insert them
				List<ContentValues> results = new LinkedList<ContentValues>();
				for (Future<ContentValues> futureResult : futureResults) {
					try {
						ContentValues result = futureResult.get();
						results.add(result);
					} catch (ExecutionException e) {
						e.printStackTrace();
					}
				}
				Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
						+ TwitterUsers.TWITTERUSERS);
				getContentResolver().bulkInsert(insertUri, results.toArray(new ContentValues[results.size()]));
				getContentResolver().notifyChange(TwitterUsers.CONTENT_URI, null);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (profileImageUpdateNotificationNeeded) {
				notifyProfileImageUpdate();
			}

		}
		c.close();
	}

	private void notifyProfileImageUpdate() {
		ContentResolver contentResolver = getContentResolver();
		contentResolver.notifyChange(Tweets.TABLE_TIMELINE_URI, null);
		contentResolver.notifyChange(Tweets.TABLE_MENTIONS_URI, null);
		contentResolver.notifyChange(Tweets.TABLE_FAVORITES_URI, null);
		contentResolver.notifyChange(Tweets.TABLE_SEARCH_URI, null);
		contentResolver.notifyChange(TwitterUsers.USERS_SEARCH_URI, null);
		contentResolver.notifyChange(TwitterUsers.USERS_DISASTER_URI, null);
		contentResolver.notifyChange(TwitterUsers.USERS_FOLLOWERS_URI, null);
		contentResolver.notifyChange(TwitterUsers.USERS_FRIENDS_URI, null);
	}

	private Callable<ContentValues> getUserSyncTask(Cursor c, boolean force) {
		int flags = c.getInt(c.getColumnIndex(TwitterUsers.COL_FLAGS));
		Callable<ContentValues> userSyncTask = null;
		if ((flags & TwitterUsers.FLAG_TO_UPDATE) > 0) {
			// Update a user if it's time to do so
			if (force
					|| c.isNull(c.getColumnIndex(TwitterUsers.COL_LASTUPDATE))
					|| (System.currentTimeMillis() - c.getInt(c.getColumnIndex(TwitterUsers.COL_LASTUPDATE)) > Constants.USERS_MIN_SYNCH)) {
				userSyncTask = new UpdateUserTask(c);
			}
		} else if ((flags & TwitterUsers.FLAG_TO_FOLLOW) > 0) {
			userSyncTask = new FollowUserTask(c);
		} else if ((flags & TwitterUsers.FLAG_TO_UNFOLLOW) > 0) {
			userSyncTask = new UnfollowUserTask(c);
		} else if ((flags & TwitterUsers.FLAG_TO_UPDATEIMAGE) > 0) {
			userSyncTask = new ProfileImageUpdateTask(c);
		}
		return userSyncTask;
	}

	private class UpdateUserTask implements Callable<ContentValues> {
		private final Long mTwitterUserId;
		private final String mTwitteruserScreenName;
		private final long mRowId;
		private final int mFlags;

		private UpdateUserTask(Cursor c) {
			mTwitterUserId = c.getLong(c.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID));
			mTwitteruserScreenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME));
			mRowId = c.getLong(c.getColumnIndex(TwitterUsers.COL_ROW_ID));
			mFlags = c.getInt(c.getColumnIndex(TwitterUsers.COL_FLAGS));
		}

		@Override
		public ContentValues call() throws Exception {
			boolean success = false;
			User user = null;
			for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
				Log.d(TAG, "UpdateuserTask call() " + mTwitterUserId + " " + mTwitteruserScreenName + " attempt "
						+ attempt);
				try {
					if (mTwitterUserId != null) {
						user = mTwitter.showUser(mTwitterUserId);
					} else if (mTwitteruserScreenName != null) {
						user = mTwitter.showUser(mTwitteruserScreenName);
					}
					if (user != null) {
						success = true;
						break;
					}
				} catch (TwitterException e) {
					e.printStackTrace();
				}
			}
			// on success: insert user to DB
			ContentValues cv = null;
			if (success) {
				cv = getUserContentValues(user);
				cv.put(TwitterUsers.COL_LASTUPDATE, System.currentTimeMillis());
				cv.put(TwitterUsers.COL_FLAGS, mFlags & ~TwitterUsers.FLAG_TO_UPDATE);
				cv.put(TwitterUsers.COL_ROW_ID, mRowId);
			}
			return cv;
		}
	}

	private class FollowUserTask implements Callable<ContentValues> {

		private final Long mTwitterUserId;
		private final String mTwitteruserScreenName;
		private final long mRowId;
		private final int mFlags;

		private FollowUserTask(Cursor c) {
			mTwitterUserId = c.getLong(c.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID));
			mTwitteruserScreenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME));
			mRowId = c.getLong(c.getColumnIndex(TwitterUsers.COL_ROW_ID));
			mFlags = c.getInt(c.getColumnIndex(TwitterUsers.COL_FLAGS));
		}

		@Override
		public ContentValues call() throws Exception {
			boolean success = false;
			User user = null;
			for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
				try {
					if (mTwitterUserId != null) {
						user = mTwitter.createFriendship(mTwitterUserId);
					} else if (mTwitteruserScreenName != null) {
						user = mTwitter.createFriendship(mTwitteruserScreenName);
					}
					if (user != null) {
						success = true;
						break;
					}
				} catch (TwitterException e) {
					e.printStackTrace();
				}
			}
			// on success: insert user to DB
			ContentValues cv = null;
			if (success) {
				cv = getUserContentValues(user);
				cv.put(TwitterUsers.COL_ISFRIEND, 1);
				cv.put(TwitterUsers.COL_FLAGS, (mFlags & ~TwitterUsers.FLAG_TO_FOLLOW));
				cv.put(TwitterUsers.COL_ROW_ID, mRowId);
				// TODO: toast
			}
			return cv;
		}
	}

	private class UnfollowUserTask implements Callable<ContentValues> {

		private final Long mTwitterUserId;
		private final String mTwitteruserScreenName;
		private final long mRowId;
		private final int mFlags;

		private UnfollowUserTask(Cursor c) {
			mTwitterUserId = c.getLong(c.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID));
			mTwitteruserScreenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME));
			mRowId = c.getLong(c.getColumnIndex(TwitterUsers.COL_ROW_ID));
			mFlags = c.getInt(c.getColumnIndex(TwitterUsers.COL_FLAGS));
		}

		@Override
		public ContentValues call() throws Exception {
			boolean success = false;
			User user = null;
			for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
				try {
					if (mTwitterUserId != null) {
						user = mTwitter.destroyFriendship(mTwitterUserId);
					} else if (mTwitteruserScreenName != null) {
						user = mTwitter.destroyFriendship(mTwitteruserScreenName);
					}
					if (user != null) {
						success = true;
						break;
					}
				} catch (TwitterException e) {
					e.printStackTrace();
				}
			}
			// on success: insert user to DB
			ContentValues cv = null;
			if (success) {
				cv = getUserContentValues(user);
				cv.put(TwitterUsers.COL_ISFRIEND, 0);
				cv.put(TwitterUsers.COL_FLAGS, (mFlags & ~TwitterUsers.FLAG_TO_UNFOLLOW));
				cv.put(TwitterUsers.COL_ROW_ID, mRowId);
				// TODO: toast
			}
			return cv;
		}
	}

	private class ProfileImageUpdateTask implements Callable<ContentValues> {

		private final Integer mLastPictureUpdate;
		private final String mImageUrl;
		private final String mScreenName;
		private final long mRowId;
		private final int mFlags;

		private ProfileImageUpdateTask(Cursor c) {
			mLastPictureUpdate = c.getInt(c.getColumnIndex(TwitterUsers.COL_LAST_PICTURE_UPDATE));
			mImageUrl = c.getString(c.getColumnIndex(TwitterUsers.COL_IMAGEURL));
			mScreenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME));
			mRowId = c.getLong(c.getColumnIndex(TwitterUsers.COL_ROW_ID));
			mFlags = c.getInt(c.getColumnIndex(TwitterUsers.COL_FLAGS));
		}

		@Override
		public ContentValues call() {
			ContentValues cv = null;
			if ((System.currentTimeMillis() - mLastPictureUpdate > Constants.USERS_MIN_SYNCH)) {
				Log.d(TAG, "ProfileImageUpdateTask call() for " + mScreenName);
				// download the image
				String biggerVariantUrl = ProfileImageVariant.getVariantUrl(mImageUrl, ProfileImageVariant.BIGGER);
				byte[] image = downloadImage(biggerVariantUrl);
				// store image in file system
				String profileImagePath = null;
				if (image != null) {
					InternalStorageHelper helper = new InternalStorageHelper(TwitterSyncService.this);
					helper.writeImage(image, mScreenName);
					profileImagePath = new File(getFilesDir(), mScreenName).getPath();
				}
				// update user entry in DB
				cv = new ContentValues();
				cv.put(TwitterUsers.COL_ROW_ID, mRowId);
				cv.put(TwitterUsers.COL_FLAGS, mFlags & ~TwitterUsers.FLAG_TO_UPDATEIMAGE);
				cv.put(TwitterUsers.COL_PROFILEIMAGE_PATH, profileImagePath);
				cv.put(TwitterUsers.COL_LAST_PICTURE_UPDATE, System.currentTimeMillis());
			}
			return cv;
		}
	}

	/**
	 * Downloads the resource at the given URL
	 * 
	 * @param url
	 *            the URL of a reource accessible via HTTP
	 * @return the downloaded resource as a byte array or null if the download
	 *         fails
	 */
	private byte[] downloadImage(String url) {
		byte[] image = null;
		HttpGet httpGet = new HttpGet(url);
		HttpResponse mHttpResponse;
		DefaultHttpClient httpClient = new DefaultHttpClient();
		try {
			mHttpResponse = httpClient.execute(httpGet);
			if (mHttpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				try {
					image = EntityUtils.toByteArray(mHttpResponse.getEntity());
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return image;
	}

	/**
	 * Looks up all the direct messages that have a flag set and triggers the
	 * necessary sync actions for each.
	 */
	private void syncTransactionalMessages() {
		Log.d(TAG, "syncTransactionalMessages()");
		Uri queryUri = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS);
		Cursor c = getContentResolver().query(queryUri, null, DirectMessages.COL_FLAGS + "!=0", null, null);
		while (c.moveToNext()) {
			int flags = c.getInt(c.getColumnIndex(DirectMessages.COL_FLAGS));
			if ((flags & DirectMessages.FLAG_TO_DELETE) > 0) {
				destroyDirectMessage(c);
			} else if ((flags & DirectMessages.FLAG_TO_INSERT) > 0) {
				sendDirectMessage(c);
			}
		}
		c.close();
	}

	/**
	 * Deletes the given direct message on Twitter.
	 * 
	 * @param c
	 *            a cursor pointing to the message to delete. The cursor will
	 *            not be modified.
	 */
	private void destroyDirectMessage(Cursor c) {
		boolean success = false;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			long dmId = c.getLong(c.getColumnIndex(DirectMessages.COL_DMID));
			try {
				mTwitter.destroyDirectMessage(dmId);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			success = true;
			break;
		}
		if (success) {
			long rowId = c.getLong(c.getColumnIndex(DirectMessages.COL_ROW_ID));
			Uri deleteUri = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
					+ rowId);
			getContentResolver().delete(deleteUri, null, null);
		}
	}

	/**
	 * Sends the given direct message on Twitter.
	 * 
	 * @param c
	 *            a cursor pointing to the message to send. The cursor will not
	 *            be modified.
	 */
	private void sendDirectMessage(Cursor c) {
		boolean success = false;
		DirectMessage directMessage = null;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			String receiverScreenName = c.getString(c.getColumnIndex(DirectMessages.COL_RECEIVER_SCREENNAME));
			String text = c.getString(c.getColumnIndex(DirectMessages.COL_TEXT));
			try {
				directMessage = mTwitter.sendDirectMessage(receiverScreenName, text);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			if (directMessage != null) {
				success = true;
				break;
			}
		}
		if (success) {
			int flags = c.getInt(c.getColumnIndex(DirectMessages.COL_FLAGS));
			int buffer = c.getInt(c.getColumnIndex(DirectMessages.COL_BUFFER));
			long rowId = c.getLong(c.getColumnIndex(DirectMessages.COL_ROW_ID));
			ContentValues cv = new ContentValues();
			cv.put(DirectMessages.COL_FLAGS, flags & ~(Tweets.FLAG_TO_INSERT));
			cv.put(DirectMessages.COL_BUFFER, buffer);
			cv.put(DirectMessages.COL_TEXT, directMessage.getText());
			cv.put(DirectMessages.COL_CREATED, directMessage.getCreatedAt().getTime());
			cv.put(DirectMessages.COL_DMID, directMessage.getId());
			cv.put(DirectMessages.COL_SENDER, directMessage.getSender().getId());
			cv.put(DirectMessages.COL_RECEIVER, directMessage.getRecipient().getId());
			cv.put(DirectMessages.COL_RECEIVER_SCREENNAME, directMessage.getRecipient().getScreenName());

			Uri queryUri = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
					+ rowId);
			getContentResolver().update(queryUri, cv, null, null);
			// TODO: notify
		} else {
			// TODO: notify
		}
	}

	/**
	 * Looks up all the tweets that have a flag set and triggers the necessary
	 * sync actions for each.
	 */
	private void syncTransactionalTweets() {
		Log.d(TAG, "syncTransactionalTweets()");
		Uri queryUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS);
		Cursor c = getContentResolver().query(queryUri, null, Tweets.COL_FLAGS + "!=0", null, null);
		while (c.moveToNext()) {
			syncTweet(c, false);
		}
		c.close();
	}

	/**
	 * Performs the necessary sync actions on the given tweet.
	 * 
	 * @param c
	 *            a cursor pointing to the tweet to be synced
	 * @param notify
	 *            if true, a toast will be created to inform the user about the
	 *            outcome of each action
	 */
	private void syncTweet(Cursor c, boolean notify) {
		int flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
		if ((flags & Tweets.FLAG_TO_DELETE) > 0) {
			destroyStatus(c, notify);
		} else if ((flags & Tweets.FLAG_TO_INSERT) > 0) {
			updateStatus(c, notify);
		}
		if ((flags & Tweets.FLAG_TO_FAVORITE) > 0) {
			favoriteStatus(c, notify);
		} else if ((flags & Tweets.FLAG_TO_UNFAVORITE) > 0) {
			unfavoriteStatus(c, notify);
		}
		if ((flags & Tweets.FLAG_TO_RETWEET) > 0) {
			retweetStatus(c, notify);
		}
	}

	/**
	 * Deletes the given status on Twitter.
	 * 
	 * @param c
	 *            a cursor pointing to the status to delete. The cursor will not
	 *            be modified.
	 * @param notify
	 *            if true a toast will be created to notify the user about the
	 *            outcome
	 */
	private void destroyStatus(Cursor c, boolean notify) {
		boolean success = false;
		long statusId = c.getLong(c.getColumnIndex(Tweets.COL_TID));
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				mTwitter.destroyStatus(statusId);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			success = true;
			break;
		}
		if (success) {
			long rowId = c.getLong(c.getColumnIndex(Tweets.COL_ROW_ID));
			Uri deleteUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);
			getContentResolver().delete(deleteUri, null, null);
			if (notify) {
				makeToast(getString(R.string.tweet_deletion_success));
			}
		} else {
			if (notify) {
				makeToast(getString(R.string.tweet_deletion_failure));
			}
		}
	}

	/**
	 * Publishes the given status on Twitter.
	 * 
	 * @param c
	 *            a cursor pointing to the status to publish. The cursor will
	 *            not be modified.
	 * 
	 * @param notify
	 *            if true a toast will be created to notify the user about the
	 *            outcome
	 */
	private void updateStatus(Cursor c, boolean notify) {

		String text = c.getString(c.getColumnIndex(Tweets.COL_TEXT));
		StatusUpdate statusUpdate = new StatusUpdate(text);
		// media?
		String mediaName = c.getString(c.getColumnIndex(Tweets.COL_MEDIA));
		if (mediaName != null) {
			String mediaUrl = Environment.getExternalStoragePublicDirectory(
					Tweets.PHOTO_PATH + "/" + LoginActivity.getTwitterId(this) + "/" + mediaName).getAbsolutePath();
			File mediaFile = new File(mediaUrl);
			statusUpdate.setMedia(mediaFile);
		}
		// location?
		double latitute = c.getDouble(c.getColumnIndex(Tweets.COL_LAT));
		double longitude = c.getDouble(c.getColumnIndex(Tweets.COL_LNG));
		if (!(latitute == 0 && longitude == 0)) {
			GeoLocation location = new GeoLocation(latitute, longitude);
			statusUpdate.setLocation(location);
		}
		// is reply?
		if (c.getColumnIndex(Tweets.COL_REPLYTO) >= 0) {
			long replyToId = c.getLong(c.getColumnIndex(Tweets.COL_REPLYTO));
			statusUpdate.setInReplyToStatusId(replyToId);
		}
		// update status
		boolean success = false;
		Status tweet = null;
		for (int attempt = 0; attempt <= MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				tweet = mTwitter.updateStatus(statusUpdate);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			if (tweet != null) {
				success = true;
				break;
			}
		}

		if (success) {
			// update DB
			long rowId = c.getLong(c.getColumnIndex(Tweets.COL_ROW_ID));
			int flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
			int buffer = c.getInt(c.getColumnIndex(Tweets.COL_BUFFER));
			ContentValues cv = getTweetContentValues(tweet, 0);
			cv.put(Tweets.COL_FLAGS, flags & ~(Tweets.FLAG_TO_INSERT));
			cv.put(Tweets.COL_BUFFER, buffer);
			Uri queryUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);
			getContentResolver().update(queryUri, cv, null, null);
			if (notify) {
				makeToast(getString(R.string.status_update_success));
			}
		} else {
			if (notify) {
				makeToast(getString(R.string.status_update_failure));
			}
		}

		// TODO: notify
	}

	/**
	 * Marks the given status as favorite on Twitter.
	 * 
	 * @param c
	 *            a cursor pointing to the status to favorite. The cursor will
	 *            not be modified.
	 * @param notify
	 *            if true a toast will be created to notify the user about the
	 *            outcome
	 */
	private void favoriteStatus(Cursor c, boolean notify) {
		boolean success = false;
		long favoriteStatusId = c.getLong(c.getColumnIndex(Tweets.COL_TID));
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				mTwitter.createFavorite(favoriteStatusId);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			success = true;
			break;
		}
		// update DB
		if (success) {
			int flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
			int buffer = c.getInt(c.getColumnIndex(Tweets.COL_BUFFER));
			long rowId = c.getLong(c.getColumnIndex(Tweets.COL_ROW_ID));
			ContentValues cv = new ContentValues();
			cv.put(Tweets.COL_FLAGS, flags & ~Tweets.FLAG_TO_FAVORITE);
			cv.put(Tweets.COL_BUFFER, buffer | Tweets.BUFFER_FAVORITES);
			Uri updateUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);
			getContentResolver().update(updateUri, cv, null, null);
			if (notify) {
				makeToast(getString(R.string.favorite_status_success));
			}
		} else {
			if (notify) {
				makeToast(getString(R.string.favorite_status_failure));
			}
		}
	}

	/**
	 * Unfavorites the given status on Twitter.
	 * 
	 * @param c
	 *            a cursor pointing to the status to unfavorite. The cursor will
	 *            not be modified.
	 * @param notify
	 *            if true a toast will be created to notify the user about the
	 *            outcome
	 */
	private void unfavoriteStatus(Cursor c, boolean notify) {
		long unfavoriteStatusId = c.getLong(c.getColumnIndex(Tweets.COL_TID));
		boolean success = false;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				mTwitter.destroyFavorite(unfavoriteStatusId);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			success = true;
			break;
		}
		// update DB
		if (success) {
			int flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
			int buffer = c.getInt(c.getColumnIndex(Tweets.COL_BUFFER));
			long rowId = c.getLong(c.getColumnIndex(Tweets.COL_ROW_ID));
			ContentValues cv = new ContentValues();
			cv.put(Tweets.COL_FLAGS, flags & ~Tweets.FLAG_TO_UNFAVORITE);
			cv.put(Tweets.COL_BUFFER, buffer & ~Tweets.BUFFER_FAVORITES);
			Uri updateUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);
			getContentResolver().update(updateUri, cv, null, null);
			getContentResolver().notifyChange(Tweets.TABLE_FAVORITES_URI, null);
			if (notify) {
				makeToast(getString(R.string.unfavorite_status_success));
			}
		} else {
			if (notify) {
				makeToast(getString(R.string.unfavorite_status_failure));
			}
		}
	}

	/**
	 * Retweets the given status.
	 * 
	 * @param c
	 *            a cursor pointing to the status to retweet. The cursor will
	 *            not be modified.
	 * @param notify
	 *            if true a toast will be created to notify the user about the
	 *            outcome
	 */
	private void retweetStatus(Cursor c, boolean notify) {
		long retweetStatusId = c.getLong(c.getColumnIndex(Tweets.COL_TID));
		boolean success = false;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				mTwitter.retweetStatus(retweetStatusId);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			success = true;
			break;
		}
		if (success) {
			long rowId = c.getLong(c.getColumnIndex(Tweets.COL_ROW_ID));
			int flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
			int buffer = c.getInt(c.getColumnIndex(Tweets.COL_BUFFER));
			ContentValues cv = new ContentValues();
			cv.put(Tweets.COL_FLAGS, flags & ~Tweets.FLAG_TO_RETWEET);
			cv.put(Tweets.COL_BUFFER, buffer);
			cv.put(Tweets.COL_RETWEETED, 1);
			Uri updateUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);
			getContentResolver().update(updateUri, cv, null, null);
			if (notify) {
				makeToast(getString(R.string.retweet_success));
			}
		} else {
			if (notify) {
				makeToast(getString(R.string.retweet_failure));
			}
		}
	}

	/*
	 * ACTION METHODS
	 */

	/**
	 * Logs in to Twitter with the credentials given in the LoginActivity. On
	 * success the logged in user is stored in the preferences and added to the
	 * databae. A broadcast is sent, notifying any receivers whether the action
	 * failed or succeeded.
	 */
	private void login() {
		Log.d(TAG, "login()");
		User user = null;
		boolean success = false;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				user = mTwitter.verifyCredentials();
			} catch (TwitterException e) {
				if (e.getStatusCode() == TwitterException.UNAUTHORIZED) {
					// credentials are wrong. no need to try again.
					break;
				}
			}
			// login worked
			if (user != null) {
				success = true;
				break;
			}
		}

		if (success) {
			// save the user
			ContentValues cv = getUserContentValues(user);
			storeUser(cv);
			// store user Id and screenname in shared prefs
			LoginActivity.setTwitterId(Long.toString(user.getId()), getBaseContext());
			LoginActivity.setTwitterScreenname(user.getScreenName(), getBaseContext());
			// broadcast the result
			Intent intent = new Intent(LoginActivity.LOGIN_RESULT_ACTION);
			intent.putExtra(LoginActivity.LOGIN_RESULT, LoginActivity.LOGIN_SUCCESS);
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		} else {
			// failure
			Intent intent = new Intent(LoginActivity.LOGIN_RESULT_ACTION);
			intent.putExtra(LoginActivity.LOGIN_RESULT, LoginActivity.LOGIN_FAILURE);
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
	}

	/*
	 * SYNC TIMELINE
	 */

	private void syncTimeline() {
		Log.d(TAG, "TimelineSyncService executeSync() called on Thread " + Thread.currentThread().getId());
		if (isTimelineSyncNeeded()) {
			Log.d(TAG, "TimelineSyncService executeSync() needed");
			List<Status> timeline = loadTimeline();
			insertTimeline(timeline);
		}
		Log.d(TAG, "TimelineSyncService executeSync() exit");
	}

	private boolean isTimelineSyncNeeded() {
		boolean needed = false;
		if (mStartIntent.getBooleanExtra(EXTRA_FORCE_SYNC, false)) {
			needed = true;
		} else if ((System.currentTimeMillis() - getLastUpdate(PREF_LAST_TIMELINE_UPDATE) > Constants.TIMELINE_MIN_SYNCH)) {
			needed = true;
		}
		return needed;
	}

	/**
	 * Loads the timeline from Twitter according to the parameters in the
	 * intent.
	 * 
	 * @return a list of statuses or null if loading failed
	 */
	private List<Status> loadTimeline() {
		int updateDirection = mStartIntent.getIntExtra(EXTRA_TIMELINE_UPDATE_DIRECTION, TIMELINE_UPDATE_DIRECTION_UP);
		List<twitter4j.Status> timeline = null;
		Paging paging = new Paging();
		paging.setCount(Constants.NR_TWEETS);
		if (updateDirection == TIMELINE_UPDATE_DIRECTION_UP) {
			Log.d(TAG, "loadTimeline() direction: UP");
			paging.setSinceId(getSinceId(getBaseContext(), PREF_TIMELINE_SINCE_ID));
		} else {
			Log.d(TAG, "loadTimeline() update direction: DOWN");
			paging.setMaxId(getTimelineUntilId());
		}
		boolean success = false;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				timeline = mTwitter.getHomeTimeline(paging);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			if (timeline != null) {
				success = true;
				break;
			}
		}
		if (!success) {
			if (TweetListActivity.running) {
				makeToast(getString(R.string.timeline_loading_failure));
			}
		}
		return timeline;
	}

	/**
	 * Inserts all the tweets and users contained in the given timeline into the
	 * DB.
	 * 
	 * @param timeline
	 *            a list of statuses
	 */
	private void insertTimeline(List<Status> timeline) {
		Log.d(TAG, "insertTimeline()");
		if (timeline != null && !timeline.isEmpty()) {
			int updateDirection = mStartIntent.getIntExtra(EXTRA_TIMELINE_UPDATE_DIRECTION,
					TIMELINE_UPDATE_DIRECTION_UP);
			if (updateDirection == TIMELINE_UPDATE_DIRECTION_DOWN) {
				Constants.TIMELINE_BUFFER_SIZE += timeline.size();
				Log.d(TAG, "update direction DOWN; inserting " + timeline.size() + " new tweets; BUFFER SIZE now: "
						+ Constants.TIMELINE_BUFFER_SIZE);
			}
			Long lastId = null;
			List<ContentValues> tweetsValues = new ArrayList<ContentValues>();
			List<ContentValues> usersValues = new ArrayList<ContentValues>();
			for (Status tweet : timeline) {
				lastId = tweet.getId();
				tweetsValues.add(getTweetContentValues(tweet, Tweets.BUFFER_TIMELINE));
				ContentValues userValues;
				if (!tweet.isRetweet()) {
					userValues = getUserContentValues(tweet.getUser());
					userValues.put(TwitterUsers.COL_ISFRIEND, 1);
				} else {
					userValues = getUserContentValues(tweet.getRetweetedStatus().getUser());
				}
				usersValues.add(userValues);
			}
			storeTweets(tweetsValues.toArray(new ContentValues[tweetsValues.size()]));
			storeUsers(usersValues.toArray(new ContentValues[usersValues.size()]));
			syncTransactionalUsers();

			if (updateDirection == TIMELINE_UPDATE_DIRECTION_UP) {
				setSinceId(this, PREF_TIMELINE_SINCE_ID, lastId);
				setLastUpdate(this, PREF_LAST_TIMELINE_UPDATE, System.currentTimeMillis());
			}
		}
	}

	/**
	 * Reads the ID of the last tweet from shared preferences.
	 * 
	 * @return
	 */
	private long getTimelineUntilId() {
		long timelineUntilId = 1;
		Cursor c = getContentResolver().query(
				Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
						+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_NORMAL), null, null, null, null);
		if (c.getCount() > 0) {
			c.moveToFirst();
			if (!c.isNull(c.getColumnIndex(Tweets.COL_TID))) {
				timelineUntilId = c.getLong(c.getColumnIndex(Tweets.COL_TID));
			}
		}
		c.close();
		return timelineUntilId;
	}

	/*
	 * SYNC TRANSACTIONAL
	 */

	private void syncAllTransactional() {
		syncTransactionalMessages();
		syncTransactionalTweets();
		syncTransactionalUsers();
	}

	/*
	 * SYNC MENTIONS
	 */

	private void syncMentions() {
		Log.d(TAG, "MentionsSyncService executeSync() called on Thread " + Thread.currentThread().getId());
		if (isMentionsSyncNeeded()) {
			Log.d(TAG, "MentionsSyncService executeSync() needed");
			List<Status> mentions = loadMentions();
			if (mentions != null) {
				insertMentions(mentions);
			}
		}
		Log.d(TAG, "MentionsSyncService executeSync() exit");
	}

	public static boolean firstMentionsSyncCompleted(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(PREF_LAST_MENTIONS_UPDATE, -1) > 0;
	}

	private boolean isMentionsSyncNeeded() {
		boolean needed = false;
		if (mStartIntent.getBooleanExtra(EXTRA_FORCE_SYNC, false)) {
			needed = true;
		} else if ((System.currentTimeMillis() - getLastUpdate(PREF_LAST_MENTIONS_UPDATE) > Constants.MENTIONS_MIN_SYNCH)) {
			needed = true;
		}
		return needed;
	}

	/**
	 * Loads the mentions from Twitter.
	 * 
	 * @return a list of mentions or null if loading failed
	 */
	private List<Status> loadMentions() {
		List<twitter4j.Status> mentions = null;
		Paging paging = new Paging(getSinceId(getBaseContext(), PREF_MENTIONS_SINCE_ID));
		paging.setCount(Constants.NR_MENTIONS);
		boolean success = false;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				mentions = mTwitter.getMentionsTimeline(paging);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			if (mentions != null) {
				success = true;
				break;
			}
		}
		if (!success) {
			if (TweetListActivity.running) {
				makeToast(getString(R.string.mentions_loading_failure));
			}
		}
		return mentions;
	}

	/**
	 * Inserts all the tweets and users contained in the given list of mentions
	 * into the DB.
	 * 
	 * @param mentions
	 *            a list of statuses
	 */
	private void insertMentions(List<Status> mentions) {
		if (mentions != null && !mentions.isEmpty()) {
			Long lastId = null;
			List<ContentValues> tweetsValues = new ArrayList<ContentValues>();
			List<ContentValues> usersValues = new ArrayList<ContentValues>();
			for (Status tweet : mentions) {
				if (lastId == null) {
					lastId = tweet.getId();
				}
				tweetsValues.add(getTweetContentValues(tweet, Tweets.BUFFER_MENTIONS));
				usersValues.add(getUserContentValues(tweet.getUser()));
			}

			storeTweets(tweetsValues.toArray(new ContentValues[tweetsValues.size()]));
			storeUsers(usersValues.toArray(new ContentValues[usersValues.size()]));
			syncTransactionalUsers();
			getContentResolver().notifyChange(Tweets.TABLE_MENTIONS_URI, null);
			setSinceId(this, PREF_MENTIONS_SINCE_ID, lastId);
			setLastUpdate(this, PREF_LAST_MENTIONS_UPDATE, System.currentTimeMillis());
		}
	}

	/*
	 * SYNC FAVORITES
	 */

	private void syncFavorites() {
		Log.d(TAG, "FavoritesSyncService executeSync() called on Thread " + Thread.currentThread().getId());
		if (isFavoritesSyncNeeded()) {
			Log.d(TAG, "FavoritesSyncService executeSync() needed");
			List<Status> favorites = loadFavorites();
			if (favorites != null) {
				insertFavorites(favorites);
			}
		}
		Log.d(TAG, "FavoritesSyncService executeSync() exit");
	}

	private boolean isFavoritesSyncNeeded() {
		boolean needed = false;
		if (mStartIntent.getBooleanExtra(EXTRA_FORCE_SYNC, false)) {
			needed = true;
		} else if ((System.currentTimeMillis() - getLastUpdate(PREF_LAST_FAVORITES_UPDATE) > Constants.FAVORITES_MIN_SYNCH)) {
			needed = true;
		}
		return needed;
	}

	/**
	 * Loads the favorites from Twitter.
	 * 
	 * @return a list of favorites or null if loading failed
	 */
	private List<Status> loadFavorites() {
		List<twitter4j.Status> favorites = null;
		Paging paging = new Paging(getSinceId(getBaseContext(), PREF_FAVORITES_SINCE_ID));
		paging.setCount(Constants.NR_FAVORITES);
		boolean success = false;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			try {
				favorites = mTwitter.getFavorites(paging);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			if (favorites != null) {
				success = true;
				break;
			}
		}
		if (!success) {
			if (TweetListActivity.running) {
				makeToast(getString(R.string.favorites_loading_failure));
			}
		}
		return favorites;
	}

	/**
	 * Inserts all the tweets and users contained in the given list of favorites
	 * into the DB.
	 * 
	 * @param favorites
	 *            a list of statuses
	 */
	private void insertFavorites(List<Status> favorites) {
		if (favorites != null && !favorites.isEmpty()) {
			Long lastId = null;
			List<ContentValues> tweetsValues = new ArrayList<ContentValues>();
			List<ContentValues> usersValues = new ArrayList<ContentValues>();
			for (Status tweet : favorites) {
				if (lastId == null) {
					lastId = tweet.getId();
				}
				tweetsValues.add(getTweetContentValues(tweet, Tweets.BUFFER_FAVORITES));
				usersValues.add(getUserContentValues(tweet.getUser()));
			}

			storeTweets(tweetsValues.toArray(new ContentValues[tweetsValues.size()]));
			storeUsers(usersValues.toArray(new ContentValues[usersValues.size()]));
			syncTransactionalUsers();
			getContentResolver().notifyChange(Tweets.TABLE_FAVORITES_URI, null);
			setSinceId(this, PREF_FAVORITES_SINCE_ID, lastId);
			setLastUpdate(this, PREF_LAST_FAVORITES_UPDATE, System.currentTimeMillis());
		}
	}

	private void syncFriends() {
		Log.d(TAG, "FriendsSyncService executeSync() called on Thread " + Thread.currentThread().getId());
		if (isFriendSyncNeeded()) {
			Log.d(TAG, "FriendsSyncService executeSync() needed");
			List<User> friends = loadFriends();
			insertFriends(friends);
		}
		Log.d(TAG, "FriendsSyncService executeSync() exit");
	}

	private boolean isFriendSyncNeeded() {
		boolean needed = false;
		if ((System.currentTimeMillis() - getLastUpdate(PREF_LAST_FRIENDS_UPDATE) > Constants.FRIENDS_MIN_SYNCH)) {
			needed = true;
		}
		return needed;
	}

	/**
	 * Loads at most 5 pages with 20 friends each from twitter.
	 * 
	 * @return a list of friends
	 */
	private List<User> loadFriends() {
		List<User> friends = new LinkedList<User>();
		long cursor = -1;
		int pagesLoaded = 0;
		String ownScreenname = LoginActivity.getTwitterScreenname(this);
		do {
			PagableResponseList<User> pagedFriendsList;
			try {
				pagedFriendsList = mTwitter.getFriendsList(ownScreenname, cursor);
			} catch (TwitterException e) {
				e.printStackTrace();
				break;
			}
			for (User friend : pagedFriendsList) {
				friends.add(friend);
			}
			pagesLoaded++;
			cursor = pagedFriendsList.getNextCursor();
		} while (pagesLoaded < FRIENDS_PAGES_TO_LOAD);
		return friends;
	}

	/**
	 * Inserts the given users into the DB as friends.
	 * 
	 * @param friends
	 *            a list of users that the logged in user follows
	 */
	private void insertFriends(List<User> friends) {
		if (friends != null && !friends.isEmpty()) {
			List<ContentValues> friendsValues = new LinkedList<ContentValues>();
			for (User friend : friends) {
				ContentValues cv = getUserContentValues(friend);
				cv.put(TwitterUsers.COL_LASTUPDATE, System.currentTimeMillis());
				cv.put(TwitterUsers.COL_ISFRIEND, 1);
				friendsValues.add(cv);
			}
			storeUsers(friendsValues.toArray(new ContentValues[friendsValues.size()]));
			setLastUpdate(this, PREF_LAST_FRIENDS_UPDATE, System.currentTimeMillis());
			getContentResolver().notifyChange(TwitterUsers.USERS_FRIENDS_URI, null);
			syncTransactionalUsers();
		}
	}

	/*
	 * SYNC FOLLOWERS
	 */
	private void syncFollowers() {
		Log.d(TAG, "FollowersSyncService executeSync() called on Thread " + Thread.currentThread().getId());
		if (isFollowersSyncNeeded()) {
			Log.d(TAG, "FollowersSyncService executeSync() needed");
			List<User> followers = loadFollowers();
			insertFollowers(followers);
		}
		Log.d(TAG, "FollowersSyncService executeSync() exit");
	}

	private boolean isFollowersSyncNeeded() {
		boolean needed = false;
		if ((System.currentTimeMillis() - getLastUpdate(PREF_LAST_FOLLOWERS_UPDATE) > Constants.FOLLOWERS_MIN_SYNCH)) {
			needed = true;
		}
		return needed;
	}

	/**
	 * Loads at most 5 pages with 20 followers each from twitter.
	 * 
	 * @return a list of followers
	 */
	private List<User> loadFollowers() {
		List<User> followers = new LinkedList<User>();
		long cursor = -1;
		int pagesLoaded = 0;
		String ownScreenname = LoginActivity.getTwitterScreenname(this);
		do {
			PagableResponseList<User> pagedFollowersList;
			try {
				pagedFollowersList = mTwitter.getFollowersList(ownScreenname, cursor);
			} catch (TwitterException e) {
				e.printStackTrace();
				break;
			}
			Log.d(TAG, "loaded followers page " + pagesLoaded);
			Log.d(TAG, "loaded " + pagedFollowersList.size() + " followers");
			for (User follower : pagedFollowersList) {
				followers.add(follower);
			}
			pagesLoaded++;
			cursor = pagedFollowersList.getNextCursor();
		} while (pagesLoaded < FOLLOWERS_PAGES_TO_LOAD);
		return followers;
	}

	/**
	 * Inserts the given users into the DB as followers.
	 * 
	 * @param followers
	 *            a list of users that follow the logged in user
	 */
	private void insertFollowers(List<User> followers) {
		if (followers != null && !followers.isEmpty()) {
			List<ContentValues> followersValues = new LinkedList<ContentValues>();
			for (User follower : followers) {
				ContentValues cv = getUserContentValues(follower);
				cv.put(TwitterUsers.COL_LASTUPDATE, System.currentTimeMillis());
				cv.put(TwitterUsers.COL_ISFOLLOWER, 1);
				followersValues.add(cv);
			}
			storeUsers(followersValues.toArray(new ContentValues[followersValues.size()]));
			setLastUpdate(this, PREF_LAST_FOLLOWERS_UPDATE, System.currentTimeMillis());
			getContentResolver().notifyChange(TwitterUsers.USERS_FOLLOWERS_URI, null);
			syncTransactionalUsers();
		}
	}

	/*
	 * SEARCH TWEET
	 */

	private void searchTweet() {
		Log.d(TAG, "SearchTweetService executeSync() called on Thread " + Thread.currentThread().getId());
		String queryString = mStartIntent.getStringExtra(EXTRA_TWEET_SEARCH_QUERY);
		if (queryString != null) {
			List<Status> searchResults = loadSearchTweets(queryString);
			insertSearchTweets(searchResults);
		}
		Log.d(TAG, "SearchTweetService executeSync() exit");
	}

	private List<Status> loadSearchTweets(String queryString) {
		List<Status> searchResults = null;
		Query query = new Query(queryString);
		query.setCount(Constants.NR_SEARCH_TWEETS);
		try {
			searchResults = mTwitter.search(query).getTweets();
		} catch (TwitterException e) {
			e.printStackTrace();
			// TODO: notify
		}
		return searchResults;
	}

	private void insertSearchTweets(List<Status> searchResults) {
		if (searchResults != null && !searchResults.isEmpty()) {
			List<ContentValues> tweetsValues = new ArrayList<ContentValues>();
			List<ContentValues> usersValues = new ArrayList<ContentValues>();
			for (Status tweet : searchResults) {
				if (tweet.isRetweet()) {
					tweet = tweet.getRetweetedStatus();
				}
				if (tweet.getUser() != null && tweet.getUser().getScreenName() != null) {
					tweetsValues.add(getTweetContentValues(tweet, Tweets.BUFFER_SEARCH));
					usersValues.add(getUserContentValues(tweet.getUser()));
				}
			}
			storeTweets(tweetsValues.toArray(new ContentValues[tweetsValues.size()]));
			storeUsers(usersValues.toArray(new ContentValues[usersValues.size()]));
			getContentResolver().notifyChange(Tweets.TABLE_SEARCH_URI, null);
			syncTransactionalUsers();
		}
	}

	/*
	 * SEARCH USER
	 */
	private void searchUser() {
		Log.d(TAG, "SearchUserService executeSync() called on Thread " + Thread.currentThread().getId());
		String queryString = mStartIntent.getStringExtra(EXTRA_USER_SEARCH_QUERY);
		if (queryString != null) {
			List<User> searchResults = loadSearchUsers(queryString);
			insertSearchUsers(searchResults);
		}
		Log.d(TAG, "SearchUserService executeSync() exit");
	}

	private List<User> loadSearchUsers(String queryString) {
		List<User> searchResults = new LinkedList<User>();
		ResponseList<User> resultPage;
		int page = 0;
		try {
			do {
				resultPage = mTwitter.searchUsers(queryString, page);
				searchResults.addAll(resultPage);
				page++;
			} while (resultPage.isEmpty() && page < MAX_USER_SEARCH_PAGES);
		} catch (TwitterException e) {
			e.printStackTrace();
		}
		return searchResults;
	}

	private void insertSearchUsers(List<User> searchResults) {
		if (searchResults != null && !searchResults.isEmpty()) {
			List<ContentValues> usersValues = new LinkedList<ContentValues>();
			for (User user : searchResults) {
				ContentValues cv = getUserContentValues(user);
				cv.put(TwitterUsers.COL_LASTUPDATE, System.currentTimeMillis());
				cv.put(TwitterUsers.COL_IS_SEARCH_RESULT, 1);
				usersValues.add(cv);
			}
			storeUsers(usersValues.toArray(new ContentValues[usersValues.size()]));
			getContentResolver().notifyChange(TwitterUsers.USERS_SEARCH_URI, null);
			getContentResolver().notifyChange(TwitterUsers.CONTENT_URI, null);
			syncTransactionalUsers();
		}
	}

	/**
	 * SYNC USER
	 */
	private void syncUser() {
		long rowId = mStartIntent.getLongExtra(EXTRA_USER_ROW_ID, -1);
		Uri queryUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS
				+ "/" + rowId);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);
		if (c != null && c.getCount() > 0) {
			c.moveToFirst();
			Callable<ContentValues> userSyncTask = getUserSyncTask(c, true);
			try {
				ContentValues cv = userSyncTask.call();
				if (cv != null) {
					Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
							+ TwitterUsers.TWITTERUSERS);
					getContentResolver().bulkInsert(insertUri, new ContentValues[] { cv });
					getContentResolver().notifyChange(TwitterUsers.CONTENT_URI, null);
					if (userSyncTask instanceof ProfileImageUpdateTask) {
						notifyProfileImageUpdate();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		c.close();
	}

	/**
	 * SYNC TWEET
	 */

	private void syncTweet() {
		long rowId = mStartIntent.getLongExtra(EXTRA_TWEET_ROW_ID, -1);
		Uri queryUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);
		if (c != null && c.getCount() > 0) {
			c.moveToFirst();
			syncTweet(c, true);
		}
		c.close();
	}

	/*
	 * SYNC MESSAGES
	 */

	private void syncMessages() {
		Log.d(TAG, "MessagesSyncService executeSync() called on Thread " + Thread.currentThread().getId());
		if (isIncomingDmSyncNeeded()) {
			Log.d(TAG, "MessagesSyncService executeSync() incoming needed");
			List<DirectMessage> incomingDms = loadIncomingDms();
			insertIncomingDms(incomingDms);
		}
		if (isOutgoingDmSyncNeeded()) {
			Log.d(TAG, "MessagesSyncService executeSync() outgoing needed");
			List<DirectMessage> outgoingDms = loadOutgoingDms();
			insertOutgoingDms(outgoingDms);
		}
		syncTransactionalUsers();
		Log.d(TAG, "MessagesSyncService executeSync() exit");
	}

	public static boolean firstIncomingDmSyncCompleted(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(PREF_LAST_INCOMING_DMS_UPDATE, -1) > 0;
	}

	private boolean isIncomingDmSyncNeeded() {
		boolean needed = false;
		if ((System.currentTimeMillis() - getLastUpdate(PREF_LAST_INCOMING_DMS_UPDATE) > DMS_MIN_SYNCH_INTERVAL)) {
			needed = true;
		}
		return needed;
	}

	private boolean isOutgoingDmSyncNeeded() {
		boolean needed = false;
		if ((System.currentTimeMillis() - getLastUpdate(PREF_LAST_OUTGOING_DMS_UPDATE) > DMS_MIN_SYNCH_INTERVAL)) {
			needed = true;
		}
		return needed;
	}

	private List<DirectMessage> loadIncomingDms() {
		boolean success = false;
		List<DirectMessage> incomingDms = null;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			Paging paging = new Paging();
			paging.setSinceId(getSinceId(getBaseContext(), PREF_INCOMING_DMS_SINCE_ID));
			paging.setCount(Constants.NR_DMS);
			try {
				incomingDms = mTwitter.getDirectMessages(paging);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			if (incomingDms != null) {
				success = true;
				break;
			}
		}
		if (!success) {
			if (TweetListActivity.running) {
				makeToast(getString(R.string.dms_loading_failure));
			}
		}
		return incomingDms;
	}

	private List<DirectMessage> loadOutgoingDms() {
		boolean success = false;
		List<DirectMessage> outgoingDms = null;
		for (int attempt = 0; attempt < MAX_LOAD_ATTEMPTS; attempt++) {
			Paging paging = new Paging();
			paging.setSinceId(getSinceId(getBaseContext(), PREF_OUTGOING_DMS_SINCE_ID));
			paging.setCount(Constants.NR_DMS);
			try {
				outgoingDms = mTwitter.getSentDirectMessages(paging);
			} catch (TwitterException e) {
				e.printStackTrace();
				continue;
			}
			if (outgoingDms != null) {
				success = true;
				break;
			}
		}
		if (!success) {
			if (TweetListActivity.running) {
				makeToast(getString(R.string.dms_loading_failure));
			}
		}
		return outgoingDms;
	}

	private void insertIncomingDms(List<DirectMessage> messages) {
		if (messages != null && !messages.isEmpty()) {
			Long lastId = null;
			for (DirectMessage message : messages) {
				if (lastId == null) {
					lastId = message.getId();
				}
				ContentValues userValues = getUserContentValues(message.getSender());
				storeUser(userValues);
				ContentValues messageValues = getMessageContentValues(message);
				storeMessage(messageValues);
			}
			setSinceId(this, PREF_LAST_INCOMING_DMS_UPDATE, lastId);
			setLastUpdate(this, PREF_LAST_INCOMING_DMS_UPDATE, System.currentTimeMillis());
		}
	}

	private void insertOutgoingDms(List<DirectMessage> messages) {
		if (messages != null && !messages.isEmpty()) {
			Long lastId = null;
			for (DirectMessage message : messages) {
				if (lastId == null) {
					lastId = message.getId();
				}
				ContentValues userValues = getUserContentValues(message.getSender());
				storeUser(userValues);
				ContentValues messageValues = getMessageContentValues(message);
				storeMessage(messageValues);
			}
			setSinceId(this, PREF_LAST_OUTGOING_DMS_UPDATE, lastId);
			setLastUpdate(this, PREF_LAST_INCOMING_DMS_UPDATE, System.currentTimeMillis());
		}
	}

	private long storeMessage(ContentValues messageValues) {
		long rowId = 0;
		if (messageValues != null) {
			Uri insertUri = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
					+ DirectMessages.DMS_LIST + "/" + DirectMessages.DMS_SOURCE_NORMAL);
			Uri resultUri = getContentResolver().insert(insertUri, messageValues);
			rowId = Long.valueOf(resultUri.getLastPathSegment());
		}
		return rowId;
	}

	/**
	 * Creates content values for a direct message from Twitter.
	 * 
	 * @param message
	 *            the message
	 * @return the content values
	 */
	private ContentValues getMessageContentValues(DirectMessage message) {
		ContentValues cv = new ContentValues();
		cv.put(DirectMessages.COL_TEXT, message.getText());
		cv.put(DirectMessages.COL_CREATED, message.getCreatedAt().getTime());
		cv.put(DirectMessages.COL_DMID, message.getId());
		cv.put(DirectMessages.COL_SENDER, message.getSender().getId());
		cv.put(DirectMessages.COL_RECEIVER, message.getRecipient().getId());
		cv.put(DirectMessages.COL_RECEIVER_SCREENNAME, message.getRecipient().getScreenName());
		cv.put(Tweets.COL_BUFFER, DirectMessages.BUFFER_MESSAGES);

		return cv;
	}

	/*
	 * SYNC USER TWEETS
	 */

	private void syncUserTweets() {
		String screenName = mStartIntent.getStringExtra(EXTRA_SCREEN_NAME);
		if (screenName != null) {
			List<Status> userTweets = loadUserTweets(screenName);
			insertUserTweets(userTweets);
		}
	}

	private List<Status> loadUserTweets(String screenName) {
		List<Status> userTweets = null;
		try {
			userTweets = mTwitter.getUserTimeline(screenName);
		} catch (TwitterException e) {
			e.printStackTrace();
			// TODO: notify
		}
		return userTweets;
	}

	private void insertUserTweets(List<Status> userTweets) {
		if (userTweets != null && !userTweets.isEmpty()) {
			List<ContentValues> tweetsValues = new LinkedList<ContentValues>();
			for (Status tweet : userTweets) {
				ContentValues cv = getTweetContentValues(tweet, Tweets.BUFFER_USERS);
				tweetsValues.add(cv);
			}
			storeTweets(tweetsValues.toArray(new ContentValues[tweetsValues.size()]));
			getContentResolver().notifyChange(Tweets.TABLE_USER_URI, null);
		}
	}

	// TODO: make own service for this
	private class CacheUrlTask extends AsyncTask<Void, Void, Void> {
		twitter4j.Status mTweet;

		public CacheUrlTask(twitter4j.Status tweet) {
			mTweet = tweet;
		}

		@Override
		protected Void doInBackground(Void... params) {
			HtmlPagesDbHelper htmlDbHelper = new HtmlPagesDbHelper(getApplicationContext());
			htmlDbHelper.open();
			htmlDbHelper.insertLinksIntoDb(mTweet.getURLEntities(), mTweet.getId(), HtmlPagesDbHelper.DOWNLOAD_NORMAL);
			return null;
		}

	}
}
