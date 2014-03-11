package ch.ethz.twimight.net.twitter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import twitter4j.MediaEntity;
import twitter4j.URLEntity;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.Html.StartServiceHelper;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.util.Serialization;

/**
 * Initiates an action on a single tweet.
 * 
 * @author msteven
 * 
 */
public class TweetActionService extends IntentService {

	private static final String TAG = TweetActionService.class.getName();

	public static final String EXTRA_KEY_ROW_ID = "EXTRA_ROW_ID";
	public static final String EXTRA_KEY_ACTION = "EXTRA_ACTION";

	public static final String EXTRA_ACTION_RETWEET = "EXTRA_ACTION_RETWEET";
	public static final String EXTRA_ACTION_DELETE = "EXTRA_ACTION_DELETE";
	public static final String EXTRA_ACTION_FAVORITE = "EXTRA_ACTION_FAVORITE";
	public static final String EXTRA_ACTION_UNFAVORITE = "EXTRA_ACTION_UNFAVORITE";
	public static final String EXTRA_ACTION_CACHE_LINKS = "EXTRA_ACTION_CACHE_LINKS";

	private long mRowId;
	private Cursor mCursor;

	private Uri mUri;

	public TweetActionService() {
		super(TweetActionService.class.getName());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		mRowId = intent.getLongExtra(EXTRA_KEY_ROW_ID, -1);
		String action = intent.getStringExtra(EXTRA_KEY_ACTION);
		mUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + mRowId);
		mCursor = getContentResolver().query(mUri, null, null, null, null);
		if (mCursor != null) {
			mCursor.moveToFirst();
			if (EXTRA_ACTION_RETWEET.equals(action)) {
				retweet();
			} else if (EXTRA_ACTION_DELETE.equals(action)) {
				delete();
			} else if (EXTRA_ACTION_FAVORITE.equals(action)) {
				favorite();
			} else if (EXTRA_ACTION_UNFAVORITE.equals(action)) {
				unfavorite();
			} else if (EXTRA_ACTION_CACHE_LINKS.equals(action)) {
				cacheLinks();
			} else {
				Log.w(TAG, TweetActionService.class.getName() + " started without valid action extra");
			}
			mCursor.close();
		} else {
			Log.e(TAG, TweetActionService.class.getName() + " could not load cursor for tweet with row ID " + mRowId);
		}
	}

	/**
	 * Marks the specified tweet to be retweeted.
	 */
	private void retweet() {
		Log.d(TAG, "TweetActionService retweet(); row ID: " + mRowId);
		ContentValues cv = new ContentValues();
		int flags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
		cv.put(Tweets.COL_FLAGS, flags | Tweets.FLAG_TO_RETWEET);

		int buffer = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER));
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode",
				Constants.DISASTER_DEFAULT_ON) == true) {
			cv.put(Tweets.COL_BUFFER, buffer | Tweets.BUFFER_DISASTER);
		} else {
			cv.put(Tweets.COL_BUFFER, buffer);
		}
		getContentResolver().update(mUri, cv, null, null);
	}

	/**
	 * Marks the specified tweet for deleting and removes all the attached
	 * images and downloaded HTML pages
	 */
	private void delete() {
		Log.d(TAG, "TweetActionService delete(); row ID: " + mRowId);
		// delete picture
		String delPhotoName = mCursor.getString(mCursor.getColumnIndex(Tweets.COL_MEDIA));
		SDCardHelper sdCardHelper = new SDCardHelper();
		if (delPhotoName != null) {
			String twitterUserId = String.valueOf(mCursor.getLong(mCursor
					.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID)));
			String photoPath = Tweets.PHOTO_PATH + "/" + twitterUserId;
			String[] filePath = { photoPath };
			if (sdCardHelper.checkSDState(filePath)) {
				File photoFile = sdCardHelper.getFileFromSDCard(photoPath, delPhotoName);
				photoFile.delete();
			}

		}
		// delete html pages
		List<String> linkUrls = getLinkUrls();

		HtmlPagesDbHelper htmlDbHelper = new HtmlPagesDbHelper(this);
		htmlDbHelper.open();

		if (!linkUrls.isEmpty()) {
			for (String htmlUrl : linkUrls) {
				Cursor cursorHtml = htmlDbHelper.getPageInfo(htmlUrl);
				if (cursorHtml != null) {
					String[] filePath = { HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(this) };
					if (sdCardHelper.checkSDState(filePath)) {
						File htmlFile = sdCardHelper.getFileFromSDCard(filePath[0],
								cursorHtml.getString(cursorHtml.getColumnIndex(HtmlPage.COL_FILENAME)));
						htmlFile.delete();
						htmlDbHelper.deletePage(htmlUrl);
					}
					htmlDbHelper.deletePage(htmlUrl);
				}
			}
		}

		// mark for deleting in db (or delete directly if not yet on twitter)
		if (!mCursor.isNull((mCursor.getColumnIndex(Tweets.COL_TID)))) {
			int flags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
			ContentValues cv = new ContentValues();
			cv.put(Tweets.COL_FLAGS, flags | Tweets.FLAG_TO_DELETE);
			getContentResolver().update(mUri, cv, null, null);
		} else {
			getContentResolver().delete(mUri, null, null);
		}
	}

	/**
	 * Marks the specified tweet to be favorited.
	 */
	private void favorite() {
		Log.d(TAG, "TweetActionService favorite(); row ID: " + mRowId);
		ContentValues cv = new ContentValues();
		int flags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
		int buffer = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER));
		try {
			// set favorite flag and clear unfavorite flag
			if ((buffer & Tweets.BUFFER_FAVORITES) != 0) {
				cv.put(Tweets.COL_FLAGS, (flags & ~Tweets.FLAG_TO_UNFAVORITE));
			} else {
				cv.put(Tweets.COL_FLAGS, (flags | Tweets.FLAG_TO_FAVORITE) & (~Tweets.FLAG_TO_UNFAVORITE));
			}
			// put in favorites bufer
			cv.put(Tweets.COL_BUFFER, buffer | Tweets.BUFFER_FAVORITES);
		} catch (Exception e) {
			e.printStackTrace();
		}
		getContentResolver().update(mUri, cv, null, null);
	}

	/**
	 * Marks the specified tweet to be favorited.
	 */
	private void unfavorite() {
		Log.d(TAG, "TweetActionService unfavorite(); row ID: " + mRowId);
		ContentValues cv = new ContentValues();
		int flags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
		int buffer = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER));
		// clear favorite flag and set unfavorite flag
		if ((buffer & Tweets.BUFFER_FAVORITES) != 0) {
			cv.put(Tweets.COL_FLAGS, (flags & (~Tweets.FLAG_TO_FAVORITE)) | Tweets.FLAG_TO_UNFAVORITE);
		} else {
			cv.put(Tweets.COL_FLAGS, (flags & (~Tweets.FLAG_TO_FAVORITE)));
		}
		if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_TID))) {
			cv.put(Tweets.COL_BUFFER, buffer);
		} else {
			cv.put(Tweets.COL_BUFFER, buffer & (~Tweets.BUFFER_FAVORITES));
		}
		getContentResolver().update(mUri, cv, null, null);
	}

	private List<String> getLinkUrls() {
		List<String> linkUrls = new LinkedList<String>();
		byte[] serializedMediaEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_MEDIA_ENTITIES));
		MediaEntity[] mediaEntities = Serialization.deserialize(serializedMediaEntities);
		if (mediaEntities != null) {
			for (URLEntity mediaEntity : mediaEntities) {
				linkUrls.add(mediaEntity.getURL());
			}
		}
		byte[] serializedUrlEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_URL_ENTITIES));
		URLEntity[] urlEntities = Serialization.deserialize(serializedUrlEntities);
		if (urlEntities != null) {
			for (URLEntity urlEntity : urlEntities) {
				linkUrls.add(urlEntity.getURL());
			}
		}
		return linkUrls;
	}

	/**
	 * Downloads the links contained in the specified tweet.
	 */
	private void cacheLinks() {
		List<String> linkUrls = getLinkUrls();
		List<String> urlsToDownload = new ArrayList<String>();

		HtmlPagesDbHelper htmlDbHelper = new HtmlPagesDbHelper(this);
		htmlDbHelper.open();
		String ownTwitterId = LoginActivity.getTwitterId(this);
		for (String linkUrl : linkUrls) {
			Cursor cursorInfo = htmlDbHelper.getPageInfo(linkUrl);
			String filename = null;
			boolean fileStatusNormal = true;
			if (cursorInfo != null) {
				// check if file status normal, exists and size
				String[] filePath = { HtmlPage.HTML_PATH + "/" + ownTwitterId };
				SDCardHelper sdCardHelper = new SDCardHelper();
				if (sdCardHelper.checkSDState(filePath)) {

					if (!cursorInfo.isNull(cursorInfo.getColumnIndex(HtmlPage.COL_FILENAME))) {
						filename = cursorInfo.getString(cursorInfo.getColumnIndex(HtmlPage.COL_FILENAME));

						if (sdCardHelper.getFileFromSDCard(filePath[0], filename).length() <= 1) {
							fileStatusNormal = false;
						}
					}

				}

			}
			if (cursorInfo == null || (filename == null) || !fileStatusNormal) {
				urlsToDownload.add(linkUrl);
			}
		}

		// insert database
		String[] filePath = { HtmlPage.HTML_PATH + "/" + ownTwitterId };
		SDCardHelper sdCardHelper = new SDCardHelper();
		if (sdCardHelper.checkSDState(filePath)) {

			Long tweetId = mCursor.getLong(mCursor.getColumnIndex(Tweets.COL_DISASTERID));
			for (int i = 0; i < urlsToDownload.size(); i++) {

				Cursor cursorInfo = htmlDbHelper.getPageInfo(urlsToDownload.get(i));
				if (cursorInfo != null) {

					int attempts = cursorInfo.getInt(cursorInfo.getColumnIndex(HtmlPage.COL_ATTEMPTS));
					if (attempts > HtmlPage.DOWNLOAD_LIMIT) {

						String filename = null;
						if (!cursorInfo.isNull(cursorInfo.getColumnIndex(HtmlPage.COL_FILENAME))) {
							filename = cursorInfo.getString(mCursor.getColumnIndex(HtmlPage.COL_FILENAME));
							sdCardHelper.deleteFile(filePath[0] + "/" + filename);
						}

						htmlDbHelper.updatePage(urlsToDownload.get(i), null, tweetId,
								HtmlPagesDbHelper.DOWNLOAD_FORCED, 0);
					}

				} else {
					htmlDbHelper.insertPage(urlsToDownload.get(i), tweetId, HtmlPagesDbHelper.DOWNLOAD_FORCED);
				}
			}

			getContentResolver().notifyChange(Tweets.TABLE_TIMELINE_URI, null);
			// insert database and start downloading service
			StartServiceHelper.startService(this);
		}
	}

}
