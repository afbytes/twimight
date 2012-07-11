package ch.ethz.twimight.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import ch.ethz.twimight.net.Html.HtmlPage;

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
	
	public int insertPage(String url, String page) {
		Log.i(TAG,"url: " + url);	
		ContentValues cv = createContentValues(url,page);
		
		try {			
			//database.insertOrThrow(DBOpenHelper.TABLE_HTML, null, cv);
			
		} catch (SQLException ex) {
			Log.e(TAG,"error inserting htlm page",ex);
		}
		return 0;
	}
	
	public String getPage(String url) {
	/*	
		Cursor c = database.query(DBOpenHelper.TABLE_HTML, null, HtmlPage.COL_URL + "= '" + url +"' " , null, null, null, null);
		if (c.getCount()==1) {
			c.moveToFirst();
			String page = c.getString(c.getColumnIndex(HtmlPage.COL_HTML));
			return page;
		}
		*/
		return null;
	}
	
	
	/**
	 * Creates a Html page record to insert in the DB
	 * @param mac long	
	 * @return
	 */
	private ContentValues createContentValues(String url, String page) {
		ContentValues values = new ContentValues();
		values.put(HtmlPage.COL_HTML ,page );
		values.put(HtmlPage.COL_URL ,url);
		return values;
	}


}
