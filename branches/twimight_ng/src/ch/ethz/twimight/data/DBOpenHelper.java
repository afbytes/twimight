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
package ch.ethz.twimight.data;

import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Manages the database: creation/deletion of tables, opening the connection, etc.
 * @author theus
 *
 */
public class DBOpenHelper extends SQLiteOpenHelper {
	private static final String DATABASE_NAME = "twimight";

	// Database table names;
	static final String TABLE_REVOCATIONS = "revocations";
	static final String TABLE_MACS = "macs";
	static final String TABLE_LOCATIONS = "locations";
	static final String TABLE_FRIENDS_KEYS = "friends_keys";
	public static final String TABLE_TWEETS = "tweets";
	public static final String TABLE_USERS = "users";

	private static final int DATABASE_VERSION = 25;

	// Database creation sql statement
	private static final String TABLE_MACS_CREATE = "create table "+TABLE_MACS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ "mac bigint not null, "
			+ "attempts integer, "
			+ "successful integer, "
			+ "active integer);";
	
	private static final String TABLE_LOCATIONS_CREATE = "create table "+TABLE_LOCATIONS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ "lat real not null, "
			+ "lng real not null, "
			+ "accuracy integer, "
			+ "loc_date integer, "
			+ "provider string);";

	private static final String TABLE_REVOCATION_CREATE = "create table "+TABLE_REVOCATIONS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ "serial string not null, "
			+ "until integer not null);";

	private static final String TABLE_FRIENDS_KEYS_CREATE = "create table "+TABLE_FRIENDS_KEYS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ "twitter_id bigint not null, "
			+ "key text not null);";
	
	// Extend this with the rest of a tweet's metadata
	private static final String TABLE_TWEETS_CREATE = "create table "+TABLE_TWEETS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ Tweets.TWEETS_COLUMNS_TEXT + " string not null, "
			+ Tweets.TWEETS_COLUMNS_USER + " bigint not null, "
			+ Tweets.TWEETS_COLUMNS_TID + " bigint unique, "
			+ Tweets.TWEETS_COLUMNS_REPLYTO + " bigint, "
			+ Tweets.TWEETS_COLUMNS_FAVORITED + " int, "
			+ Tweets.TWEETS_COLUMNS_RETWEETED + " int, "
			+ Tweets.TWEETS_COLUMNS_RETWEETCOUNT + " int, "
			+ Tweets.TWEETS_COLUMNS_MENTIONS + " int, "
			+ Tweets.TWEETS_COLUMNS_LAT + " real, "
			+ Tweets.TWEETS_COLUMNS_LNG + " real, "
			+ Tweets.TWEETS_COLUMNS_CREATED + " integer, "
			+ Tweets.TWEETS_COLUMNS_SOURCE + " string, "
			+ Tweets.TWEETS_COLUMNS_FLAGS + " integer not null default 0);";
	
	// Twitter Users
	private static final String TABLE_USERS_CREATE = "create table "+TABLE_USERS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_SCREENNAME + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_ID + " bigint unique not null, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_NAME + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_LANG + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_DESCRIPTION + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_IMAGEURL + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_STATUSES + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOWERS + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_FRIENDS + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_LISTED + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_FAVORITES + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_LOCATION + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_UTCOFFSET + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_TIMEZONE + " string, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_URL + " string, "
			+ TwitterUsers.TWITTERUSERS_CREATED + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_PROTECTED + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_VERIFIED + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOWING + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOW + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_FOLLOWREQUEST + " integer, "
			+ TwitterUsers.TWITTERUSERS_COLUMNS_PROFILEIMAGE + " blob,"
			+ TwitterUsers.TWITTERUSERS_COLUMNS_LASTUPDATE + " integer,"
			+ TwitterUsers.TWITTERUSERS_COLUMNS_FLAGS + " integer not null default 0);";
	
	// TODO: MyDisasterTweets
	
	// TODO: DisasterTweets
	
	// TODO: DeletedDisasterTweets
	
	// TODO: DisasterMessages
	
	// TODO: MyDisasterMessages
	
	// TODO: DeletedDisasterMessages
	
	// TODO: SearchResults

	// TODO: Images
	
	private static DBOpenHelper dbHelper; /** the one and only instance of this class */

	/**
	 * Constructor
	 * @param context
	 */
	public DBOpenHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	/**
	 * We want only one instance of a DBHelper to avoid problems.
	 * @param context
	 * @return the 
	 */
	public static DBOpenHelper getInstance(Context context){
		if(dbHelper == null)
			dbHelper = new DBOpenHelper(context);
		
		return dbHelper;
		
	}
	
	/**
	 * Called when creating the DB
	 */
	@Override
	public void onCreate(SQLiteDatabase database) {
		database.execSQL(TABLE_MACS_CREATE);
		database.execSQL(TABLE_LOCATIONS_CREATE);
		database.execSQL(TABLE_REVOCATION_CREATE);
		database.execSQL(TABLE_FRIENDS_KEYS_CREATE);
		database.execSQL(TABLE_TWEETS_CREATE);
		database.execSQL(TABLE_USERS_CREATE);
	}

	/**
	 * Called when upgrading the DB (new DATABASE_VERSION)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase database, int oldVersion,
			int newVersion) {
		Log.w(DBOpenHelper.class.getName(),
				"Upgrading database from version " + oldVersion + " to "
						+ newVersion + ", which will destroy all old data");
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_MACS);
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_LOCATIONS);
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_REVOCATIONS);
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_FRIENDS_KEYS);
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_TWEETS);
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_USERS);
		onCreate(database);
	}
	
	/**
	 * Drops all tables;
	 */
	public void flushDB(){
		
		SQLiteDatabase database = this.getWritableDatabase();
		database.execSQL("DELETE FROM "+TABLE_MACS);
		database.execSQL("DELETE FROM "+TABLE_LOCATIONS);
		database.execSQL("DELETE FROM "+TABLE_REVOCATIONS);
		database.execSQL("DELETE FROM "+TABLE_FRIENDS_KEYS);
		database.execSQL("DELETE FROM "+TABLE_TWEETS);
		database.execSQL("DELETE FROM "+TABLE_USERS);
	}
}
