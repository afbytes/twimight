package ch.ethz.twimight.net.Html;

import android.net.Uri;
import android.provider.BaseColumns;


/**
 * Html pages definitions: Column names for the DB and flags
 * @author thossmann
 *
 */
public class HtmlPage implements BaseColumns {

	// This class cannot be instantiated
	private HtmlPage(){ }

	//
	public static final String HTML_PATH = "twimight_offline";
	public static final String OFFLINE_PREFERENCE = "offline_preference";
	public static final String OFFLINE_MANUAL = "offline_manual";
	public static final int DOWNLOAD_LIMIT = 15;
	public static final String WEB_SHARE = "web_share";
	
	// here start the column names
	public static final String COL_URL = "url"; /** url of the page */
	public static final String COL_HTML = "text"; /** the page */
	public static final String COL_FILENAME = "filename"; /** xml file filename of the web page stored locally */	
	public static final String COL_TID = "t_id"; /** the tweet id associated with this page*/
	public static final String COL_USER = "user_id"; /** the tweet id associated with this page*/
	public static final String COL_DOWNLOADED = "downloaded"; /** if the page has been downloaded*/
	public static final String COL_FORCED = "forced"; /** if the page is a forced download one */
	public static final String COL_TRIES = "tries"; /** how many times app has tried to download the page */

	// flags for synchronizing with twitter
	public static final int FLAG_TO_DOWNLOAD = 1; /** the page should be downloaded */
	public static final int FLAG_TO_DELETE = 2; /** the page should be deleted */


}
