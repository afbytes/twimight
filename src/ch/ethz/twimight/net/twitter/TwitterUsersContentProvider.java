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

import ch.ethz.twimight.data.DBOpenHelper;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

/**
 * The content provider for Twitter users
 * @author thossmann
 *
 */
public class TwitterUsersContentProvider extends ContentProvider {

	private static final String TAG = "TwitterUsersContentProvider";
	
	private SQLiteDatabase database;
	private DBOpenHelper dbHelper;
	
	private static final UriMatcher twitterusersUriMatcher;
	
	private static final int USERS = 1;
	private static final int USERS_ID = 2;
	
	private static final int USERS_FRIENDS = 4;
	private static final int USERS_FOLLOWERS = 5;
	private static final int USERS_DISASTER = 6;
	
	
	// Here we define all the URIs this provider knows
	static{
		twitterusersUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		twitterusersUriMatcher.addURI(TwitterUsers.TWITTERUSERS_AUTHORITY, TwitterUsers.TWITTERUSERS, USERS);
		
		twitterusersUriMatcher.addURI(TwitterUsers.TWITTERUSERS_AUTHORITY, TwitterUsers.TWITTERUSERS + "/#", USERS_ID);

		twitterusersUriMatcher.addURI(TwitterUsers.TWITTERUSERS_AUTHORITY, TwitterUsers.TWITTERUSERS + "/" + TwitterUsers.TWITTERUSERS_FRIENDS, USERS_FRIENDS);
		twitterusersUriMatcher.addURI(TwitterUsers.TWITTERUSERS_AUTHORITY, TwitterUsers.TWITTERUSERS + "/" + TwitterUsers.TWITTERUSERS_FOLLOWERS, USERS_FOLLOWERS);
		twitterusersUriMatcher.addURI(TwitterUsers.TWITTERUSERS_AUTHORITY, TwitterUsers.TWITTERUSERS + "/" + TwitterUsers.TWITTERUSERS_DISASTER, USERS_DISASTER);

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
	 * Returns the MIME types (defined in TwitterUsers) of a URI
	 */
	@Override
	public String getType(Uri uri) {
		switch(twitterusersUriMatcher.match(uri)){
			case USERS: return TwitterUsers.TWITTERUSERS_CONTENT_TYPE;
			case USERS_ID: return TwitterUsers.TWITTERUSER_CONTENT_TYPE;
			case USERS_FRIENDS: return TwitterUsers.TWITTERUSERS_CONTENT_TYPE;
			case USERS_FOLLOWERS: return TwitterUsers.TWITTERUSERS_CONTENT_TYPE;
			default: throw new IllegalArgumentException("Unknown URI: " + uri);	
		}
	}

	@Override
	public synchronized Cursor query(Uri uri, String[] projection, String where, String[] whereArgs, String sortOrder) {
				
		Intent i;
		
		Cursor c = null;
		switch(twitterusersUriMatcher.match(uri)){
			case USERS: 
				Log.d(TAG, "Query USERS");
				c = database.query(DBOpenHelper.TABLE_USERS, projection, where, whereArgs, null, null, sortOrder);
				c.setNotificationUri(getContext().getContentResolver(), TwitterUsers.CONTENT_URI);
				break;

			
			case USERS_ID: 
				Log.d(TAG, "Query USERS_ID " + uri.getLastPathSegment());
				c = database.query(DBOpenHelper.TABLE_USERS, projection, "_id="+uri.getLastPathSegment(), whereArgs, null, null, sortOrder);
				//c.setNotificationUri(getContext().getContentResolver(),uri);
				c.setNotificationUri(getContext().getContentResolver(), TwitterUsers.CONTENT_URI);
				break;
			
			case USERS_FOLLOWERS:
				Log.d(TAG, "Query USERS_FOLLOWERS");
				c = database.query(DBOpenHelper.TABLE_USERS, projection, TwitterUsers.COL_ISFOLLOWER+">0 AND "+TwitterUsers.COL_SCREENNAME+" IS NOT NULL", whereArgs, null, null, sortOrder);
				c.setNotificationUri(getContext().getContentResolver(), uri);
				c.setNotificationUri(getContext().getContentResolver(), TwitterUsers.CONTENT_URI);
				
				// start synch service with a synch followers request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FOLLOWERS);
				getContext().startService(i);
			
				break;
			case USERS_FRIENDS:
				Log.d(TAG, "Query USERS_FRIENDS");
				c = database.query(DBOpenHelper.TABLE_USERS, projection, TwitterUsers.COL_ISFRIEND+">0 AND "+TwitterUsers.COL_SCREENNAME+" IS NOT NULL", whereArgs, null, null, sortOrder);
				c.setNotificationUri(getContext().getContentResolver(),TwitterUsers.CONTENT_URI);
				c.setNotificationUri(getContext().getContentResolver(),uri);
				// start synch service with a synch friends request
				i = new Intent(TwitterService.SYNCH_ACTION);
				i.putExtra("synch_request", TwitterService.SYNCH_FRIENDS);
				getContext().startService(i);
				
				break;
			case USERS_DISASTER:
				Log.d(TAG, "Query USERS_DISASTER");
				c = database.query(DBOpenHelper.TABLE_USERS, projection, TwitterUsers.COL_ISDISASTER_PEER+">0 AND "+TwitterUsers.COL_SCREENNAME+" IS NOT NULL", whereArgs, null, null, sortOrder);
				c.setNotificationUri(getContext().getContentResolver(),TwitterUsers.CONTENT_URI);
				break;
			default: throw new IllegalArgumentException("Unsupported URI: " + uri);	
		}
		
		return c;
	}

	@Override
	public synchronized Uri insert(Uri uri, ContentValues values) {
		Log.d(TAG, "Insert USER");

		if(twitterusersUriMatcher.match(uri) != USERS) throw new IllegalArgumentException("Unsupported URI: " + uri);
		
		if(checkValues(values)){
			// if we already have the user, we update with the new info
			String[] projection = {"_id", TwitterUsers.COL_PROFILEIMAGE};
			Cursor c = database.query(DBOpenHelper.TABLE_USERS, projection, TwitterUsers.COL_SCREENNAME+" LIKE '"+values.getAsString(TwitterUsers.COL_SCREENNAME)+"' OR "+ TwitterUsers.COL_ID+"="+values.getAsString(TwitterUsers.COL_ID), null, null, null, null);
			if(c.getCount()==1){
				Log.i(TAG, "already have the user, updating...");
				c.moveToFirst();
				
				// we flag the user for updating the profile image if
				// - the flag is set
				// - and we do not yet have a profile image
				// - otherwise, we clear the profile image flag
				if(values.containsKey(TwitterUsers.COL_FLAGS) && ((values.getAsInteger(TwitterUsers.COL_FLAGS) & TwitterUsers.FLAG_TO_UPDATEIMAGE) >0)){
					if(!c.isNull(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE))){
						values.put(TwitterUsers.COL_FLAGS, values.getAsInteger(TwitterUsers.COL_FLAGS) & (~TwitterUsers.FLAG_TO_UPDATEIMAGE));
					} 
				}
				Uri updateUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+Integer.toString(c.getInt(c.getColumnIndex("_id"))));
				update(updateUri, values, null, null);
				c.close();
				return updateUri;
				
			} else {
				
				c.close();
				
				long rowId = database.insert(DBOpenHelper.TABLE_USERS, null, values);
				if(rowId >= 0){
					
					Uri insertUri = Uri.parse("content://"+TwitterUsers.TWITTERUSERS_AUTHORITY+"/"+TwitterUsers.TWITTERUSERS+"/"+rowId);				
					purge(DBOpenHelper.TABLE_USERS);
						
					return insertUri;
				} else {
					throw new IllegalStateException("Could not insert user into database " + values);
				}
				
			}		
			
			
		} else {
			throw new IllegalArgumentException("Illegal user: " + values);
		}
	}

	@Override
	public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		Log.d(TAG, "Update USERS");
		if(twitterusersUriMatcher.match(uri) != USERS_ID) throw new IllegalArgumentException("Unsupported URI: " + uri);
		
		if(checkValues(values)){
			int nrRows = database.update(DBOpenHelper.TABLE_USERS, values, "_id=" + uri.getLastPathSegment() , null);
			if(nrRows > 0){
				Log.i(TAG,"user updated");
				return nrRows;
			} else {
				throw new IllegalStateException("Could not insert user into database " + values);
			}
		} else {
			throw new IllegalArgumentException("Illegal user: " + values);
		}
	}
	
	@Override
	public synchronized int delete(Uri uri, String arg1, String[] arg2) {
		return 0;
	}
	
	private void purge(String table){
		
	}
	
	private boolean checkValues(ContentValues values){
		return true;
	}


}
