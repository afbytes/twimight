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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * Tweet definitions: Column names for the DB and MIME types, columns and URIs
 * for the content provider
 * 
 * @author thossmann
 * @author pcarta
 */
public class Tweets implements BaseColumns {

	// This class cannot be instantiated
	private Tweets() {
	}

	/** authority part of the URI */
	public static final String TWEET_AUTHORITY = "ch.ethz.twimight.Tweets";

	// MIME type definitions
	/** the MIME type of a set of tweets */
	public static final String TWEETS_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.twimight.tweet";
	/** the MIME type of a single tweet */
	public static final String TWEET_CONTENT_TYPE = "vnd.android.cursor.item/vnd.twimight.tweet";

	// URI name definitions
	/** the timeline filter */
	public static final String TWEETS_TABLE_TIMELINE = "timeline";
	/** the favorites filter */
	public static final String TWEETS_TABLE_FAVORITES = "favorites";
	/** the mentions filter */
	public static final String TWEETS_TABLE_MENTIONS = "mentions";
	/** the mentions filter */
	public static final String TWEETS_TABLE_USER = "user";

	/** only normal tweets (no disaster tweets) */
	public static final String TWEETS_SOURCE_NORMAL = "normal";
	/** only disaster tweets */
	public static final String TWEETS_SOURCE_DISASTER = "disaster";
	/** both, normal and disaster tweets */
	public static final String TWEETS_SOURCE_ALL = "all";

	/** a specific tweet */
	public static final String TWEET_TID = "tweet_tid";
	/** a search request */
	public static final String SEARCH = "search";
	/** new tweets received */
	public static final String TWEETS_SINCE_LAST_UPDATE = "tweetsSinceLastUpdate";

	// URI for cursor notifications
	public static final String TWEETS = "tweets";
	/** the tweets part of the URI */
	private static final String BASE_URI = "content://" + TWEET_AUTHORITY + "/";
	public static final Uri ALL_TWEETS_URI = Uri.parse(BASE_URI + TWEETS);
	/** URI to reference all tweets */
	public static final Uri TABLE_TIMELINE_URI = Uri.parse(BASE_URI + TWEETS_TABLE_TIMELINE);
	public static final Uri TABLE_FAVORITES_URI = Uri.parse(BASE_URI + TWEETS_TABLE_FAVORITES);
	public static final Uri TABLE_MENTIONS_URI = Uri.parse(BASE_URI + TWEETS_TABLE_MENTIONS);
	public static final Uri TABLE_SEARCH_URI = Uri.parse(BASE_URI + SEARCH);
	public static final Uri TABLE_USER_URI = Uri.parse(BASE_URI + TWEETS_TABLE_USER);

	// photo path
	public static final String PHOTO_PATH = "twimight_photos";

	// here start the column names
	public static final String COL_ROW_ID = BaseColumns._ID;
	/**
	 * the tweet text (uses display version of urls => ready for display without
	 * further processing but not aligned with entity indexes)
	 */
	public static final String COL_TEXT = "text";
	/** original tweet text with twitter urls **/
	public static final String COL_TEXT_PLAIN = "text_plain";
	/** serialized array of hashtag entities */
	public static final String COL_HASHTAG_ENTITIES = "hashtag_entities";
	/** serialized array of media entities */
	public static final String COL_MEDIA_ENTITIES = "media_entities";
	/** serialized array of url entities */
	public static final String COL_URL_ENTITIES = "url_entities";
	/** serialized array of user mention entities */
	public static final String COL_USER_MENTION_ENTITIES = "user_mention_entities";

	/** the user id of the author */
	public static final String COL_TWITTERUSER = "twitteruser_id";
	/** the user screenname of the author */
	public static final String COL_SCREENNAME = "user_screenname";
	/** the "official" tweet ID from twitter */
	public static final String COL_TID = "t_id";
	/** the tweet ID to which this tweet replies */
	public static final String COL_REPLYTO = "reply_to";
	/** the user ID to which this tweet repies */
	public static final String COL_REPLY_TO_USER_ID = "reply_to_user_id";
	/** the user ID to which this tweet repies */
	public static final String COL_REPLY_TO_SCREEN_NAME = "reply_to_screen_name";
	/** did we retweet the tweet? */
	public static final String COL_RETWEETED = "retweeted";
	/** how many favorites does twitter report for this tweet */
	public static final String COL_FAVORITE_COUNT = "favorite_count";
	/** how many retweets does twitter report for this tweet */
	public static final String COL_RETWEET_COUNT = "retweet_count";
	/** has been retweeted by */
	public static final String COL_RETWEETED_BY = "retweetedBy";
	/** does the tweet mention the local user */
	public static final String COL_MENTIONS = "mentions";
	/** latitude in case of geo-tagging */
	public static final String COL_LAT = "lat";
	/** longitude in case of geo-tagging */
	public static final String COL_LNG = "lng";
	/** the creation timestamp (millisecs since 1970) */
	public static final String COL_CREATED = "created";
	/** timestamp we insert the tweet into the DB */
	public static final String COL_RECEIVED = "received";
	/**
	 * the application with which the tweet was created (as reported by twitter)
	 */
	public static final String COL_SOURCE = "source";
	/** which buffer(s) is the tweet in */
	public static final String COL_BUFFER = "buffer_flags";
	/** url of media */
	public static final String COL_MEDIA = "media_url";
	/** Transactional flags */
	public static final String COL_FLAGS = "flags";
	// /** url hashtag */
	// public static final String COL_URLS = "";
	/** status of html pages related to this tweet, 0: not have, 1: does have */
	public static final String COL_HTML_PAGES = "html_pages";

	// for disaster mode
	/**
	 * the disaster ID of the tweet (for both, disaster and normal tweets) this
	 * is the java hashcode (32Bit) of the String
	 * TWEETS_COLUMNS_TEXT+TWEETS_COLUMNS_USER
	 */
	public static final String COL_DISASTERID = "d_id";
	/** is the signature of the disaster tweet valid? */
	public static final String COL_ISVERIFIED = "is_verified";
	/** the signature of the disaster tweet */
	public static final String COL_SIGNATURE = "signature";
	/** the certificate of the user */
	public static final String COL_CERTIFICATE = "certificate";

	public static final String DEFAULT_SORT_ORDER = COL_CREATED + " DESC";
	public static final String REVERSE_SORT_ORDER = COL_CREATED + " ASC";

	// flags for synchronizing with twitter
	/** The tweet is new and should be posted to twitter */
	public static final int FLAG_TO_INSERT = 1;
	/** The tweet was marked as a favorite locally */
	public static final int FLAG_TO_FAVORITE = 2;
	/** The tweet was un-favorited locally */
	public static final int FLAG_TO_UNFAVORITE = 4;
	/** The tweet was marked for re-tweeting locally */
	public static final int FLAG_TO_RETWEET = 8;
	/** The tweet was marked for deletion locally */
	public static final int FLAG_TO_DELETE = 16;
	/** The tweet should be reloaded from twitter */
	public static final int FLAG_TO_UPDATE = 32;

	// flags to mark which buffer(s) a tweet belongs to. (Buffer sizes are
	// defined in class Constants)
	/** The normal timeline */
	public static final int BUFFER_TIMELINE = 1;
	/** Disaster tweets of other users */
	public static final int BUFFER_DISASTER = 2;
	/** The disaster tweets of the local user */
	public static final int BUFFER_MYDISASTER = 4;
	/** All favorites of the local user */
	public static final int BUFFER_FAVORITES = 8;
	/** All mentions of the local user */
	public static final int BUFFER_MENTIONS = 16;
	/** All mentions of the local user */
	public static final int BUFFER_USERS = 32;
	/** Results obtained from searching on Twitter */
	public static final int BUFFER_SEARCH = 64;

}
