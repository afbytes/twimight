package ch.ethz.twimight.data;

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
	public boolean insertPage(String url, String filename, String tweetId, String userId, int downloaded, int forced) {
		Log.i(TAG,"insert page url: " + url);
		ContentValues cv = createContentValues(url,filename, tweetId, userId, downloaded, forced, 0);
		
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
	public boolean updatePage(String url, String filename, String tweetId, String userId, int downloaded, int forced, int tries){
		
		ContentValues cv = createContentValues(url,filename, tweetId, userId, downloaded, forced, tries);
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
			Log.i(TAG,"delete row: " + result);
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
	public ContentValues getPageInfo(String url, String tweetId, String userId) {
		
		if(tweetId.equals("0")){
			Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, HtmlPage.COL_URL + " = '" + url +"' and " + HtmlPage.COL_USER + " = '" + userId + "'", null, null, null, null);
			
			c.moveToFirst();
			
			if(c.getCount() == 0)return null;		
			
			ContentValues htmlCV = createContentValues(c.getString(c.getColumnIndex(HtmlPage.COL_URL)),
					c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)),
					c.getString(c.getColumnIndex(HtmlPage.COL_TID)),
					c.getString(c.getColumnIndex(HtmlPage.COL_USER)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_DOWNLOADED)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_FORCED)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_TRIES)));
			Log.d(TAG, "page info:" + htmlCV.toString());
			
			return htmlCV;
		}else{
			Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, HtmlPage.COL_URL + " = '" + url +"' and " + HtmlPage.COL_TID + " = '" + tweetId + "'" , null, null, null, null);
			
			c.moveToFirst();
			
			if(c.getCount() == 0)return null;		
			
			ContentValues htmlCV = createContentValues(c.getString(c.getColumnIndex(HtmlPage.COL_URL)),
					c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)),
					c.getString(c.getColumnIndex(HtmlPage.COL_TID)),
					c.getString(c.getColumnIndex(HtmlPage.COL_USER)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_DOWNLOADED)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_FORCED)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_TRIES)));
			Log.d(TAG, "page info:" + htmlCV.toString());
			
			return htmlCV;
		}
		
	}
	
	//return all urls for a tweet
	public Cursor getUrlsByTweetId(String tweetId){
		
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, HtmlPage.COL_TID + " = '" + tweetId + "'" , null, null, null, null);
		return c;
	}
	
	/**
	 * get the tweet we received after timestamp
	 * @param timestamp
	 * @return
	 */
	public Cursor getNewTweet(Long timestamp){
		
		String[] cols = {Tweets.COL_TEXT, Tweets.COL_TID, Tweets.COL_USER};
		String sql = Tweets.COL_RECEIVED + "> '" + String.valueOf(timestamp) +"' and " + Tweets.COL_HTML_PAGES + " = '" + String.valueOf(1) + "'";
		Cursor c = database.query(DBOpenHelper.TABLE_TWEETS, cols, sql, null, null, null, null);
		return c;
	}
	
	/**
	 * get undownloaded pages
	 * @return
	 */
	public Cursor getUndownloadedHtmls(boolean forced){
		Log.d(TAG, "get undownloaded htmls");
		String sql = null;
		if(forced){
			sql = HtmlPage.COL_DOWNLOADED + "= '" + String.valueOf(0) + "' and " + HtmlPage.COL_FORCED + " = '" + String.valueOf(1) + "' and " + HtmlPage.COL_TRIES + " < '" + String.valueOf(HtmlPage.DOWNLOAD_LIMIT) + "'";
		}else{
			sql = HtmlPage.COL_DOWNLOADED + "= '" + String.valueOf(0) + "' and " + HtmlPage.COL_TRIES + " < '" + String.valueOf(HtmlPage.DOWNLOAD_LIMIT) + "'";
		}
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, sql, null, null, null, HtmlPage.COL_FILENAME + " DESC");
		return c;
	}
	
	
	/**
	 * get downloaded pages for cleaning mess
	 * @return
	 */
	public Cursor getDownloadedHtmls(){
		Log.d(TAG, "get downloaded htmls");
		String sql = HtmlPage.COL_DOWNLOADED + "= '" + String.valueOf(1) + "'";
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, sql, null, null, null, null);
		return c;
	}
	
	/**
	 * Creates a Html page record to insert in the DB
	 * @param url
	 * @param filename
	 * @param tweetId
	 * @param downloaded
	 * @return
	 */
	private ContentValues createContentValues(String url, String filename, String tweetId, String userId, int downloaded, int forced, int tries) {
		ContentValues values = new ContentValues();
		values.put(HtmlPage.COL_FILENAME ,filename);
		values.put(HtmlPage.COL_URL ,url);
		values.put(HtmlPage.COL_TID, tweetId);
		values.put(HtmlPage.COL_USER, userId);
		values.put(HtmlPage.COL_DOWNLOADED, downloaded);
		values.put(HtmlPage.COL_FORCED, forced);
		values.put(HtmlPage.COL_TRIES, tries);
		return values;
	}
	
	/**
	 * return all rows of html table
	 * @return
	 */
	public Cursor getAll(){
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, null, null, null, null, null, null);
		Log.d(TAG, "all html pages:" + String.valueOf(c.getCount()));
		return c;
	}	
	
	public String getTweetId(int id){
		String[] col = {Tweets.COL_TID};
		Cursor c = database.query(DBOpenHelper.TABLE_TWEETS, col, "_id = '" + String.valueOf(id)+ "'", null, null, null, null);
		c.moveToFirst();
		return String.valueOf(c.getLong(c.getColumnIndex(Tweets.COL_TID)));
	}

}
