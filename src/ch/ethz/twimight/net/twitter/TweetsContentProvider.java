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

import java.io.File;
import java.io.FileNotFoundException;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.ShowTweetListActivity;
import ch.ethz.twimight.data.DBOpenHelper;
import ch.ethz.twimight.net.opportunistic.ScanningAlarm;
import ch.ethz.twimight.net.opportunistic.ScanningService;
import ch.ethz.twimight.security.CertificateManager;
import ch.ethz.twimight.security.KeyManager;
import ch.ethz.twimight.util.Constants;

/**
 * The content provider for all kinds of tweets (normal, disaster, favorites, mentions). 
 * The URIs and column names are defined in class Tweets.
 * @author thossmann
 * @author pcarta
 */
public class TweetsContentProvider extends ContentProvider {

	private static final String TAG = "TweetsContentProvider";

	
	private SQLiteDatabase database;
	private DBOpenHelper dbHelper;
	private static String localScreenName;
	
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
	
	private static final int TWEETS_USER_ID = 15;
	
	private static final int TWEETS_SEARCH = 16;
		
	// Here we define all the URIs this provider knows
	static{		
		
		tweetUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS, TWEETS);
		
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/#", TWEETS_ID);
		
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.SEARCH, TWEETS_SEARCH);
		
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_TIMELINE, TWEETS_TIMELINE);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_FAVORITES, TWEETS_FAVORITES);
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_MENTIONS, TWEETS_MENTIONS);
		
		tweetUriMatcher.addURI(Tweets.TWEET_AUTHORITY, Tweets.TWEETS + "/" + Tweets.TWEETS_TABLE_USER + "/#", TWEETS_USER_ID);
		
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
	
	// for the status bar notification
	private static final int TWEET_NOTIFICATION_ID = 1;
	
	private static final int NOTIFY_MENTION = 2;
	private static final int NOTIFY_DISASTER = 3;
	private static final int NOTIFY_TWEET = 4;
	
	/**
	 * onCreate we initialize and open the DB.
	 */
	@Override
	public boolean onCreate() {
		dbHelper = DBOpenHelper.getInstance(getContext());
		database = dbHelper.getWritableDatabase();
		localScreenName = LoginActivity.getTwitterScreenname(getContext());
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
			
			case TWEETS_USER_ID: return Tweets.TWEETS_CONTENT_TYPE;
			
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
     * Provides read only access to files that have been downloaded and stored
     * in the provider cache. Specifically, in this provider, clients can
     * access the files of downloaded images.
     */
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException
    {
    	Log.i(TAG," inside openFile");
    	File root = getContext().getFilesDir();
    	if (!root.exists())
    		root.mkdirs();
    	Log.i(TAG,uri.getEncodedPath().toString());
        File path = new File(root, "/" + TwitterUsers.TWITTERUSERS_PICTURE + "/" + uri.getLastPathSegment());        

        int imode = 0;
        
        if (mode.contains("r")) imode |= ParcelFileDescriptor.MODE_READ_ONLY;          

        return ParcelFileDescriptor.open(path, imode);

       
    }

	/**
	 * Query the timeline table
	 * TODO: Create the queries more elegantly..
	 */
	@Override
	public synchronized Cursor query(Uri uri, String[] projection, String where, String[] whereArgs, String sortOrder) {
		
		if(TextUtils.isEmpty(sortOrder)) sortOrder = Tweets.DEFAULT_SORT_ORDER;
		
		Cursor c;
		String sql;
		Intent i;
		switch(tweetUriMatcher.match(uri)){
			case TWEETS: 
				Log.d(TAG, "Query TWEETS");
				c = database.query(DBOpenHelper.TABLE_TWEETS, projection, where, whereArgs, null, null, sortOrder);
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				break;
			
			case TWEETS_ID: 
				Log.d(TAG, "Query TWEETS_ID " + uri.getLastPathSegment());
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TID + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_SOURCE + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETCOUNT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_LAT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_LNG + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_BUFFER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." + "_id AS userRowId, "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_ID + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE " + DBOpenHelper.TABLE_TWEETS+ "._id=" + uri.getLastPathSegment() + ";";
				c = database.rawQuery(sql, null);
				c.setNotificationUri(getContext().getContentResolver(), uri);

				break;
						
			case TWEETS_SEARCH: // the search query must be given in the where argument
				Log.d(TAG, "Query SEARCH");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TID + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_SOURCE + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETCOUNT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_LAT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_LNG + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_BUFFER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." + "_id AS userRowId, "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_ID + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE  " + DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_TEXT+" LIKE '%" + where + "%' "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				c = database.rawQuery(sql, null);
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				//start synch service with a synch timeline request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_SEARCH_TWEETS);
				i.putExtra("query", where);
				getContext().startService(i);
				break;
				
			case TWEETS_TIMELINE_NORMAL:
				Log.d(TAG, "Query TIMELINE_NORMAL");

				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TID + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_TIMELINE+")!=0 "					
					+ "ORDER BY " + Tweets.REVERSE_SORT_ORDER
					+ " LIMIT 5;";
				c = database.rawQuery(sql, null);
				// TODO: Correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch timeline request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
				getContext().startService(i);
				break;
			case TWEETS_TIMELINE_DISASTER: 
				Log.d(TAG, "Query TIMELINE_DISASTER");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RECEIVED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CERTIFICATE + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_SIGNATURE + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_DISASTER+")!=0 "
					+ "OR ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_MYDISASTER+")!=0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				c = database.rawQuery(sql, null);
				// TODO: Correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch timeline request
				//i = new Intent(TwitterService.SYNCH_ACTION);
				//i.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
				//getContext().startService(i);
				break;
			case TWEETS_TIMELINE_ALL:
				Log.d(TAG, "Query TIMELINE_ALL");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_DISASTER+")!=0 "
					+ "OR ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_MYDISASTER+")!=0 "
					+ "OR ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_TIMELINE+")!=0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";

				c = database.rawQuery(sql, null);				
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch timeline request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
				getContext().startService(i);
				break;
				
			case TWEETS_USER_ID:
				Log.i(TAG, "Query TWEETS_USER_ID");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE "+DBOpenHelper.TABLE_USERS+"."+TwitterUsers.COL_ID+"="+uri.getLastPathSegment()+" "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				
				//TODO: could be done be a separate thread
				c = database.rawQuery(sql, null); 
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// get the screenname for updating user tweets
				Uri userUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
				String[] userProjection = {TwitterUsers.COL_SCREENNAME};
				Cursor userCursor = getContext().getContentResolver().query(userUri, userProjection, TwitterUsers.COL_ID+"="+uri.getLastPathSegment(), null, null);

				if(userCursor.getCount()>0){
					userCursor.moveToFirst();
					// start synch service with a synch user tweets request
					i = new Intent(TwitterService.SYNCH_ACTION);
					i.putExtra("synch_request", TwitterService.SYNCH_USERTWEETS);
					i.putExtra("screenname", userCursor.getString(userCursor.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
					getContext().startService(i); 
				}
				//userCursor.close();

				break;
			
			case TWEETS_FAVORITES_NORMAL: 
				Log.d(TAG, "Query FAVORITES_NORMAL");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_FAVORITES+")!=0 "
					+ "AND "+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_ISDISASTER+"=0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch favorites request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
				getContext().startService(i);

				break;
			case TWEETS_FAVORITES_DISASTER: 
				Log.d(TAG, "Query FAVORITES_DISASTER");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "					
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_FAVORITES+")!=0 "
					+ "AND "+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_ISDISASTER+">0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch favorites request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
				getContext().startService(i);
				break;
				
			case TWEETS_FAVORITES_ALL: 
				Log.d(TAG, "Query FAVORITES_ALL");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "					
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_FAVORITES+")!=0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch favorites request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
				getContext().startService(i);	
				break;
				
			case TWEETS_MENTIONS_NORMAL: 
				Log.d(TAG, "Query MENTIONS_NORMAL");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "					
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_MENTIONS+")!=0 "
					+ "AND "+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_ISDISASTER+"=0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch mentions request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
				getContext().startService(i);

				break;
			case TWEETS_MENTIONS_DISASTER: 
				Log.d(TAG, "Query MENTIONS_DISASTER");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "					
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_MENTIONS+")!=0 "
					+ "AND "+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_ISDISASTER+">0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
				c = database.rawQuery(sql, null);
				
				// TODO: correct notification URI
				c.setNotificationUri(getContext().getContentResolver(), Tweets.CONTENT_URI);
				
				// start synch service with a synch mentions request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
				getContext().startService(i);

				break;
			case TWEETS_MENTIONS_ALL: 
				Log.d(TAG, "Query MENTIONS_ALL");
				sql = "SELECT "
					+ DBOpenHelper.TABLE_TWEETS + "." + "_id AS _id, "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_TEXT + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_USER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_MENTIONS + ", "					
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_CREATED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_REPLYTO + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FAVORITED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_FLAGS + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISDISASTER + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_ISVERIFIED + ", "
					+ DBOpenHelper.TABLE_TWEETS + "." +Tweets.COL_RETWEETED_BY + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_SCREENNAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_NAME + ", "
					+ DBOpenHelper.TABLE_USERS + "." +TwitterUsers.COL_PROFILEIMAGE + " "
					+ "FROM "+DBOpenHelper.TABLE_TWEETS + " "
					+ "JOIN " + DBOpenHelper.TABLE_USERS + " " 
					+ "ON " +DBOpenHelper.TABLE_TWEETS+"." +Tweets.COL_SCREENNAME+ "=" +DBOpenHelper.TABLE_USERS+"." +TwitterUsers.COL_SCREENNAME+ " "
					+ "WHERE ("+DBOpenHelper.TABLE_TWEETS+"."+Tweets.COL_BUFFER+"&"+Tweets.BUFFER_MENTIONS+")!=0 "
					+ "ORDER BY " + Tweets.DEFAULT_SORT_ORDER +";";
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
	 * Inserts a bunch of tweets into the DB
	 */
	@Override
	public synchronized int bulkInsert(Uri uri, ContentValues[] values) {		
		int numInserted= 0;
		database.beginTransaction();
		try {			
			
			for (ContentValues value : values){
				
				if (insertTweetsTimelineNormal(value) != null) ;	
				numInserted++;
			}
			database.setTransactionSuccessful();
			
		} finally {
			database.endTransaction();
		}
		return numInserted;
	}

	/**
	 * Insert a tweet into the DB
	 */
	@Override
	public synchronized Uri insert(Uri uri, ContentValues values) {
		int disasterId;
		Cursor c;
		Uri insertUri = null; // the return value;
		
		switch(tweetUriMatcher.match(uri)){
			case TWEETS_TIMELINE_NORMAL:				
				
				insertUri = insertTweetsTimelineNormal(values);				

				break;
				
			case TWEETS_TIMELINE_DISASTER:
				Log.d(TAG, "Insert TWEETS_TIMELINE_DISASTER");
				// in disaster mode, we set the is disaster flag 
				//and sign the tweet (if we have a certificate for our key pair)
				values.put(Tweets.COL_ISDISASTER, 1);
				
				// if we already have a disaster tweet with the same disaster ID, 
				// we discard the new one
				disasterId = getDisasterID(values);
				c = database.query(DBOpenHelper.TABLE_TWEETS, null, Tweets.COL_DISASTERID+"="+disasterId+" AND "+Tweets.COL_ISDISASTER+">0", null, null, null, null);
				if(c.getCount()>0){
					c.moveToFirst();
					Uri oldUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+Long.toString(c.getLong(c.getColumnIndex("_id"))));
					c.close();
					return oldUri; 
				}
				
				CertificateManager cm = new CertificateManager(getContext());
				KeyManager km = new KeyManager(getContext());
				
				//verify whether I was the author or not
				if(LoginActivity.getTwitterId(getContext()).equals(values.getAsInteger(Tweets.COL_USER).toString())){
					if(cm.hasCertificate()){
						// we put the signature
						String text = values.getAsString(Tweets.COL_TEXT);
						String userId = LoginActivity.getTwitterId(getContext()).toString();
						
						String signature = km.getSignature(new String(text+userId));
						values.put(Tweets.COL_SIGNATURE, signature);
						
						// and the certificate
						values.put(Tweets.COL_CERTIFICATE, cm.getCertificate());
						
						// and set the is_verified flag to show that the tweet is signed
						values.put(Tweets.COL_ISVERIFIED, 1);
					} else {
						values.put(Tweets.COL_ISVERIFIED, 0);
					}
					// if we are in disaster mode, we give the content provider a 
					// second to insert the tweet and then schedule a scanning operation
					if(PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("prefDisasterMode", false) == true){
						if (ScanningService.getState() == ScanningService.STATE_IDLE){
			    				new ScanningAlarm(getContext(),0,true);
			    			
			    			} 
						
					}
				} else {
					
					String certificate = values.getAsString(Tweets.COL_CERTIFICATE);
					// check validity
					if(cm.checkCertificate(cm.parsePem(certificate), values.getAsLong(Tweets.COL_USER).toString())){
						
						// check signature
						String signature = values.getAsString(Tweets.COL_SIGNATURE);
						String text = values.getAsString(Tweets.COL_TEXT) + values.getAsString(Tweets.COL_USER);
						if(km.checkSignature(cm.parsePem(certificate), signature, text)){							
							values.put(Tweets.COL_ISVERIFIED, 1);
						} else {
							values.put(Tweets.COL_ISVERIFIED, 0);
						}
					
					} else {
						values.put(Tweets.COL_ISVERIFIED, 0);
					}
					
					if (ShowTweetListActivity.running==false && 
							PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("prefNotifyTweets", false) == true) {
						// notify user 
						notifyUser(NOTIFY_DISASTER, values.getAsString(Tweets.COL_TEXT));
					}
					
				}
				c.close();
				insertUri = insertTweet(values);
				//getContext().getContentResolver().notifyChange(uri, null);
				// delete everything that now falls out of the buffer
				purgeTweets(values);
				break;
				
			default: throw new IllegalArgumentException("Unsupported URI: " + uri);	
		}
		
		return insertUri;
	}

	private Uri insertTweetsTimelineNormal(ContentValues values) {
		
		int disasterId;
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
		disasterId = getDisasterID(values);
		
		Cursor c = database.query(DBOpenHelper.TABLE_TWEETS, null, Tweets.COL_DISASTERID+"="+disasterId, null, null, null, null);
		if(c.getCount() == 1){   

			c.moveToFirst();
			if(Long.toString(c.getLong(c.getColumnIndex(Tweets.COL_USER))).equals(LoginActivity.getTwitterId(getContext()))) {
				// clear the to insert flag
				int flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
				values.put(Tweets.COL_FLAGS, flags & (~Tweets.FLAG_TO_INSERT));
				
			} else if( !c.isNull(c.getColumnIndex(Tweets.COL_TID)) &&
					c.getLong(c.getColumnIndex(Tweets.COL_TID))==values.getAsLong(Tweets.COL_TID) ){
				
				return null;
			}
			
			Uri updateUri = Uri.parse("content://"+Tweets.TWEET_AUTHORITY+"/"+Tweets.TWEETS+"/"+Integer.toString(c.getInt(c.getColumnIndex("_id"))));
			update(updateUri, values, null, null);
			c.close();
			
			return updateUri;
			
		}
		c.close();
		// if none of the before was true, this is a proper new tweet which we now insert
		try {
			Uri insertUri = insertTweet(values);
			if (ShowTweetListActivity.running==false && ( (values.getAsInteger(Tweets.COL_BUFFER) & Tweets.BUFFER_SEARCH) == 0) &&
					PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("prefNotifyTweets", false) == true ) {
				// notify user 				
				notifyUser(NOTIFY_TWEET, values.getAsString(Tweets.COL_TEXT));
			}
			// delete everything that now falls out of the buffer
			purgeTweets(values);
			return insertUri;
			
		} catch (Exception ex) {
			Log.e(TAG,"exception while inserting", ex);
			return null;
		}
		
		
	}

	/**
	 * Update a tweet
	 */
	@Override
	public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		
		if(tweetUriMatcher.match(uri) != TWEETS_ID) throw new IllegalArgumentException("Unsupported URI: " + uri);		
		
		int nrRows = database.update(DBOpenHelper.TABLE_TWEETS, values, "_id="+uri.getLastPathSegment() , null);
		if(nrRows >= 0){			
			getContext().getContentResolver().notifyChange(uri, null);		
			
			// Trigger synch if needed
			if(values.containsKey(Tweets.COL_FLAGS) && values.getAsInteger(Tweets.COL_FLAGS)!=0){
				
				Intent i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_TWEET);
				i.putExtra("rowId", new Long(uri.getLastPathSegment()));
				getContext().startService(i);
			}

			return nrRows;
		} else {
			throw new IllegalStateException("Could not update tweet " + values);
		}
	}
	
	/**
	 * Delete a local tweet from the DB
	 */
	@Override
	public synchronized int delete(Uri uri, String arg1, String[] arg2) {
		if(tweetUriMatcher.match(uri) != TWEETS_ID) throw new IllegalArgumentException("Unsupported URI: " + uri);
		
		Log.d(TAG, "Delete TWEETS_ID");
		
		int nrRows = database.delete(DBOpenHelper.TABLE_TWEETS, "_id="+uri.getLastPathSegment(), null);
		getContext().getContentResolver().notifyChange(Tweets.CONTENT_URI, null);
		return nrRows;
	}
	
	/**
	 * purges a provided buffer to the provided number of tweets
	 */
	private void purgeBuffer(int buffer, int size){
		/*
		 * First, we remove the respective flag
		 * Second, we delete all tweets which have no more buffer flags
		 */		
		String sqlWhere;
		String sql;		
		// NOTE: DELETE in android does not allow ORDER BY. Hence, the trick with the _id
		sqlWhere = "("+Tweets.COL_BUFFER+"&"+buffer+")!=0";
		sql = "UPDATE " + DBOpenHelper.TABLE_TWEETS + " "
				+"SET " + Tweets.COL_BUFFER +"=("+(~buffer)+"&"+Tweets.COL_BUFFER+") "
				+"WHERE "
				+"_id IN (SELECT _id FROM "+DBOpenHelper.TABLE_TWEETS 
				+ " WHERE " + sqlWhere
				+ " ORDER BY "+Tweets.DEFAULT_SORT_ORDER+" "
				+ " LIMIT 100 OFFSET "
				+ size +");";		
		database.execSQL(sql);
		
		// now delete			
		int result = database.delete(DBOpenHelper.TABLE_TWEETS, Tweets.COL_BUFFER + "=0" , null);
		Log.i(TAG,"deleted " + result + " tweets");		

		getContext().getContentResolver().notifyChange(Tweets.CONTENT_URI, null);		
	}

	/**
	 * Keeps the tweets table at acceptable size
	 */
	private void purgeTweets(ContentValues cv){
		
		// in the content values we find which buffer(s) to purge
		int bufferFlags = cv.getAsInteger(Tweets.COL_BUFFER);
		
		if((bufferFlags & Tweets.BUFFER_TIMELINE) != 0){
			Log.d(TAG, "Purging timeline buffer "+ Constants.TIMELINE_BUFFER_SIZE);
			purgeBuffer(Tweets.BUFFER_TIMELINE, Constants.TIMELINE_BUFFER_SIZE);
		}
			
		if((bufferFlags & Tweets.BUFFER_FAVORITES) != 0){
			Log.d(TAG, "Purging favorites buffer");
			purgeBuffer(Tweets.BUFFER_FAVORITES, Constants.FAVORITES_BUFFER_SIZE);
		}

		if((bufferFlags & Tweets.BUFFER_MENTIONS) != 0){
			Log.d(TAG, "Purging mentions buffer");
			purgeBuffer(Tweets.BUFFER_MENTIONS, Constants.MENTIONS_BUFFER_SIZE);
		}

		if((bufferFlags & Tweets.BUFFER_DISASTER) != 0){
			Log.d(TAG, "Purging disaster buffer");
			purgeBuffer(Tweets.BUFFER_DISASTER, Constants.DTWEET_BUFFER_SIZE);
		}

		if((bufferFlags & Tweets.BUFFER_MYDISASTER) != 0){
			Log.d(TAG, "Purging mydisaster buffer");
			purgeBuffer(Tweets.BUFFER_MYDISASTER, Constants.MYDTWEET_BUFFER_SIZE);
		}
		
		if((bufferFlags & Tweets.BUFFER_USERS) != 0){
			Log.d(TAG, "Purging user tweets buffer");
			purgeBuffer(Tweets.BUFFER_USERS, Constants.USERTWEETS_BUFFER_SIZE);
		}
		
		if((bufferFlags & Tweets.BUFFER_SEARCH) != 0){
			Log.d(TAG, "Purging search tweets buffer");
			purgeBuffer(Tweets.BUFFER_SEARCH, Constants.SEARCHTWEETS_BUFFER_SIZE);
		}

	}

	
	/**
	 * Computes the java String object hash code (32 bit) as the disaster ID of the tweet
	 * TODO: For security reasons (to prevent intentional hash collisions), this should be a cryptographic hash function instead of the string hash.
	 * @param cv
	 * @return
	 */
	private int getDisasterID(ContentValues cv){
		if ( cv != null ) {
			String text = Html.fromHtml(cv.getAsString(Tweets.COL_TEXT), null, null).toString();
			
			String userId;
			if(!cv.containsKey(Tweets.COL_USER) || (cv.getAsString(Tweets.COL_USER)==null)){
				userId = LoginActivity.getTwitterId(getContext()).toString();
			} else {
				userId = cv.getAsString(Tweets.COL_USER);
			}
			
			return (new String(text+userId)).hashCode();
		} else 
			return -1;
		
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
	//	if(checkValues(values)){
			
			if(!values.containsKey(Tweets.COL_CREATED)){
				// set the current timestamp
				values.put(Tweets.COL_CREATED, System.currentTimeMillis());
			}
			
			values.put(Tweets.COL_RECEIVED, System.currentTimeMillis());			
			// the disaster ID must be set for all tweets (normal and disaster)
			values.put(Tweets.COL_DISASTERID, getDisasterID(values));			
			// does it mention the local user?
			String text = values.getAsString(Tweets.COL_TEXT);
			
			localScreenName = LoginActivity.getTwitterScreenname(getContext());
			String localUserScreenName = this.localScreenName;
			
			if (localUserScreenName != null) {
				// we convert to lower case to check if it's a mention
				if(text.toLowerCase().contains("@"+localUserScreenName.toLowerCase())){
					
					values.put(Tweets.COL_MENTIONS, 1);
					// put into mentions buffer
					if(values.containsKey(Tweets.COL_BUFFER)){
						values.put(Tweets.COL_BUFFER, values.getAsInteger(Tweets.COL_BUFFER)|Tweets.BUFFER_MENTIONS);
					} else {
						values.put(Tweets.COL_BUFFER, Tweets.BUFFER_MENTIONS);
					}
					
					if (ShowTweetListActivity.running==false && 
							PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("prefNotifyMentions", true) == true 
							&& hasBeenExecuted()  ) {
						// notify user						
						notifyUser(NOTIFY_MENTION, values.getAsString(Tweets.COL_TEXT));
					} 
									
				} else {
					values.put(Tweets.COL_MENTIONS, 0);				
				}
			}		
			try {
				
				long rowId = database.insertOrThrow(DBOpenHelper.TABLE_TWEETS, null, values);						
				if(rowId >= 0){							
					Uri insertUri = ContentUris.withAppendedId(Tweets.CONTENT_URI, rowId);
					return insertUri;
				} else {
					 return null; 
				}
				
			} catch (Exception ex) {
				Log.e(TAG,"could not insert tweet in the table",ex);
				return null;
			}
			
		//} else {
		//	throw new IllegalArgumentException("Illegal tweet: " + values);
	//	}
	}

	 
	  private boolean hasBeenExecuted() {
          return PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(TwitterService.TASK_MENTIONS, false);
  }
	

    
	/**
	 * Creates and triggers the status bar notifications
	 */
	private void notifyUser(int type, String tickerText){
		
		NotificationManager mNotificationManager = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
		int icon = R.drawable.ic_launcher_twimight;
		long when = System.currentTimeMillis();
		Notification notification = new Notification(icon, Html.fromHtml(tickerText, null, null), when);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		
		Context context = getContext().getApplicationContext();
		
		CharSequence contentTitle = "New Tweets!";
		CharSequence contentText = "New Tweets!";
		Intent notificationIntent = new Intent(getContext(), ShowTweetListActivity.class);
		PendingIntent contentIntent;
		switch(type){
		case(NOTIFY_MENTION):
			contentText = "You have new mention(s)";
			notificationIntent.putExtra("filter_request", ShowTweetListActivity.SHOW_MENTIONS);
			break;
		case(NOTIFY_DISASTER):
			contentText = "You have new disaster tweet(s)";
			notificationIntent.putExtra("filter_request", ShowTweetListActivity.SHOW_TIMELINE);
			break;
		case(NOTIFY_TWEET):
			contentText = "New tweet(s) in your timeline";
			notificationIntent.putExtra("filter_request", ShowTweetListActivity.SHOW_TIMELINE);
			break;
		default:
			break;
		}
		contentIntent = PendingIntent.getActivity(getContext(), 0, notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);

		mNotificationManager.notify(TWEET_NOTIFICATION_ID, notification);

	}

}
