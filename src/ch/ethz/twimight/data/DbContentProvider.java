package ch.ethz.twimight.data;

import ch.ethz.twimight.util.Constants;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

/**
 * The Content Provider that shares disaster tweets with other applications (plugins).
 * @author pcarta
 *
 */
public class DbContentProvider extends ContentProvider {
	DbOpenHelper dbHelper;
	SQLiteDatabase db;

	private static final String TAG = "DbContentProvider";
	//authority and paths
	public static final String AUTHORITY="ch.ethz.twimight.DbContentProvider";
	//public static final String DISASTER_PATH="DisasterTable";

	//URiMatcher to match client URis
	public static final int ALLTWEETS=1;
	public static final int SINGLETWEET=2;

	public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY+ "/DisasterTable");

	static final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
	static {
		matcher.addURI(AUTHORITY,"DisasterTable",ALLTWEETS);
		matcher.addURI(AUTHORITY, "DisasterTable/#", SINGLETWEET);

	}

	/**
	 * Delete from the Disaster DB.
	 */
	@Override
	public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
		int count = 0;
		int match = matcher.match(uri);
		ContentValues values = new ContentValues();
		values.put(DbOpenHelper.C_IS_VALID, false);
		switch (match) {
		case ALLTWEETS:				
			count = db.update(DbOpenHelper.TABLE_DISASTER, values, selection, selectionArgs);
			break;
		case SINGLETWEET:
			String id = uri.getPathSegments().get(1);
			count = db.update(DbOpenHelper.TABLE_DISASTER,values, 
					DbOpenHelper.C_ID + "=" + id, selectionArgs);
		default:
			throw new IllegalArgumentException("Unknown URI " + uri );	

		}
		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * insert into Disaster DB.
	 */
	@Override
	public synchronized Uri insert(Uri uri, ContentValues values) {		
		int match = matcher.match(uri);
		long newID=0;

		if(match!=1)
			throw new IllegalArgumentException("Wrong URi "+uri.toString());
		if(values!=null)
		{
			try { //NEED TO SAVE USER AND PUBLIC KEY 

				ContentValues valuesFriends = new ContentValues();
				valuesFriends.put(DbOpenHelper.C_USER, values.getAsString("user") );
				values.remove("user");
				valuesFriends.put(DbOpenHelper.C_ID, values.getAsLong("userCode") );
				valuesFriends.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Constants.TRUE);
				db.insertOrThrow(DbOpenHelper.TABLE_FRIENDS, null, valuesFriends);					

				Log.i(TAG,"external app added added");
				newID=db.insertOrThrow(DbOpenHelper.TABLE_DISASTER, null, values);
				Log.i(TAG,"values added");
				return Uri.withAppendedPath(uri, String.valueOf(newID));
			} catch (Exception ex) {
				Log.e(TAG,"error",ex);
				return null;}
		}  
		else
			return null;

	}

	/**
	 * onCreate: setup the content provider
	 */
	@Override
	public boolean onCreate() {
		// TODO Auto-generated method stub
		dbHelper = new DbOpenHelper(this.getContext());
		db = dbHelper.getWritableDatabase(); 
		if (db != null)
			return true;
		else
			return false;
	}

	/**
	 * Query Disaster DB
	 */
	@Override
	public synchronized Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder sqlBuilder = new SQLiteQueryBuilder();
		sqlBuilder.setTables(DbOpenHelper.TABLE_DISASTER);	    

		Cursor result=null;	    
		int match=matcher.match(uri);
		switch(match)
		{
		case ALLTWEETS:	    		
			result= sqlBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
			break;   
		case SINGLETWEET:
			sqlBuilder.appendWhere(DbOpenHelper.C_ID + " = " + uri.getPathSegments().get(1));  
			result= sqlBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);	    		
			break;

		}	   
		return result;

	}

	/**
	 * Update Disaster DB
	 */
	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		int match=matcher.match(uri);

		int rows=0;
		switch (match) {
		case SINGLETWEET:
			rows = db.update(DbOpenHelper.TABLE_DISASTER, values, 
					DbOpenHelper.C_ID + "=" + uri.getPathSegments().get(1) , selectionArgs);
			break;
		case ALLTWEETS:
			rows = db.update(DbOpenHelper.TABLE_DISASTER, values, selection , selectionArgs);
			break;
		default: throw new IllegalArgumentException("Unknown URI " + uri);    
		}
		getContext().getContentResolver().notifyChange(uri, null);
		return rows;
	}


}
