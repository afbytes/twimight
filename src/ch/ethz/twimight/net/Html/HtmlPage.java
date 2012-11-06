package ch.ethz.twimight.net.Html;

import android.provider.BaseColumns;


/**
 * Html pages definitions: Column names for the DB and flags
 * @author thossmann
 *
 */
public class HtmlPage implements BaseColumns {

	// This class cannot be instantiated
	private HtmlPage(){ }

	// here start the column names
	public static final String COL_URL = "url"; /** url of the page */
	public static final String COL_HTML = "text"; /** the page */

	// flags for synchronizing with twitter
	public static final int FLAG_TO_DOWNLOAD = 1; /** the page should be downloaded */
	public static final int FLAG_TO_DELETE = 2; /** the page should be deleted */


}
