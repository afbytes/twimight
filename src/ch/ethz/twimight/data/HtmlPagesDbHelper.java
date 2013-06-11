package ch.ethz.twimight.data;

import java.io.File;
import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.text.Html;
import android.text.SpannableString;
import android.util.Log;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService.LinksParser;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.util.TweetTagHandler;

public class HtmlPagesDbHelper {
	
	private Context context;
	private static final String TAG = "HtmlPagesDbHelper";
	
	public static final int DOWNLOAD_NORMAL = 0;
	public static final int DOWNLOAD_FORCED = 1;
	
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
	public boolean insertPage(String url, String filename, long tweetId, int downloaded, int forced) {
		Log.i(TAG,"insert page url: " + url );
		ContentValues cv = createContentValues(url,filename, tweetId, downloaded, forced, 0);
		
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
	
	
	
	public void saveLinksFromCursor(Cursor c, int type){
		
		if (c != null) {
			
			for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
				
				String rawText = c.getString(c.getColumnIndex(Tweets.COL_TEXT));
				SpannableString str = new SpannableString(Html.fromHtml(rawText, null, new TweetTagHandler(context)));
				Log.i(TAG,str.toString());
				Long tid = c.getLong(c.getColumnIndex(Tweets.COL_TID));
				insertLinksIntoDb(str.toString(),tid, type);
			}
		}
	}
	
	
	
	public void insertLinksIntoDb(String tweetText, long tweetId, int type) {
		
		ArrayList<String> urls = LinksParser.parseText(tweetText);
		for (String url: urls) {
			String filename = "twimight" + String.valueOf(System.currentTimeMillis()) + ".xml";
			insertPage(url, filename, tweetId, 0, type);

		}
		
	}
	
	/**
	 * update an entry
	 * @param url
	 * @param filename
	 * @param tweetId
	 * @param downloaded
	 * @return
	 */
	public boolean updatePage(String url, String filename, long tweetId, int downloaded, int forced, int tries){
		
		ContentValues cv = createContentValues(url,filename, tweetId, downloaded, forced, tries);
		Log.d(TAG, "update an entry:" + cv.toString());
		String sql = HtmlPage.COL_URL + " = '" + url +"' ";
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
	public boolean deletePage(String url) {
		
		
		try {
			String sql = HtmlPage.COL_URL + "='" + url + "'";
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
	public ContentValues getPageInfo(String url) {
		
		
			Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, HtmlPage.COL_URL + " = '" + url +"'" , null, null, null, null);
			
			c.moveToFirst();			
			if(c.getCount() == 0)return null;		
			
			ContentValues htmlCV = createContentValues(c.getString(c.getColumnIndex(HtmlPage.COL_URL)),
					c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)),
					c.getLong(c.getColumnIndex(HtmlPage.COL_TID)),				
					c.getInt(c.getColumnIndex(HtmlPage.COL_DOWNLOADED)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_FORCED)),
					c.getInt(c.getColumnIndex(HtmlPage.COL_ATTEMPTS)));
			Log.d(TAG, "page info:" + htmlCV.toString());
			
			return htmlCV;
		
		
	}
	
	//return all urls for a tweet
	public Cursor getUrlsByTweetId(Long tweetId){
		
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, HtmlPage.COL_TID + " =" + tweetId + "" , null, null, null, null);
		c.moveToFirst();		
		
		return c;
	}
	
	/**
	 * get the tweet we received after timestamp
	 * @param timestamp
	 * @return
	 */
	public Cursor getNewTweets(Long timestamp){
		
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
			sql = HtmlPage.COL_DOWNLOADED + "= '" + String.valueOf(0) + "' and " + HtmlPage.COL_FORCED + " = '" 
					+ String.valueOf(1) + "' and " + HtmlPage.COL_ATTEMPTS + " < '" + String.valueOf(HtmlPage.DOWNLOAD_LIMIT) + "'";
		}else{
			sql = HtmlPage.COL_DOWNLOADED + "= '" + String.valueOf(0) + "' and " + HtmlPage.COL_ATTEMPTS + " < '" 
					+ String.valueOf(HtmlPage.DOWNLOAD_LIMIT) + "'";
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
	private ContentValues createContentValues(String url, String filename, long tweetId, int downloaded, int forced , int tries) {
		ContentValues values = new ContentValues();
		values.put(HtmlPage.COL_FILENAME ,filename);
		values.put(HtmlPage.COL_URL ,url);
		values.put(HtmlPage.COL_TID,tweetId );	
		values.put(HtmlPage.COL_DOWNLOADED, downloaded);
		values.put(HtmlPage.COL_FORCED, forced);
		values.put(HtmlPage.COL_ATTEMPTS, tries);
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
	
	

	
	public void clearHtmlPages(long timeSpan) {
		new ClearHtmlPages().execute(timeSpan);
	}
	
	
	
	/**
	 * clear all downloaded html pages 
	 * @author fshi
	 *
	 */
	private class ClearHtmlPages extends AsyncTask<Long, Void, Boolean>{

		@Override
		protected Boolean doInBackground(Long... params) {
			// TODO Auto-generated method stub
			
			long timeSpan = params[0];
			SDCardHelper sdCardHelper = new SDCardHelper();
			
			Cursor c = getAll();
			
			for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
				
				String filename = c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME));

				Long createdTime = Long.parseLong(filename.substring(8,filename.length()-4));
				
				if((System.currentTimeMillis() - createdTime) > timeSpan){

					String[] filePath = {HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(context)};
					if(sdCardHelper.checkSDState(filePath)){
						File deleteFile = sdCardHelper.getFileFromSDCard(filePath[0], filename);

						if(deleteFile.delete()){
							deletePage(c.getString(c.getColumnIndex(HtmlPage.COL_URL)));
						}

					}
				}

			}
			return null;
		}

	}

}
