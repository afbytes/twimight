package ch.ethz.twimight.data;

import ch.ethz.twimight.activities.FeedbackActivity;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class BugsDBHelper {
	
	Context context;
	private SQLiteDatabase database;
	private DBOpenHelper dbHelper;
	
	public BugsDBHelper(Context context) {
		this.context = context;
	}
	
	/**
	 * Opens the DB.
	 * @return
	 * @throws SQLException
	 */
	public BugsDBHelper open() throws SQLException {
		dbHelper = DBOpenHelper.getInstance(context);
		database = dbHelper.getWritableDatabase();
		return this;
	}
	
	/**
	 * Inserts a location update into the DB
	 * @param loc
	 * @return
	 */
	public boolean insertRow(long twitterId, String text, int type) {
		
		ContentValues values = createContentValues(twitterId, text, type);
		try { 
		   database.insertOrThrow(DBOpenHelper.TABLE_BUGS, null, values);
		   return true;
		} catch (Exception ex) {
			return false;
		}
		
	}
	
	public int deleteOldData() {
		
		return database.delete(DBOpenHelper.TABLE_BUGS, null, null);
	}
	
	public Cursor getData() {
		
		Cursor cursor = database.query(DBOpenHelper.TABLE_BUGS, null, null, null, null,null,null);
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();			
		}
			
		else 
			return null;
		
		
		return cursor;
	}
	
	/**
	 * Creates a Location record to insert in the DB
	 * @return
	 */
	private ContentValues createContentValues(long twitterId, String text, int type) {
		
		ContentValues values = new ContentValues();		
	    values.put(FeedbackActivity.COL_TWITTER_ID, twitterId);
	    values.put(FeedbackActivity.COL_TEXT, text);
	    values.put(FeedbackActivity.COL_TYPE, type);
		
		return values;
	}

}
