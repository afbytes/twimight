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
	static final String TABLE_REVOCATIONS = "revocations"; /** the table holding the local version of the revocation list */
	static final String TABLE_MACS = "macs"; /** table holding the bluetooth MAC addresses we know */
	static final String TABLE_LOCATIONS = "locations";
	static final String TABLE_FRIENDS_KEYS = "friends_keys";
	public static final String TABLE_TWEETS = "tweets";
	public static final String TABLE_USERS = "users";
	public static final String TABLE_DTWEETS = "deleted_dtweets"; /** the list of deleted disaster tweets */

	private static final int DATABASE_VERSION = 30;

	// Database creation sql statement
	private static final String TABLE_MACS_CREATE = "create table "+TABLE_MACS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ "mac bigint not null, "
			+ "attempts integer, "
			+ "successful integer, "
			+ "active integer, "
			+ "last_update integer);";
	
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
	
	// Tweets (including disaster tweets)
	private static final String TABLE_TWEETS_CREATE = "create table "+TABLE_TWEETS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ Tweets.COL_TEXT + " string not null, "
			+ Tweets.COL_USER + " bigint not null, "
			+ Tweets.COL_TID + " bigint unique, "
			+ Tweets.COL_REPLYTO + " bigint, "
			+ Tweets.COL_FAVORITED + " int, "
			+ Tweets.COL_RETWEETED + " int, "
			+ Tweets.COL_RETWEETCOUNT + " int, "
			+ Tweets.COL_MENTIONS + " int, "
			+ Tweets.COL_LAT + " real, "
			+ Tweets.COL_LNG + " real, "
			+ Tweets.COL_CREATED + " integer, "
			+ Tweets.COL_RECEIVED + " integer, "
			+ Tweets.COL_SOURCE + " string, "
			+ Tweets.COL_FLAGS + " integer not null default 0, "
			+ Tweets.COL_ISDISASTER + " integer not null default 0, "
			+ Tweets.COL_DISASTERID + " integer not null, "
			+ Tweets.COL_ISVERIFIED + " integer, "
			+ Tweets.COL_SIGNATURE + " string, "
			+ Tweets.COL_CERTIFICATE + " string);";
	
	// Twitter Users
	private static final String TABLE_USERS_CREATE = "create table "+TABLE_USERS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ TwitterUsers.COL_SCREENNAME + " string, "
			+ TwitterUsers.COL_ID + " bigint unique not null, "
			+ TwitterUsers.COL_NAME + " string, "
			+ TwitterUsers.COL_LANG + " string, "
			+ TwitterUsers.COL_DESCRIPTION + " string, "
			+ TwitterUsers.COL_IMAGEURL + " string, "
			+ TwitterUsers.COL_STATUSES + " integer, "
			+ TwitterUsers.COL_FOLLOWERS + " integer, "
			+ TwitterUsers.COL_FRIENDS + " integer, "
			+ TwitterUsers.COL_LISTED + " integer, "
			+ TwitterUsers.COL_FAVORITES + " integer, "
			+ TwitterUsers.COL_LOCATION + " string, "
			+ TwitterUsers.COL_UTCOFFSET + " string, "
			+ TwitterUsers.COL_TIMEZONE + " string, "
			+ TwitterUsers.COL_URL + " string, "
			+ TwitterUsers.COL_CREATED + " integer, "
			+ TwitterUsers.COL_PROTECTED + " integer, "
			+ TwitterUsers.COL_VERIFIED + " integer, "
			+ TwitterUsers.COL_FOLLOWING + " integer, "
			+ TwitterUsers.COL_FOLLOW + " integer, "
			+ TwitterUsers.COL_FOLLOWREQUEST + " integer, "
			+ TwitterUsers.COL_PROFILEIMAGE + " blob,"
			+ TwitterUsers.COL_LASTUPDATE + " integer,"
			+ TwitterUsers.COL_FLAGS + " integer not null default 0);";

	private static final String TABLE_DTWEETS_CREATE = "create table "+TABLE_DTWEETS+" ("
			+ "_id integer primary key autoincrement not null, "
			+ "d_id bigint unique not null, "
			+ "timestamp bigint not null);";
	
	// TODO: DisasterMessages
	
	// TODO: MyDisasterMessages
	
	// TODO: DeletedDisasterMessages
	
	// TODO: SearchResults

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
		database.execSQL(TABLE_DTWEETS_CREATE);
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
		database.execSQL("DROP TABLE IF EXISTS "+TABLE_DTWEETS);
		onCreate(database);
	}
	
	/**
	 * Empties all tables;
	 */
	public void flushDB(){
		
		SQLiteDatabase database = this.getWritableDatabase();
		database.execSQL("DELETE FROM "+TABLE_MACS);
		database.execSQL("DELETE FROM "+TABLE_LOCATIONS);
		database.execSQL("DELETE FROM "+TABLE_REVOCATIONS);
		database.execSQL("DELETE FROM "+TABLE_FRIENDS_KEYS);
		database.execSQL("DELETE FROM "+TABLE_TWEETS);
		database.execSQL("DELETE FROM "+TABLE_USERS);
		database.execSQL("DELETE FROM "+TABLE_DTWEETS);
	}
}
