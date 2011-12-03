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

import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import winterwell.jtwitter.OAuthSignpostClient;
import winterwell.jtwitter.Twitter;
import winterwell.jtwitter.TwitterAccount;
import winterwell.jtwitter.TwitterException;
import winterwell.jtwitter.Twitter.User;

import ch.ethz.bluetest.credentials.Obfuscator;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.ShowTweetListActivity;
import ch.ethz.twimight.activities.ShowUserListActivity;
import ch.ethz.twimight.util.Constants;

import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

/**
 * The service to send all kinds of API calls to Twitter. 
 * This is the only place where calling Twitter is allowed!
 * @author thossman
 *
 */
public class TwitterService extends Service {
	
	private static final String TAG = "TwitterService";
	
	public static final String SYNCH_ACTION = "twimight_synch";
	
	public static final int SYNCH_LOGIN = 0;
	public static final int SYNCH_ALL = 1;
	public static final int SYNCH_TIMELINE = 2;
	public static final int SYNCH_FAVORITES = 3;
	public static final int SYNCH_MENTIONS = 4;
	public static final int SYNCH_SEARCH = 5;
	public static final int SYNCH_MESSAGES = 6;
	public static final int SYNCH_FRIENDS = 7;
	public static final int SYNCH_FOLLOWERS = 8;
	public static final int SYNCH_USER = 9;
	public static final int SYNCH_TWEET = 10;
	public static final int SYNCH_MESSAGE = 11;
	
	Twitter twitter;

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	/**
	 * Executed when the service is started. We return START_STICKY to not be stopped immediately.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		Log.i(TAG, "started..");
		
		
		// Do we have connectivity?
		ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		if(cm.getActiveNetworkInfo()==null || !cm.getActiveNetworkInfo().isConnected()){
			Log.i(TAG, "Error synching: no connectivity");
			return START_STICKY;
		} else {
			Log.i(TAG, "We are connected!");
		}
		
		// Create twitter object
		String token = LoginActivity.getAccessToken(this);
		String secret = LoginActivity.getAccessTokenSecret(this);
		if(!(token == null || secret == null) ) {
			// get ready for OAuth
			OAuthSignpostClient client = new OAuthSignpostClient(Obfuscator.getKey(), Obfuscator.getSecret(), token, secret);
			twitter = new Twitter(null, client);
		} else {
			Log.i(TAG, "Error synching: no access token or secret");
			return START_STICKY;
		}
		
		
		// check what we have to synch
		int synchRequest = intent.getIntExtra("synch_request", SYNCH_ALL);
		switch(synchRequest){
		case SYNCH_LOGIN:
			Log.i(TAG, "SYNCH_LOGIN");
			synchLogin();
			break;
		case SYNCH_ALL:
			Log.i(TAG, "SYNCH_ALL");
			synchTransactionalTweets();
			synchTimeline();
			synchMentions();
			synchFavorites();
			break;
		case SYNCH_TIMELINE:
			Log.i(TAG, "SYNCH_TIMELINE");
			synchTimeline();
			break;
		case SYNCH_MENTIONS:
			Log.i(TAG, "SYNCH_MENTIONS");
			synchMentions();
			break;
		case SYNCH_FAVORITES:
			Log.i(TAG, "SYNCH_FAVORITES");
			synchFavorites();
			break;
		case SYNCH_FRIENDS:
			Log.i(TAG, "SYNCH_FRIENDS");
			synchFriends();
			break;
		case SYNCH_FOLLOWERS:
			Log.i(TAG, "SYNCH_FOLLOWERS");
			synchFollowers();
			break;
		case SYNCH_SEARCH:
			Log.i(TAG, "SYNCH_SEARCH");
			// TODO
			break;
		case SYNCH_MESSAGES:
			Log.i(TAG, "SYNCH_MESSAGES");
			// TODO
			break;
		case SYNCH_USER:
			Log.i(TAG, "SYNCH_USER");
			if(intent.getLongExtra("rowId", 0) != 0){
				synchUser(intent.getLongExtra("rowId", 0));
			}
			break;
		case SYNCH_TWEET:
			Log.i(TAG, "SYNCH_TWEET");
			if(intent.getLongExtra("rowId", 0) != 0){
				synchTweet(intent.getLongExtra("rowId", 0));
			}
			break;
		case SYNCH_MESSAGE:
			Log.i(TAG, "SYNCH_MESSAGE");
			// TODO
			break;
		default:
			throw new IllegalArgumentException("Exception: Unknown synch request");
		}
		
		return START_STICKY;
	}

	/**
	 * Creates the thread to update friends
	 */
	private void synchFriends() {
		if(System.currentTimeMillis() - getLastFriendsUpdate(getBaseContext()) > Constants.FRIENDS_MIN_SYNCH){
			(new UpdateFriendsTask()).execute();
		} else {
			Log.i(TAG, "Last friends synch too recent.");
		}

		
	}

	/**
	 * Creates the thread to update followers
	 */
	private void synchFollowers() {
		if(System.currentTimeMillis() - getLastFollowerUpdate(getBaseContext()) > Constants.FOLLOWERS_MIN_SYNCH){
			(new UpdateFollowersTask()).execute();
		} else {
			Log.i(TAG, "Last followers synch too recent.");
		}

		
	}

	/**
	 * Verifies the user credentials by a verifyCredentials call.
	 * Stores the user ID in the shared preferences.
	 */
	private void synchLogin(){
		(new VerifyCredentialsTask()).execute(Constants.LOGIN_ATTEMPTS);
	}

	/**
	 * Syncs all tweets which have transactional flags set
	 */
	private void synchTransactionalTweets(){
		// get the flagged tweets
		Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS);
		Cursor c = getContentResolver().query(queryUri, null, Tweets.TWEETS_COLUMNS_FLAGS+"!=0", null, null);
		Log.i(TAG, c.getCount()+" transactional tweets to synch");
		if(c.getCount() >= 0){
			c.moveToFirst();
			while(!c.isAfterLast()){
				synchTweet(c.getLong(c.getColumnIndex("_id")));
				c.moveToNext();
			}
		}
	}
	
	/**
	 * Checks the transactional flags of the tweet with the given _id and performs the corresponding actions
	 */
	private void synchTweet(long rowId) {
		// get the flags
		Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+rowId);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);
		
		if(c.getCount() == 0){
			Log.i(TAG, "Synch Tweet: Tweet not found " + rowId);
			return;
		}
		c.moveToFirst();
		
		int flags = c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));
		if(flags == 0){
			Log.i(TAG, "nothing to do");
		} else if((flags & Tweets.FLAG_TO_DELETE)>0) {
			// Delete a tweet from twitter
			(new DestroyStatusTask()).execute(rowId);
		} else if((flags & Tweets.FLAG_TO_INSERT)>0) {
			// post the tweet to twitter
			(new UpdateStatusTask()).execute(rowId);
		} else if((flags & Tweets.FLAG_TO_FAVORITE)>0) {
			// post favorite to twitter
			(new FavoriteStatusTask()).execute(rowId);
		} else if((flags & Tweets.FLAG_TO_UNFAVORITE)>0) {
			// remove favorite from twitter
			(new UnfavoriteStatusTask()).execute(rowId);
		} else if((flags & Tweets.FLAG_TO_RETWEET)>0) {
			// retweet
			(new RetweetStatusTask()).execute(rowId);
		} 
		c.close();
	}
	
	/**
	 * Checks the transactional flags of the user with the given _id and performs the corresponding actions
	 */
	private void synchUser(long rowId) {
		// get the flags
		Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+rowId);
		Cursor c = getContentResolver().query(queryUri, null, null, null, null);
		
		if(c.getCount() == 0){
			Log.i(TAG, "Synch User: Tweet not found " + rowId);
			return;
		}
		c.moveToFirst();
		
		int flags = c.getInt(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS));
		if(flags == 0){
			Log.i(TAG, "nothing to do");
		} else if((flags & TwitterUsers.FLAG_TO_UPDATE)>0) {
			// Update a user if it's time to do so
			if(c.isNull(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_LASTUPDATE)) | (System.currentTimeMillis() - c.getInt(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_LASTUPDATE))>Constants.USERS_MIN_SYNCH)){
				(new UpdateUserTask()).execute(rowId);
			} else {
				Log.i(TAG, "Last user update too recent");
			}
		} else if((flags & TwitterUsers.FLAG_TO_FOLLOW)>0) {
			// Follow a user
			(new FollowUserTask()).execute(rowId);
		} else if((flags & TwitterUsers.FLAG_TO_UNFOLLOW)>0) {
			// Unfollow a user
			(new UnfollowUserTask()).execute(rowId);
		}
		c.close();
	}
	
	/**
	 * Starts a thread to load the timeline. But only if the last timeline request is old enough.
	 */
	private void synchTimeline() {
		
		if(System.currentTimeMillis() - getLastTimelineUpdate(getBaseContext()) > Constants.TIMELINE_MIN_SYNCH){
			(new UpdateTimelineTask()).execute();
		} else {
			Log.i(TAG, "Last timeline synch too recent.");
		}
		
	}
	
	/**
	 * Starts a thread to load the favorites. But only if the last favorites request is old enough.
	 */
	private void synchFavorites() {
		
		if(System.currentTimeMillis() - getLastFavoritesUpdate(getBaseContext()) > Constants.FAVORITES_MIN_SYNCH){
			(new UpdateFavoritesTask()).execute();
		} else {
			Log.i(TAG, "Last favorites synch too recent.");
		}
		
	}
	
	/**
	 * Starts a thread to load the mentions. But only if the last mentions request is old enough.
	 */
	private void synchMentions() {
		
		if(System.currentTimeMillis() - getLastMentionsUpdate(getBaseContext()) > Constants.MENTIONS_MIN_SYNCH){
			(new UpdateMentionsTask()).execute();
		} else {
			Log.i(TAG, "Last mentions synch too recent.");
		}
		
	}
	/**
	 * Reads the ID of the last tweet from shared preferences.
	 * @return
	 */
	public static BigInteger getTimelineSinceId(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String sinceIdString = prefs.getString("timelineSinceId", null); 
		if(sinceIdString==null)
			return null;
		else
			return new BigInteger(sinceIdString);
	}

	/**
	 * Stores the given ID as the since ID
	 */
	public static void setTimelineSinceId(BigInteger sinceId, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString("timelineSinceId",sinceId==null?null:sinceId.toString());
		prefEditor.commit();
	}

	/**
	 * Reads the timestamp of the last timeline update from shared preferences.
	 * @return
	 */
	public static long getLastTimelineUpdate(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong("timelineLastUpdate", 0);
	}
	
	/**
	 * Stores the current timestamp as the time of last timeline update
	 */
	public static void setLastTimelineUpdate(Date date, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		if(date!=null)
			prefEditor.putLong("timelineLastUpdate", date.getTime());
		else
			prefEditor.putLong("timelineLastUpdate", 0);
		prefEditor.commit();
	}
	
	/**
	 * Reads the ID of the last favorites tweet from shared preferences.
	 * @return
	 */
	public static BigInteger getFavoritesSinceId(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String sinceIdString = prefs.getString("favoritesSinceId", null); 
		if(sinceIdString==null)
			return null;
		else
			return new BigInteger(sinceIdString);
	}

	/**
	 * Stores the given ID as the since ID
	 */
	public static void setFavoritesSinceId(BigInteger sinceId, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString("favoritesSinceId", sinceId==null?null:sinceId.toString());
		prefEditor.commit();
	}

	/**
	 * Reads the timestamp of the last favorites update from shared preferences.
	 * @return
	 */
	public static long getLastFavoritesUpdate(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong("favoritesLastUpdate", 0);
	}
	
	/**
	 * Stores the current timestamp as the time of last favorites update
	 */
	public static void setLastFavoritesUpdate(Date date, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		if(date!=null)
			prefEditor.putLong("favoritesLastUpdate", date.getTime());
		else
			prefEditor.putLong("favoritesLastUpdate", 0);

		prefEditor.commit();
	}

	/**
	 * Reads the ID of the last mentions tweet from shared preferences.
	 * @return
	 */
	public static BigInteger getMentionsSinceId(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String sinceIdString = prefs.getString("mentionsSinceId", null); 
		if(sinceIdString==null)
			return null;
		else
			return new BigInteger(sinceIdString);
	}

	/**
	 * Stores the given ID as the since ID
	 */
	public static void setMentionsSinceId(BigInteger sinceId, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString("mentionsSinceId", sinceId==null?null:sinceId.toString());
		prefEditor.commit();
	}

	/**
	 * Reads the timestamp of the last mentions update from shared preferences.
	 * @return
	 */
	public static long getLastMentionsUpdate(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong("mentionsLastUpdate", 0);
	}
	
	/**
	 * Stores the current timestamp as the time of last mentions update
	 */
	public static void setLastMentionsUpdate(Date date, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		if(date!=null)
			prefEditor.putLong("mentionsLastUpdate", date.getTime());
		else
			prefEditor.putLong("mentionsLastUpdate", 0);

		prefEditor.commit();
	}
	
	/**
	 * Reads the timestamp of the last friends update from shared preferences.
	 * @return
	 */
	public static long getLastFriendsUpdate(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong("friendsLastUpdate", 0);
	}
	
	/**
	 * Stores the current timestamp as the time of last friends update
	 */
	public static void setLastFriendsUpdate(Date date, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		if(date!=null)
			prefEditor.putLong("friendsLastUpdate", date.getTime());
		else
			prefEditor.putLong("friendsLastUpdate", 0);

		prefEditor.commit();
	}
	
	/**
	 * Reads the timestamp of the last follower update from shared preferences.
	 * @return
	 */
	public static long getLastFollowerUpdate(Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong("followerLastUpdate", 0);
	}
	
	/**
	 * Stores the current timestamp as the time of last follower update
	 */
	public static void setLastFollowerUpdate(Date date, Context context) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		if(date!=null)
			prefEditor.putLong("followerLastUpdate", date.getTime());
		else
			prefEditor.putLong("followerLastUpdate", 0);

		prefEditor.commit();
	}
	
	/**
	 * Updates a tweet in the DB (or inserts it if the tweet is new to us)
	 */
	private int updateTweet(Twitter.Status tweet){
		// do we already have the tweet?
		Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS);
		String[] projection = {"_id"};
		
		Cursor c = getContentResolver().query(queryUri, projection, Tweets.TWEETS_COLUMNS_TID+"="+tweet.getId(), null, null);

		int tweetId = 0;
		
		if(c.getCount()==0){
			// insert URI
			Uri insertUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_NORMAL);
			Uri resultUri = getContentResolver().insert(insertUri, getTweetContentValues(tweet));
			tweetId = new Integer(resultUri.getLastPathSegment());
			c.close();
		} else {
			c.moveToFirst();
			if(c.isNull(c.getColumnIndex("_id"))){
				c.close();
				throw new IllegalStateException("Error while loading tweet from the DB ");
			} else {
				Uri updateUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + c.getInt(c.getColumnIndex("_id")));
				getContentResolver().update(updateUri, getTweetContentValues(tweet), null, null);
				tweetId = c.getInt(c.getColumnIndex("_id"));
				c.close();
			}
		}
		
		return tweetId;
	}
	
	/**
	 * Updates the user profile in the DB
	 * @param user
	 */
	private long updateUser(Twitter.User user) {
		
		if(user==null) return 0;
		
		// do we already have the user?
		Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
		String[] projection = {"_id"};
		
		Long userId = new Long(0);
		
		Cursor c = getContentResolver().query(uri, projection, TwitterUsers.TWITTERUSERS_COLUMNS_ID+"="+user.id, null, null);
		if(c.getCount() == 0){ // we don't have the local user in the DB yet!
			Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
			Uri resultUri = getContentResolver().insert(insertUri, getUserContentValues(user));
			userId = new Long(resultUri.getLastPathSegment());
			c.close();
			// get the profile image
			(new UpdateProfileImageTask()).execute(userId);
		} else {
			c.moveToFirst();
			if(c.isNull(c.getColumnIndex("_id"))){
				c.close();
				throw new IllegalStateException("Error while loading user from the DB ");
			} else {
				Uri updateUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + c.getInt(c.getColumnIndex("_id")));
				getContentResolver().update(updateUri, getUserContentValues(user), null, null);
				userId = c.getLong(c.getColumnIndex("_id"));
				c.close();
			}
		}
		return userId;
		
	}

	/**
	 * Creates content values for a tweet from Twitter
	 * @param tweet
	 * @return
	 */
	private ContentValues getTweetContentValues(Twitter.Status tweet) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.TWEETS_COLUMNS_TEXT, tweet.getText());
		cv.put(Tweets.TWEETS_COLUMNS_CREATED, tweet.getCreatedAt().getTime());
		cv.put(Tweets.TWEETS_COLUMNS_SOURCE, tweet.source);
		cv.put(Tweets.TWEETS_COLUMNS_TID, tweet.getId().longValue());
		cv.put(Tweets.TWEETS_COLUMNS_FAVORITED, tweet.isFavorite());
		
		// TODO: How do we know if we have retweeted the tweet?
		cv.put(Tweets.TWEETS_COLUMNS_RETWEETED, 0);
		cv.put(Tweets.TWEETS_COLUMNS_RETWEETCOUNT, tweet.retweetCount);
		if(tweet.inReplyToStatusId != null){
			cv.put(Tweets.TWEETS_COLUMNS_REPLYTO, tweet.inReplyToStatusId.longValue());
		}
		cv.put(Tweets.TWEETS_COLUMNS_USER, tweet.getUser().getId());
		cv.put(Tweets.TWEETS_COLUMNS_FLAGS, 0);
		// TODO: Location (enter coordinates of tweet)
		
		return cv;
	}
	
	/**
	 * Creates content values for a user from Twitter
	 * @param user
	 * @return
	 */
	private ContentValues getUserContentValues(User user) {
		ContentValues userContentValues = new ContentValues();
		
		if(user!=null){
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_ID, user.id);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME, user.screenName);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_NAME, user.name);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_DESCRIPTION, user.description);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_LOCATION, user.location);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_FAVORITES, user.favoritesCount);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_FRIENDS, user.friendsCount);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOWERS, user.followersCount);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_LISTED, user.listedCount);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_TIMEZONE, user.timezone);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_STATUSES, user.statusesCount);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_VERIFIED, user.verified);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_PROTECTED, user.protectedUser);
			//userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOWING, user.isFollowingYou()?1:0);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOWREQUEST, user.followRequestSent);
			userContentValues.put(TwitterUsers.TWITTERUSERS_COLUMNS_IMAGEURL, user.getProfileImageUrl().toString());
		}
		return userContentValues;
	}
	
	/**
	 * Logs in with Twitter and writes the local user into the DB.
	 * @author thossmann
	 *
	 */
	private class VerifyCredentialsTask extends AsyncTask<Integer, Void, Twitter.User> {
		int attempts;
		@Override
	     protected Twitter.User doInBackground(Integer... params) {
			attempts = params[0];
			Twitter.User user = null;
			
			try {
				TwitterAccount twitterAcc = new TwitterAccount(twitter);
				user = twitterAcc.verifyCredentials();
				
			} catch (Exception ex) {					
				Log.e(TAG, "Error while verifying credentials: " + ex);
			}
	         
	        return user;
	     }

		@Override
	     protected void onPostExecute(Twitter.User result) {
			if(result==null) {
				// if we still have more attempts, we start a new thread
				if(attempts>0){
					(new VerifyCredentialsTask()).execute(attempts-1);
				} else {
					// tell the user that the login was not successful
					Toast.makeText(getBaseContext(), "There was a problem with the login. Please try again later.", Toast.LENGTH_SHORT).show();
				}
				return;
			}

			// store user Id in shared prefs
			LoginActivity.setTwitterId(Long.toString(result.getId()), getBaseContext());
			Log.i(TAG, "Local user id: "+ Long.toString(result.getId()));
			// update user in DB
			updateUser(result);
			
			// start the timeline activity and the periodic alarms
			LoginActivity.startAlarms(getBaseContext());
			
			Intent timelineIntent = new Intent(getBaseContext(), ShowTweetListActivity.class);
			timelineIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			timelineIntent.putExtra("login", true);
			startActivity(timelineIntent);
			
			// stop the login activity
			Intent loginIntent = new Intent(getBaseContext(), LoginActivity.class);
			loginIntent.putExtra(LoginActivity.FORCE_FINISH, true);
			loginIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(loginIntent);
	     }
	}
	
	/**
	 * Updates the mentions
	 * @author thossmann
	 *
	 */
	private class UpdateMentionsTask extends AsyncTask<Void, Void, List<Twitter.Status>> {
		@Override
	     protected List<Twitter.Status> doInBackground(Void... params) {
			ShowTweetListActivity.setLoading(true);
			
			List<Twitter.Status> mentions = null;
			
			try {
				twitter.setCount(Constants.NR_MENTIONS);
				twitter.setSinceId(getMentionsSinceId(getBaseContext()));
				
				mentions = twitter.getReplies();
				Log.i(TAG, "mentions size " + mentions.size() );

			} catch (Exception ex) {					
				Log.e(TAG, "Error while refreshing mentions: " + ex);
				ShowTweetListActivity.setLoading(false);
			}
	         
	        return mentions;
	     }

		@Override
	     protected void onPostExecute(List<Twitter.Status> result) {
			if(result==null) {
				ShowTweetListActivity.setLoading(false);
				return;
			}
			
			if(!result.isEmpty()){
				BigInteger lastId = null;
				
				for (Twitter.Status tweet: result) {
					if(lastId == null)
						lastId = tweet.getId();
					
					updateUser(tweet.getUser());
					updateTweet(tweet);
					
				}
				
				// save the id of the last tweet for future timeline synchs
				setMentionsSinceId(lastId, getBaseContext());
			}
			
			// save the timestamp of the last update
			setLastMentionsUpdate(new Date(), getBaseContext());
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	/**
	 * Updates the favorites
	 * @author thossmann
	 *
	 */
	private class UpdateFavoritesTask extends AsyncTask<Void, Void, List<Twitter.Status>> {
		@Override
	     protected List<Twitter.Status> doInBackground(Void... params) {
			ShowTweetListActivity.setLoading(true);
			List<Twitter.Status> favorites = null;
			
			try {
				twitter.setCount(Constants.NR_FAVORITES);
				twitter.setSinceId(getFavoritesSinceId(getBaseContext()));
				
				favorites = twitter.getFavorites();
				Log.i(TAG, "favorites size " + favorites.size() );

			} catch (Exception ex) {	
				ShowTweetListActivity.setLoading(false);
				Log.e(TAG, "Error while refreshing favorites: " + ex);
			}
	         
	        return favorites;
	     }

		@Override
	     protected void onPostExecute(List<Twitter.Status> result) {
			if(result==null) {
				ShowTweetListActivity.setLoading(false);
				return;
			}
			
			if(!result.isEmpty()){
				BigInteger lastId = null;
				
				for (Twitter.Status tweet: result) {
					if(lastId == null)
						lastId = tweet.getId();
					
					updateUser(tweet.getUser());
					updateTweet(tweet);
					
				}
				
				// save the id of the last tweet for future timeline synchs
				setFavoritesSinceId(lastId, getBaseContext());
			}
			
			// save the timestamp of the last update
			setLastFavoritesUpdate(new Date(), getBaseContext());
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	/**
	 * Loads the timeline
	 * @author thossmann
	 *
	 */
	private class UpdateTimelineTask extends AsyncTask<Void, Void, List<Twitter.Status>> {
		@Override
	     protected List<Twitter.Status> doInBackground(Void... params) {
			ShowTweetListActivity.setLoading(true);
			
			List<Twitter.Status> timeline = null;
			
			try {
				twitter.setCount(Constants.NR_TWEETS);
				twitter.setSinceId(getTimelineSinceId(getBaseContext()));

				Log.i(TAG,"inside download, " + Constants.NR_TWEETS + " requesting " + Integer.toString(Constants.NR_TWEETS) + " Tweets");
				timeline = twitter.getHomeTimeline();
				Log.i(TAG, "timeline size " + timeline.size() );

			} catch (Exception ex) {
				ShowTweetListActivity.setLoading(false);
				Log.e(TAG, "Error while refreshing timeline: " + ex.getMessage());
			}
	         
	        return timeline;
	     }

		@Override
	     protected void onPostExecute(List<Twitter.Status> result) {
			if(result==null) {
				ShowTweetListActivity.setLoading(false);
				return;
			}
			
			if(!result.isEmpty()){
				BigInteger lastId = null;
				
				for (Twitter.Status tweet: result) {
					if(lastId == null)
						lastId = tweet.getId();
					
					updateUser(tweet.getUser());
					updateTweet(tweet);
					
				}
				
				// save the id of the last tweet for future timeline synchs
				setTimelineSinceId(lastId, getBaseContext());
			}
			
			// save the timestamp of the last update
			setLastTimelineUpdate(new Date(), getBaseContext());
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Updates the list of friends
	 * @author thossmann
	 *
	 */
	private class UpdateFriendsTask extends AsyncTask<Void, Void, List<Number>> {
		@Override
	     protected List<Number> doInBackground(Void... params) {
			Log.i(TAG, "updating friends");
			ShowUserListActivity.setLoading(true);
			
			List<Number> friendsList = null;
			
			try {
				friendsList = twitter.getFriendIDs();

			} catch (Exception ex) {
				ShowUserListActivity.setLoading(false);
				Log.e(TAG, "Error while loading friends list: " + ex.getMessage());
			}
	         
	        return friendsList;
	     }

		@Override
	     protected void onPostExecute(List<Number> result) {
			if(result==null) {
				Log.i(TAG, "null");
				ShowUserListActivity.setLoading(false);
				return;
			}
			
			Log.i(TAG, "done.. what now? " + result.size());
			
			if(!result.isEmpty()){
				for (Number userId: result) {
					
					// do we already have the user?
					Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
					String[] projection = {"_id"};
					
					ContentValues cv = new ContentValues();
					// all we know is the user id and that we follow them
					cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_ID, userId.longValue());
					cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOW, 1);
					cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS, TwitterUsers.FLAG_TO_UPDATE);

					Cursor c = getContentResolver().query(uri, projection, TwitterUsers.TWITTERUSERS_COLUMNS_ID+"="+userId, null, null);
					if(c.getCount() == 0){ // we don't have the local user in the DB yet!
						Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
						getContentResolver().insert(insertUri, cv);
					} else {
						c.moveToFirst();
						if(c.isNull(c.getColumnIndex("_id"))){
							throw new IllegalStateException("Error while loading user from the DB ");
						} else {
							Uri updateUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + c.getInt(c.getColumnIndex("_id")));
							getContentResolver().update(updateUri, cv, null, null);
						}
					}
					c.close();
				}
			}
			
			// save the timestamp of the last update
			setLastFriendsUpdate(new Date(), getBaseContext());
			ShowUserListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Updates the list of followers
	 * @author thossmann
	 *
	 */
	private class UpdateFollowersTask extends AsyncTask<Void, Void, List<Number>> {
		@Override
	     protected List<Number> doInBackground(Void... params) {
			Log.i(TAG, "updating followers");
			ShowUserListActivity.setLoading(true);
			
			List<Number> followersList = null;
			
			try {
				followersList = twitter.getFollowerIDs();

			} catch (Exception ex) {
				ShowUserListActivity.setLoading(false);
				Log.e(TAG, "Error while loading follower list: " + ex.getMessage());
			}
	         
	        return followersList;
	     }

		@Override
	     protected void onPostExecute(List<Number> result) {
			if(result==null) {
				Log.i(TAG, "null");
				ShowUserListActivity.setLoading(false);
				return;
			}
			
			Log.i(TAG, "done.. what now? " + result.size());
			
			if(!result.isEmpty()){
				for (Number userId: result) {
					
					// do we already have the user?
					Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
					String[] projection = {"_id"};
					
					ContentValues cv = new ContentValues();
					// all we know is the user id and that we follow them
					cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_ID, userId.longValue());
					cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOWING, 1);
					cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS, TwitterUsers.FLAG_TO_UPDATE);

					Cursor c = getContentResolver().query(uri, projection, TwitterUsers.TWITTERUSERS_COLUMNS_ID+"="+userId, null, null);
					if(c.getCount() == 0){ // we don't have the local user in the DB yet!
						Uri insertUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
						getContentResolver().insert(insertUri, cv);
					} else {
						c.moveToFirst();
						if(c.isNull(c.getColumnIndex("_id"))){
							throw new IllegalStateException("Error while loading user from the DB ");
						} else {
							Uri updateUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS + "/" + c.getInt(c.getColumnIndex("_id")));
							getContentResolver().update(updateUri, cv, null, null);
						}
					}
					c.close();
				}
			}
			
			// save the timestamp of the last update
			setLastFollowerUpdate(new Date(), getBaseContext());
			ShowUserListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Post a tweet to twitter
	 * @author thossmann
	 */
	private class UpdateStatusTask extends AsyncTask<Long, Void, Twitter.Status> {
		
		long rowId;
		int flags;
		
		@Override
	     protected Twitter.Status doInBackground(Long... rowId) {
			ShowTweetListActivity.setLoading(true);
			this.rowId = rowId[0];
			
			Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			if(c.getCount() == 0){
				Log.i(TAG, "UpdateStatusTask: Tweet not found " + this.rowId);
				return null;
			}
			c.moveToFirst();
			flags = c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));

			Twitter.Status tweet = null;
			
			try {
				String text = c.getString(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TEXT));
				
				if(!(c.getDouble(c.getColumnIndex(Tweets.TWEETS_COLUMNS_LAT))==0 & c.getDouble(c.getColumnIndex(Tweets.TWEETS_COLUMNS_LNG))==0)){
					double[] location = {c.getDouble(c.getColumnIndex(Tweets.TWEETS_COLUMNS_LAT)),c.getDouble(c.getColumnIndex(Tweets.TWEETS_COLUMNS_LNG))}; 
					twitter.setMyLocation(location);
					Log.i(TAG, "Location set: " + location[0] +" " + location[1]);
				} else {
					twitter.setMyLocation(null);
				}
				if(c.getColumnIndex(Tweets.TWEETS_COLUMNS_REPLYTO)>=0){
					tweet = twitter.updateStatus(text, c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_REPLYTO)));
				} else {
					tweet = twitter.updateStatus(text);
				}
				
			} catch(IllegalArgumentException ex) { // TODO: properly handle the different exceptions!
				Log.e(TAG, "IllegalArgument error while posting tweet: " + ex);
			} catch(TwitterException.Repetition ex) {
				Log.e(TAG, "Repetition error while posting tweet: " + ex);
			} catch(TwitterException.Parsing ex) {
				Log.e(TAG, "Parsing error while posting tweet: " + ex);
			} catch(TwitterException.Unexplained ex) {
				Log.e(TAG, "Unexplained error while posting tweet: " + ex);
			} catch (Exception ex) {
				Log.e(TAG, "Unknown error while posting tweet: " + ex);
			} finally {
				c.close();
			}
	        return tweet;
	     }

		/**
		 * Clear to insert flag and update the tweet with the information from twitter
		 */
		@Override
	     protected void onPostExecute(Twitter.Status result) {
			if(result==null) {
				ShowTweetListActivity.setLoading(false);
				return;
			}

			Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			
			ContentValues cv = getTweetContentValues(result);
			cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags & ~(Tweets.FLAG_TO_INSERT));
			
			getContentResolver().update(queryUri, cv, null, null);
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Gets user info from twitter
	 * @author thossmann
	 */
	private class UpdateUserTask extends AsyncTask<Long, Void, Twitter.User> {
		
		long rowId;
		int flags;
		
		@Override
	     protected Twitter.User doInBackground(Long... rowId) {
			ShowUserListActivity.setLoading(true);
			this.rowId = rowId[0];
			
			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			if(c.getCount() == 0){
				Log.i(TAG, "UpdateUserTask: User not found " + this.rowId);
				c.close();
				return null;
			}
			c.moveToFirst();
			flags = c.getInt(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS));

			Twitter.User user = null;
			
			try {
				user = twitter.show(c.getLong(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_ID)));

			} catch (Exception ex) {
				Log.e(TAG, "Error while loading user: " + ex);
				ShowUserListActivity.setLoading(false);
			} finally {
				c.close();
			}
	        return user;
	     }

		/**
		 * Clear to update flag and update the user with the information from twitter
		 */
		@Override
	     protected void onPostExecute(Twitter.User result) {
			// we get null if something went wrong
			ContentValues cv = getUserContentValues(result);
			if(result!=null) {
				cv= getUserContentValues(result);
				cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_LASTUPDATE, System.currentTimeMillis());
			}
			// we clear the to update flag in any case
			cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS, flags & ~(TwitterUsers.FLAG_TO_UPDATE));
			
			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);			
			getContentResolver().update(queryUri, cv, null, null);
			ShowUserListActivity.setLoading(false);
			
			// if we have a profile_image_url, we dowload the image
			(new UpdateProfileImageTask()).execute(rowId);
	     }

	 }
	
	/**
	 * Gets profile image from twitter
	 * @author thossmann
	 */
	private class UpdateProfileImageTask extends AsyncTask<Long, Void, HttpEntity> {
		
		long rowId;
		
		@Override
	     protected HttpEntity doInBackground(Long... rowId) {
			ShowUserListActivity.setLoading(true);
			this.rowId = rowId[0];
			
			Log.i(TAG, "loading image..");
			
			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			if(c.getCount() == 0){
				Log.i(TAG, "UpdateUserTask: User not found " + this.rowId);
				c.close();
				return null;
			}
			c.moveToFirst();
			
			String imageUrl = null;
			// this should not happen
			if(c.isNull(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_IMAGEURL))){
				c.close();
				return null;
			} else {
				imageUrl = c.getString(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_IMAGEURL));
			}
			
			HttpEntity entity = null;
			
			try {

				DefaultHttpClient mHttpClient = new DefaultHttpClient();
				HttpGet mHttpGet = new HttpGet(imageUrl);
				HttpResponse mHttpResponse = mHttpClient.execute(mHttpGet);
				if (mHttpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				  entity = mHttpResponse.getEntity();
				}

			} catch (Exception ex) {
				Log.e(TAG, "Error while loading profile image: " + ex);
			} finally {
				c.close();
			}
	        return entity;
	     }

		/**
		 * Clear to update flag and update the user with the information from twitter
		 */
		@Override
	     protected void onPostExecute(HttpEntity result) {
			// we get null if something went wrong, we just stop
			if(result == null) {
				Log.i(TAG, "error while loading profile image!");
				return;
			}
			
			ContentValues cv = new ContentValues();
			try {
				cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE, EntityUtils.toByteArray(result));
			} catch (IOException e) {
				Log.i(TAG, "IOException while getting image from http entity");
				return;
			}
			
			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);			
			getContentResolver().update(queryUri, cv, null, null);
			ShowUserListActivity.setLoading(false);
			
	     }

	 }
	
	/**
	 * Delete a tweet from twitter and from the content provider
	 * @author thossmann
	 */
	private class DestroyStatusTask extends AsyncTask<Long, Void, Integer> {
		
		long rowId;
		int flags;
		
		@Override
	     protected Integer doInBackground(Long... rowId) {
			ShowTweetListActivity.setLoading(true);
			this.rowId = rowId[0];
			Integer result = null;
			
			Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			// making sure the tweet was found in the content provider
			if(c.getCount() == 0){
				Log.i(TAG, "DestroyStatusTask: Tweet not found " + this.rowId);
				return null;
			}
			c.moveToFirst();
			
			// checking if we really have an official Twitter ID
			if(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)<0 | c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)) == 0){
				Log.i(TAG, "DestroyStatusTask: Tweet has no ID! " + this.rowId);
				c.close();
				return null;
			}

			flags = c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));
			try {
				twitter.destroyStatus(c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)));
				result = 1;
			} catch (Exception ex) {
				ShowTweetListActivity.setLoading(false);
				Log.e(TAG, "Error while deleting tweet: " + ex);
				return null;
			} finally {
				c.close();
			}

	        return result;
	     }

		/**
		 * If successful, we delete the tweet also locally. If not successful, we keep the tweet and clear the todelete flag
		 */
		@Override
	     protected void onPostExecute(Integer result) {
			if(result == null){
				ContentValues cv = new ContentValues();
				cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags & ~Tweets.FLAG_TO_DELETE);
				Uri updateUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
				getContentResolver().update(updateUri, cv, null, null);

			} else {
				Uri deleteUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
				getContentResolver().delete(deleteUri, null, null);
			}
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Favorite a tweet on twitter and clear the respective flag locally
	 * @author thossmann
	 */
	private class FavoriteStatusTask extends AsyncTask<Long, Void, Integer> {
		
		long rowId;
		int flags;
		
		@SuppressWarnings("deprecation")
		@Override
	     protected Integer doInBackground(Long... rowId) {
			ShowTweetListActivity.setLoading(true);
			this.rowId = rowId[0];
			Integer result = null;
			
			Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			// making sure the Tweet was found in the content provider
			if(c.getCount() == 0){
				Log.i(TAG, "FavoriteStatusTask: Tweet not found " + this.rowId);
				return null;
			}
			c.moveToFirst();

			// making sure we have an official Tweet ID from Twitter
			if(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)<0 | c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)) == 0){
				Log.i(TAG, "FavoriteStatusTask: Tweet has no ID! " + this.rowId);
				c.close();
				return null;
			}

			// save the flags for clearing the to favorite flag later
			flags = c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));
			try {
				twitter.setFavorite(new Twitter.Status(null, null, c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)), null), true);
				result = 1;
			} catch (TwitterException.Repetition ex){
				// we get a repetition exception if the tweet was already favorited
				Log.i(TAG, "Tweet already favorited!");
			} catch (Exception ex) {
				ShowTweetListActivity.setLoading(false);
				Log.e(TAG, "Error while favoriting tweet: " + ex);
			} finally {
				c.close();
			}

	        return result;
	     }

		/**
		 * After favoriting Twitter, we clear the to favorite flag locally
		 */
		@Override
	     protected void onPostExecute(Integer result) {
			ContentValues cv = new ContentValues();
			cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags & ~Tweets.FLAG_TO_FAVORITE);
			// we get null if there was a problem with favoriting (already a favorite, etc.).
			if(result!=null) {
				cv.put(Tweets.TWEETS_COLUMNS_FAVORITED, 1);
			}
			
			Uri updateUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			getContentResolver().update(updateUri, cv, null, null);
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Unfavorite a tweet on twitter and clear the respective flag locally
	 * @author thossmann
	 */
	private class UnfavoriteStatusTask extends AsyncTask<Long, Void, Integer> {
		
		long rowId;
		int flags;
		
		@SuppressWarnings("deprecation")
		@Override
	     protected Integer doInBackground(Long... rowId) {
			ShowTweetListActivity.setLoading(true);
			this.rowId = rowId[0];
			Integer result = null;
			
			Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			// making sure the Tweet was found in the content provider
			if(c.getCount() == 0){
				Log.i(TAG, "UnfavoriteStatusTask: Tweet not found " + this.rowId);
				c.close();
				return null;
			}
			c.moveToFirst();

			// making sure we have an official Tweet ID from Twitter
			if(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)<0 | c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)) == 0){
				Log.i(TAG, "UnavoriteStatusTask: Tweet has no ID! " + this.rowId);
				c.close();
				return null;
			}

			// save the flags for clearing the to favorite flag later
			flags = c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));
			try {
				twitter.setFavorite(new Twitter.Status(null, null, c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)), null), false);
				result = 1;
			} catch (TwitterException.Repetition ex){
				// we get a repetition exception if the tweet was not a favorite
				Log.i(TAG, "Tweet not a favorite!");
			}catch (Exception ex) {
				ShowTweetListActivity.setLoading(false);
				Log.e(TAG, "Error while unfavoriting tweet: " + ex);
			} finally {
				c.close();
			}
	        return result;
	     }

		/**
		 * After deleting from Twitter, we clear the to unfavorite flag locally
		 */
		@Override
	     protected void onPostExecute(Integer result) {
			ContentValues cv = new ContentValues();
			cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags & ~Tweets.FLAG_TO_UNFAVORITE);

			if(result!=null) {
				cv.put(Tweets.TWEETS_COLUMNS_FAVORITED, 0);
			}
			
			Uri updateUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			getContentResolver().update(updateUri, cv, null, null);
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Retweet a tweet on twitter and clear the respective flag locally
	 * @author thossmann
	 */
	private class RetweetStatusTask extends AsyncTask<Long, Void, Integer> {
		
		long rowId;
		int flags;
		
		@SuppressWarnings("deprecation")
		@Override
	     protected Integer doInBackground(Long... rowId) {
			ShowTweetListActivity.setLoading(true);
			this.rowId = rowId[0];
			
			Uri queryUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			// making sure the Tweet was found in the content provider
			if(c.getCount() == 0){
				Log.i(TAG, "RetweetStatusTask: Tweet not found " + this.rowId);
				c.close();
				return null;
			}
			c.moveToFirst();

			// making sure we have an official Tweet ID from Twitter
			if(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)<0 | c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)) == 0){
				Log.i(TAG, "RetweetStatusTask: Tweet has no ID! " + this.rowId);
				c.close();
				return null;
			}

			// save the flags for clearing the to favorite flag later
			flags = c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_FLAGS));
			try {
				twitter.retweet(new Twitter.Status(null, null, c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TID)), null));
			} catch (Exception ex) {
				ShowTweetListActivity.setLoading(false);
				Log.e(TAG, "Error while retweeting tweet: " + ex);
			} finally {
				c.close();
			}
	        return 1;
	     }

		/**
		 * After retweeting, we clear the to to retweet flag locally
		 */
		@Override
	     protected void onPostExecute(Integer result) {
			ContentValues cv = new ContentValues();
			cv.put(Tweets.TWEETS_COLUMNS_FLAGS, flags & ~Tweets.FLAG_TO_RETWEET);

			if(result!=null) {
				cv.put(Tweets.TWEETS_COLUMNS_RETWEETED, 1);
			}
			
			Uri updateUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+this.rowId);
			getContentResolver().update(updateUri, cv, null, null);
			ShowTweetListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Send a follow request to Twitter
	 * @author thossmann
	 */
	private class FollowUserTask extends AsyncTask<Long, Void, Twitter.User> {
		
		long rowId;
		int flags;
		
		@Override
	     protected Twitter.User doInBackground(Long... rowId) {
			
			Log.i(TAG, "following.. ");
			ShowUserListActivity.setLoading(true);
			this.rowId = rowId[0];
			
			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			if(c.getCount() == 0){
				Log.i(TAG, "FollowUserTask: User not found " + this.rowId);
				return null;
			}
			c.moveToFirst();
			flags = c.getInt(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS));

			Twitter.User user = null;
			
			try {
				user = twitter.follow(c.getString(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME)));
			} catch (Exception ex) {
				Log.e(TAG, "Error while following user: " + ex);
				ShowUserListActivity.setLoading(false);
			} finally {
				c.close();
			}
	        return user;
	     }

		/**
		 * Clear to follow flag and update the user with the information from twitter
		 */
		@Override
	     protected void onPostExecute(Twitter.User result) {
			// we get null if: the user does not exist or is protected
			// in any case we clear the to follow flag
			ContentValues cv = getUserContentValues(result);
			cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS, (flags & ~TwitterUsers.FLAG_TO_FOLLOW));
			// we get a user if the follow was successful
			// in that case we also mark the user as followed in the DB
			if(result!=null) {
				cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOW, 1);
			}

			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);
						
			getContentResolver().update(queryUri, cv, null, null);
			ShowUserListActivity.setLoading(false);
	     }

	 }
	
	/**
	 * Unfollow a user on Twitter
	 * @author thossmann
	 */
	private class UnfollowUserTask extends AsyncTask<Long, Void, Twitter.User> {
		
		long rowId;
		int flags;
		
		@Override
	     protected Twitter.User doInBackground(Long... rowId) {
			
			Log.i(TAG, "unfollowing.. ");
			ShowUserListActivity.setLoading(true);
			this.rowId = rowId[0];
			
			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);
			Cursor c = getContentResolver().query(queryUri, null, null, null, null);
			
			if(c.getCount() == 0){
				Log.i(TAG, "UnfollowUserTask: User not found " + this.rowId);
				return null;
			}
			c.moveToFirst();
			flags = c.getInt(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS));

			Twitter.User user = null;
			
			try {
				user = twitter.stopFollowing(c.getString(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME)));
			} catch (Exception ex) {
				Log.e(TAG, "Error while unfollowing user: " + ex);
				ShowUserListActivity.setLoading(false);
			} finally {
				c.close();
			}
	        return user;
	     }

		/**
		 * Clear to unfollow flag and update the user with the information from twitter
		 */
		@Override
	     protected void onPostExecute(Twitter.User result) {
			// we get null if we did not follow the user
			// in any case we clear the to follow flag
			ContentValues cv = getUserContentValues(result);
			cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS, (flags & ~TwitterUsers.FLAG_TO_UNFOLLOW));
			// we get a user if the follow was successful
			// in that case we remove the follow in the DB
			if(result!=null) {
				cv.put(TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOW, 0);
				Log.i(TAG, "unfollowed successfully.. ");
			}
			
			Uri queryUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+this.rowId);
						
			getContentResolver().update(queryUri, cv, null, null);
			ShowUserListActivity.setLoading(false);
	     }

	 }
}
