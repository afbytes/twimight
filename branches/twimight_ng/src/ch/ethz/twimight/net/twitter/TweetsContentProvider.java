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

import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.DBOpenHelper;
import ch.ethz.twimight.net.opportunistic.ScanningService;
import ch.ethz.twimight.security.CertificateManager;
import ch.ethz.twimight.security.KeyManager;
import ch.ethz.twimight.util.Constants;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

/**
 * The content provider for all kinds of tweets (normal, disaster, favorites, mentions). 
 * The URIs and column names are defined int class Tweets.
 * @author thossmann
 *
 */
public class TweetsContentProvider extends ContentProvider {

	private static final String TAG = "TweetsContentProvider";

	
	private SQLiteDatabase database;
	private DBOpenHelper dbHelper;
	
	private static UriMatcher tweetUriMatcher;
		
	private static final int TWEETS = 1;
	private static final int TWEETS_ID = 2;
	
	private static final int TWEETS_TIMELINE = 3;
	private static final int TWEETS_TIMELINE_NORMAL = 4;
	private static final int TWEETS_TIMELINE_DISASTER = 5;
	private static final int TWEETS_TIMELINE_ALL = 6;
	
	private static final int TWEETS_FAVORITES = 7;
	private static final int TWEETS_FAVORITES_NORMAL = 8;
	private static final int TWEETS_FAVORITES_DISASTER = 9;
	private static final int TWEETS_FAVORITES_ALL = 10;

	private static final int TWEETS_MENTIONS = 11;
	private static final int TWEETS_MENTIONS_NORMAL = 12;
	private static final int TWEETS_MENTIONS_DISASTER = 13;
	private static final int TWEETS_MENTIONS_ALL = 14;
	
	// Here we define all the URIs this provider knows
	static{
		tweetUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS, TWEETS);
		
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/#", TWEETS_ID);
		
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE, TWEETS_TIMELINE);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_FAVORITES, TWEETS_FAVORITES);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_MENTIONS, TWEETS_MENTIONS);
		
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_NORMAL, TWEETS_TIMELINE_NORMAL);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER, TWEETS_TIMELINE_DISASTER);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL, TWEETS_TIMELINE_ALL);

		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_NORMAL, TWEETS_FAVORITES_NORMAL);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_DISASTER, TWEETS_FAVORITES_DISASTER);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_ALL, TWEETS_FAVORITES_ALL);

		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_NORMAL, TWEETS_MENTIONS_NORMAL);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_DISASTER, TWEETS_MENTIONS_DISASTER);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_ALL, TWEETS_MENTIONS_ALL);


	}
	
	/**
	 * onCreate we initialize and open the DB.
	 */
	@Override
	public boolean onCreate() {
		dbHelper = DBOpenHelper.getInstance(getContext());
		database = dbHelper.getWritableDatabase();
		return true;
	}

	/**
	 * Returns the MIME types (defined in Tweets) of a URI
	 */
	@Override
	public String getType(Uri uri) {
		switch(tweetUriMatcher.match(uri)){
			case TWEETS: return Tweets.TWEETS_CONTENT_TYPE;
			
			case TWEETS_ID: return Tweets.TWEET_CONTENT_TYPE;
			
			case TWEETS_TIMELINE: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_FAVORITES: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_MENTIONS: return Tweets.TWEETS_CONTENT_TYPE;
			
			case TWEETS_TIMELINE_NORMAL: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_TIMELINE_DISASTER: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_TIMELINE_ALL: return Tweets.TWEETS_CONTENT_TYPE;
			
			case TWEETS_FAVORITES_NORMAL: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_FAVORITES_DISASTER: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_FAVORITES_ALL: return Tweets.TWEETS_CONTENT_TYPE;
			
			case TWEETS_MENTIONS_NORMAL: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_MENTIONS_DISASTER: return Tweets.TWEETS_CONTENT_TYPE;
			case TWEETS_MENTIONS_ALL: return Tweets.TWEETS_CONTENT_TYPE;
	
			default: throw new IllegalArgumentException("Unknown URI: " + uri);	
		}
	}

	/**
	 * Query the timeline table
	 * TODO: Create the queries more elegantly..
	 */
	@Override
	public Cursor query(Uri uri, String[] projection, String where, String[] whereArgs, String sortOrder) {
		
		Log.i(TAG, "query: " + uri);
		
		if(TextUtils.isEmpty(sortOrder)) sortOrder = Tweets.DEFAULT_SORT_ORDER;
		
		Cursor c;
		String sql;
		Intent i;
		switch(tweetUriMatcher.match(uri)){
			case TWEETS: 
				Log.i(TAG, "query matched... TWEETS");
				c = database.query(DBOpenHelper.TABLE_TWEETS, projection, where, whereArgs, null, null, sortOrder);
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				break;
			
			case TWEETS_ID: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TID + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_SOURCE + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETCOUNT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_LAT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_LNG + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." + "_id AS userRowId, "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_ID + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE " + DBOpenHelper.TABLE_TWEETS+ "._id=" + uri.getLastPathSegment() + ";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				c.setNotificationUri(getContext().getContentResolver(), uri);

				break;
						
			case TWEETS_TIMELINE_NORMAL:
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE "+DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_ISDISASTER+" =0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				// TODO: Correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch timeline request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
				getContext().startService(i);
				break;
			case TWEETS_TIMELINE_DISASTER: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE "+DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_ISDISASTER+" =1 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				// TODO: Correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch timeline request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
				getContext().startService(i);
				break;
			case TWEETS_TIMELINE_ALL: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch timeline request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
				getContext().startService(i);
				break;
			
			case TWEETS_FAVORITES_NORMAL: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE ("  +DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_DELETE +"=0) AND "
					+ "(" + DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED+">0 OR "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_FAVORITE +">0) AND "
					+ DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_ISDISASTER+" =0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch favorites request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
				getContext().startService(i);

				break;
			case TWEETS_FAVORITES_DISASTER: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE ("  +DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_DELETE +"=0) AND "
					+ "(" + DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED+">0 OR "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_FAVORITE +">0) AND "
					+ DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_ISDISASTER+" >0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch favorites request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
				getContext().startService(i);
				break;
				
			case TWEETS_FAVORITES_ALL: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE ("  +DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_DELETE +"=0) AND "
					+ "(" + DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED+">0 OR "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_FAVORITE +">0) "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch favorites request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
				getContext().startService(i);	
				break;
				
			case TWEETS_MENTIONS_NORMAL: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE ("  +DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_DELETE +"=0) AND "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_MENTIONS+">0 AND "
					+ DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_ISDISASTER+" =0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch mentions request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
				getContext().startService(i);

				break;
			case TWEETS_MENTIONS_DISASTER: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE ("  +DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_DELETE +"=0) AND "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_MENTIONS+">0 AND "
					+ DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_ISDISASTER+" >0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch mentions request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
				getContext().startService(i);

				break;
			case TWEETS_MENTIONS_ALL: 
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "LEFT JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.TWEETS_COLUMNS_USER+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.TWITTERUSERS_COLUMNS_ID+ " "
					+ "WHERE ("  +DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_FLAGS + "&" +Tweets.FLAG_TO_DELETE +"=0) AND "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.TWEETS_COLUMNS_MENTIONS+">0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				Log.i(TAG, "DB query: " + sql);
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch mentions request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
				getContext().startService(i);

				break;	
			default: throw new IllegalArgumentException("Unsupported URI: " + uri);	
		}
		
		return c;
	}

	/**
	 * Insert a tweet into the DB
	 */
	@Override
	public Uri insert(Uri uri, ContentValues values) {
		Log.i(TAG, "insert: " + uri);
		
		switch(tweetUriMatcher.match(uri)){
			case TWEETS_TIMELINE_NORMAL:
				/*
				 *  First, we check if we already have a tweets with the same disaster ID.
				 *  If yes, three cases are possible
				 *  1 If the existing tweet is a disaster tweet, it was uploaded to the server and
				 *    now we receive it from the server. In this case we update the disaster tweet
				 *    accordingly.
				 *  2 If the existing tweet is a tweet of our own which was flagged to insert, the insert
				 *    operation may have been successful but the success was not registered locally.
				 *    In this case we update the new tweet with the new information
				 *  3 It may be a hash function collision (two different tweets have the same hash code)
				 *    Probability of this should be small.  
				 */
				int disasterId = getDisasterID(values);
				
				Cursor c = query(Tweets.CONTENT_URI, null, Tweets.TWEETS_COLUMNS_DISASTERID+"="+disasterId, null, null);
				if(c.getCount()>0){
					Log.i(TAG, "PANIIIIIIIIIIIIIIIIIIIIC: "+c.getCount()+" tweets with the same disaster ID");

					c.moveToFirst();
					while(!c.isAfterLast()){
						if(c.getInt(c.getColumnIndex(Tweets.TWEETS_COLUMNS_ISDISASTER))>0){
							// check if the text is the same
							if(c.getString(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TEXT)).equals(values.getAsString(Tweets.TWEETS_COLUMNS_TEXT))){
								Log.i(TAG, "Disaster tweet with same text and disaster ID");
								Uri updateUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+Integer.toString(c.getInt(c.getColumnIndex("_id"))));
								update(updateUri, values, null, null);
								return updateUri;
							}
						} else if(Long.toString(c.getLong(c.getColumnIndex(Tweets.TWEETS_COLUMNS_USER))).equals(LoginActivity.getTwitterId(getContext()))) {
							// check if the text is the same
							if(c.getString(c.getColumnIndex(Tweets.TWEETS_COLUMNS_TEXT)).equals(values.getAsString(Tweets.TWEETS_COLUMNS_TEXT))){
								Log.i(TAG, "Normal tweet with same text and disaster ID");
								Uri updateUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+Integer.toString(c.getInt(c.getColumnIndex("_id"))));
								update(updateUri, values, null, null);
								return updateUri;
							}
						}
						c.moveToNext();
					}
					
				}
				// if none of the before was true, this is a proper new tweet which we now insert
				return insertTweet(values);
				
			case TWEETS_TIMELINE_DISASTER:
				// in disaster mode, we set the is disaster flag 
				//and sign the tweet (if we have a certificate for our key pair)
				values.put(Tweets.TWEETS_COLUMNS_ISDISASTER, 1);
				CertificateManager cm = new CertificateManager(getContext());
				KeyManager km = new KeyManager(getContext());
				if(LoginActivity.getTwitterId(getContext()).equals(values.getAsInteger(Tweets.TWEETS_COLUMNS_USER).toString())){
					Log.i(TAG, "our own tweet!");
					if(cm.hasCertificate()){
						Log.i(TAG, "signing tweet");
						
						// we put the signature
						String text = values.getAsString(Tweets.TWEETS_COLUMNS_TEXT);
						String userId = LoginActivity.getTwitterId(getContext()).toString();
						
						String signature = km.getSignature(new String(text+userId));
						values.put(Tweets.TWEETS_COLUMNS_SIGNATURE, signature);
						
						// and the certificate
						values.put(Tweets.TWEETS_COLUMNS_CERTIFICATE, cm.getCertificate());
						
						// and set the is_verified flag to show that the tweet is signed
						values.put(Tweets.TWEETS_COLUMNS_ISVERIFIED, 1);
					} else {
						Log.i(TAG, "no certificate, not signing tweet");
						values.put(Tweets.TWEETS_COLUMNS_ISVERIFIED, 0);
					}
					// if we are in disaster mode, we give the content provider half a 
					// second to insert the tweet and then schedule a scanning operation
					if(PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("prefDisasterMode", false) == true){
						Intent i = new Intent(getContext(), ScanningService.class);
						i.putExtra(ScanningService.FORCE_SCAN, true);
						i.putExtra(ScanningService.FORCE_SCAN_DELAY, 500L);
						getContext().startService(i);
					}
				} else {
					Log.i(TAG, "not our own tweet!");
					String certificate = values.getAsString(Tweets.TWEETS_COLUMNS_CERTIFICATE);
					// check validity
					if(cm.checkCertificate(cm.parsePem(certificate), values.getAsLong(Tweets.TWEETS_COLUMNS_USER).toString())){
						Log.i(TAG, "certificate is valid!");
						// check signature
						String signature = values.getAsString(Tweets.TWEETS_COLUMNS_SIGNATURE);
						String text = values.getAsString(Tweets.TWEETS_COLUMNS_TEXT) + values.getAsString(Tweets.TWEETS_COLUMNS_USER);
						if(km.checkSinature(cm.parsePem(certificate), signature, text)){
							values.put(Tweets.TWEETS_COLUMNS_ISVERIFIED, 1);
							Log.i(TAG, "signature is valid!");
						} else {
							values.put(Tweets.TWEETS_COLUMNS_ISVERIFIED, 0);
							Log.i(TAG, "signature is not valid!");
						}
					
					} else {
						values.put(Tweets.TWEETS_COLUMNS_ISVERIFIED, 0);
						Log.i(TAG, "certificate is not valid!");
					}
					
				}

				return insertTweet(values);
			default: throw new IllegalArgumentException("Unsupported URI: " + uri);	
		}
	}

	/**
	 * Update a tweet
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		if(tweetUriMatcher.match(uri) != TWEETS_ID) throw new IllegalArgumentException("Unsupported URI: " + uri);
		
		int nrRows = database.update(DBOpenHelper.TABLE_TWEETS, values, "_id="+uri.getLastPathSegment() , null);
		if(nrRows >= 0){
			getContext().getContentResolver().notifyChange(uri, null);
			getContext().getContentResolver().notifyChange(Tweets.CONTENT_URI, null);
			Log.i(TAG, "Tweet updated! " + values);
			
			// Trigger synch
			Intent i = new Intent(TwitterService.SYNCH_ACTION);
			i.putExtra("synch_request", TwitterService.SYNCH_TWEET);
			i.putExtra("rowId", new Long(uri.getLastPathSegment()));
			getContext().startService(i);

			return nrRows;
		} else {
			throw new IllegalStateException("Could not update tweet " + values);
		}
	}
	
	/**
	 * Delete a local tweet from the DB
	 */
	@Override
	public int delete(Uri uri, String arg1, String[] arg2) {
		if(tweetUriMatcher.match(uri) != TWEETS_ID) throw new IllegalArgumentException("Unsupported URI: " + uri);
		int nrRows = database.delete(DBOpenHelper.TABLE_TWEETS, "_id="+uri.getLastPathSegment(), null);
		getContext().getContentResolver().notifyChange(Tweets.CONTENT_URI, null);
		return nrRows;
	}
	
	/**
	 * Keeps the timeline table at acceptable size
	 */
	private void purgeTimeline(){
		/*
		 *  1. Delete all tweets which are 
		 *  - not favorites 
		 *  - not mentions
		 *  - do not have transactional flags
		 *  - are beyond the buffer size
		 *  NOTE: DELETE in android does not allow ORDER BY. Hence, the trick with the _id
		 *  TODO: let the caller select which "buffer" should be purged
		 */
		String sqlWhere;
		String sql;
		Cursor c;
		sqlWhere = Tweets.TWEETS_COLUMNS_FLAGS + "=0 AND "+Tweets.TWEETS_COLUMNS_FAVORITED+"=0 AND "+Tweets.TWEETS_COLUMNS_MENTIONS+"=0";
		sql = "DELETE FROM " + DBOpenHelper.TABLE_TWEETS + " "
				+"WHERE " +sqlWhere+ " AND "
				+"_id=(SELECT _id FROM "+DBOpenHelper.TABLE_TWEETS 
				+ " WHERE " + sqlWhere
				+ " LIMIT -1 OFFSET "
				+ Constants.TIMELINE_BUFFER_SIZE+");";
		Log.i(TAG, "Query: " + sql);
		c = database.rawQuery(sql, null);
		
		c.close();
		
		/*
		 *  2. Delete all tweets which 
		 *  - are favorites
		 *  - are not mentions
		 *  - don't have transactional flags 
		 *  - are beyond the favorites buffer size
		 */
		sqlWhere = Tweets.TWEETS_COLUMNS_FLAGS + "=0 AND "+Tweets.TWEETS_COLUMNS_FAVORITED+">0 AND "+Tweets.TWEETS_COLUMNS_MENTIONS+"=0";
		sql = "DELETE FROM " + DBOpenHelper.TABLE_TWEETS + " "
				+"WHERE " +sqlWhere+ " AND "
				+"_id=(SELECT _id FROM "+DBOpenHelper.TABLE_TWEETS 
				+ " WHERE " + sqlWhere
				+ " LIMIT -1 OFFSET "
				+ Constants.FAVORITES_BUFFER_SIZE+");";
		Log.i(TAG, "Query: " + sql);
		c = database.rawQuery(sql, null);
		
		c.close();
		
		/*
		 *  3. Delete all tweets which
		 *  - are mentions 
		 *  - are not favorites  
		 *  - don't have transactional flags 
		 *  - are beyond the mentions buffer size
		 */
		sqlWhere = Tweets.TWEETS_COLUMNS_FLAGS + "=0 AND "+Tweets.TWEETS_COLUMNS_FAVORITED+"=0 AND "+Tweets.TWEETS_COLUMNS_MENTIONS+">0";
		sql = "DELETE FROM " + DBOpenHelper.TABLE_TWEETS + " "
				+"WHERE " +sqlWhere+ " AND "
				+"_id=(SELECT _id FROM "+DBOpenHelper.TABLE_TWEETS 
				+ " WHERE " + sqlWhere
				+ " LIMIT -1 OFFSET "
				+ Constants.MENTIONS_BUFFER_SIZE+");";
		Log.i(TAG, "Query: " + sql);
		c = database.rawQuery(sql, null);
		
		c.close();

	}
	
	/**
	 * The screenname of the local user
	 * @return
	 */
	private String getLocalScreenName() {
		if(LoginActivity.getTwitterId(getContext()) == null) return null;
		
		Long localUserId = new Long(LoginActivity.getTwitterId(getContext()));
		
		Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
		
		Cursor c = getContext().getContentResolver().query(uri, null, TwitterUsers.TWITTERUSERS_COLUMNS_ID+"="+localUserId, null, null);
		
		if(c.getCount() == 0) return null;
		
		c.moveToFirst();
		return c.getString(c.getColumnIndex(TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME));
	}
	
	/**
	 * Computes the java String object hash code (32 bit) as the disaster ID of the tweet
	 * @param cv
	 * @return
	 */
	private int getDisasterID(ContentValues cv){
		String text = cv.getAsString(Tweets.TWEETS_COLUMNS_TEXT);
		String userId;
		if(!cv.containsKey(Tweets.TWEETS_COLUMNS_USER) | (cv.getAsString(Tweets.TWEETS_COLUMNS_USER)==null)){
			userId = LoginActivity.getTwitterId(getContext()).toString();
		} else {
			userId = cv.getAsString(Tweets.TWEETS_COLUMNS_USER);
		}
		
		return (new String(text+userId)).hashCode();
	}
	
	/**
	 * Input verification for new tweets
	 * @param values
	 * @return
	 */
	private boolean checkValues(ContentValues values){
		// TODO: Input validation
		return true;
	}
	
	/**
	 * Inserts a tweet into the DB
	 */
	private Uri insertTweet(ContentValues values){
		if(checkValues(values)){
			int flags = values.getAsInteger(Tweets.TWEETS_COLUMNS_FLAGS);
			
			if(!values.containsKey(Tweets.TWEETS_COLUMNS_CREATED)){
				// set the current timestamp
				values.put(Tweets.TWEETS_COLUMNS_CREATED, System.currentTimeMillis());
			}
			
			// the disaster ID must be set for all tweets (normal and disaster)
			values.put(Tweets.TWEETS_COLUMNS_DISASTERID, getDisasterID(values));
			
			// does it mention the local user?
			String text = values.getAsString(Tweets.TWEETS_COLUMNS_TEXT);
			String localUserScreenName = getLocalScreenName();
			if(text.contains("@"+localUserScreenName)){
				values.put(Tweets.TWEETS_COLUMNS_MENTIONS, 1);
			} else {
				values.put(Tweets.TWEETS_COLUMNS_MENTIONS, 0);
			}
			
			long rowId = database.insert(DBOpenHelper.TABLE_TWEETS, null, values);
			if(rowId >= 0){
				
				// delete everything that now falls out of the buffer
				purgeTimeline();
				
				Uri insertUri = ContentUris.withAppendedId(Tweets.CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(insertUri, null);
				
				Log.i(TAG, "Tweet inserted! ");
				if(flags>0){
					// start synch service with a synch tweet request
					Intent i = new Intent(TwitterService.SYNCH_ACTION);
					i.putExtra("synch_request", TwitterService.SYNCH_TWEET);
					i.putExtra("rowId", rowId);
					getContext().startService(i);
				}
				return insertUri;
			} else {
				throw new IllegalStateException("Could not insert tweet into timeline " + values);
			}
		} else {
			throw new IllegalArgumentException("Illegal tweet: " + values);
		}
	}


}
