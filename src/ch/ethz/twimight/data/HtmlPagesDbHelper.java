package ch.ethz.twimight.data;

import java.math.BigInteger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.twitter.Tweets;

public class HtmlPagesDbHelper {
	
	private Context context;
	private static final String TAG = "HtmlPagesDbHelper";
	
	private SQLiteDatabase database;
	private DBOpenHelper dbHelper;
	
	
	/**
	 * Constructor.
	 * @param context
	 */
	public HtmlPagesDbHelper(Context context) {
		this.context = context;
	}

	/**
	 * Opens the DB.
	 * @return
	 * @throws SQLException
	 */
	public HtmlPagesDbHelper open() throws SQLException {
		dbHelper = DBOpenHelper.getInstance(context);
		database = dbHelper.getWritableDatabase();
		return this;
	}
	
	/**
	 * insert an entry to the database for a new link we receive
	 * @param url
	 * @param filename
	 * @param tweetId
	 * @param downloaded
	 * @return
	 */
	public boolean insertPage(String url, String filename, String tweetId, int downloaded) {
		Log.i(TAG,"url: " + url);
		ContentValues cv = createContentValues(url,filename, tweetId, downloaded);
		
		try {
			long result = database.insertOrThrow(DBOpenHelper.TABLE_HTML, null, cv);
			Log.i(TAG,"row: " + result);
			if(result!=-1)
				return true;
		} catch (SQLException ex) {
			Log.e(TAG,"error inserting html page",ex);
		}
		return false;
	}
	
	/**
	 * update an entry
	 * @param url
	 * @param filename
	 * @param tweetId
	 * @param downloaded
	 * @return
	 */
	public boolean updatePage(String url, String filename, String tweetId, int downloaded){
		
		ContentValues cv = createContentValues(url,filename, tweetId, downloaded);
		Log.d(TAG, "update an entry:" + cv.toString());
		String sql = HtmlPage.COL_URL + " = '" + url +"' and " + HtmlPage.COL_TID + " = '" + tweetId + "'";
		int row = database.update(DBOpenHelper.TABLE_HTML, cv, sql, null);
		Log.d(TAG, "row:" + String.valueOf(row));
		if(row!=0) return true;
		Log.d(TAG, "update html database failed");
		return false;
	}
	
	/**
	 * delete an entry with url and tweet_id
	 * @param url
	 * @param tweetId
	 * @return
	 */
	public boolean deletePage(String url, String tweetId) {
		Log.i(TAG,"url: " + url);
		
		try {
			String sql = HtmlPage.COL_URL + " = '" + url +"' and " + HtmlPage.COL_TID + " = '" + tweetId + "'";
			int result = database.delete(DBOpenHelper.TABLE_HTML, sql, null);
			Log.i(TAG,"row: " + result);
			if(result!=0)
				return true;
		} catch (SQLException ex) {
			Log.e(TAG,"error deleting html page",ex);
		}
		return false;
	}
	
	/**
	 * get the filename of xml file given url and tweet_id, if not found, return null
	 * @param url
	 * @param tweetId
	 * @return
	 */
	public ContentValues getPageInfo(String url, String tweetId) {
		
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, HtmlPage.COL_URL + " = '" + url +"'" , null, null, null, null);
		
		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext())
		{
				if(tweetId.equals(c.getString(c.getColumnIndex(HtmlPage.COL_TID)))){
					ContentValues htmlCV = createContentValues(c.getString(c.getColumnIndex(HtmlPage.COL_URL)),
							c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)),
							c.getString(c.getColumnIndex(HtmlPage.COL_TID)),
							c.getInt(c.getColumnIndex(HtmlPage.COL_DOWNLOADED)));
					Log.d(TAG, "page info:" + htmlCV.toString());
					return htmlCV;
				}
		}
		
		return null;
	}
	
	/**
	 * get the tweet we received after timestamp
	 * @param timestamp
	 * @return
	 */
	public Cursor getNewTweet(Long timestamp){
		
		String[] cols = {Tweets.COL_TEXT, Tweets.COL_TID, Tweets.COL_USER};
		String sql = Tweets.COL_RECEIVED + "> '" + String.valueOf(timestamp) +"' and " + Tweets.COL_HTMLS + " = '" + String.valueOf(1) + "'";
		Cursor c = database.query(DBOpenHelper.TABLE_TWEETS, cols, sql, null, null, null, null);
		return c;
	}
	
	/**
	 * get undownloaded pages
	 * @return
	 */
	public Cursor getUndownloadedHtmls(){
		Log.d(TAG, "get undownloaded htmls");
		String sql = HtmlPage.COL_DOWNLOADED + "= '" + String.valueOf(0) + "'";
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, sql, null, null, null, null);
		return c;
	}
	
	/**
	 * return user id for a specific tweet
	 * @param tweetId
	 * @return
	 */
	public String getUserId(String tweetId){
		Log.d(TAG, "get user id for tweet:" + tweetId);
		String sql = Tweets.COL_TID + " = '" + tweetId + "'";
		Cursor c = database.query(DBOpenHelper.TABLE_TWEETS, null, sql, null, null, null, null);
		c.moveToFirst();
		return String.valueOf(c.getLong(c.getColumnIndex(Tweets.COL_USER)));
	}
	
	
	/**
	 * Creates a Html page record to insert in the DB
	 * @param url
	 * @param filename
	 * @param tweetId
	 * @param downloaded
	 * @return
	 */
	private ContentValues createContentValues(String url, String filename, String tweetId, int downloaded) {
		ContentValues values = new ContentValues();
		values.put(HtmlPage.COL_FILENAME ,filename);
		values.put(HtmlPage.COL_URL ,url);
		values.put(HtmlPage.COL_TID, tweetId);
		values.put(HtmlPage.COL_DOWNLOADED, downloaded);
		return values;
	}
	
	/**
	 * return all rows of html table
	 * @return
	 */
	public Cursor getAll(){
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, null, null, null, null, null, null);
		return c;
	}	

}
