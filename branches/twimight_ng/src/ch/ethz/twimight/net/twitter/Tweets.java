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
 * Tweet definitions: Column names for the DB and MIME types, columns and URIs for the content provider
 * @author thossmann
 *
 */
public class Tweets implements BaseColumns {

	// This class cannot be instantiated
	private Tweets(){ }

	public static final String TWEET_AUTHORITY = "ch.ethz.twimight.Tweets"; /** authority part of the URI */
	public static final String TWEETS = "tweets"; /** the tweets part of the URI */
	public static final Uri TWEETS_URI = Uri.parse("content://" + TWEET_AUTHORITY + "/" + TWEETS); /** URI to reference all tweets */
	public static final Uri CONTENT_URI = TWEETS_URI; /** the content URI */	
	
	// MIME type definitions
	public static final String TWEETS_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.twimight.tweet"; /** the MIME type of a set of tweets */
	public static final String TWEET_CONTENT_TYPE = "vnd.android.cursor.item/vnd.twimight.tweet"; /** the MIME type of a single tweet */
	
	// URI name definitions
	public static final String TWEETS_TABLE_TIMELINE = "timeline"; /** the timeline filter */
	public static final String TWEETS_TABLE_FAVORITES = "favorites"; /** the favorites filter */
	public static final String TWEETS_TABLE_MENTIONS = "mentions"; /** the mentions filter */
	public static final String TWEETS_TABLE_USER = "user"; /** the mentions filter */
	
	public static final String TWEETS_SOURCE_NORMAL = "normal"; /** only normal tweets (no disaster tweets) */
	public static final String TWEETS_SOURCE_DISASTER = "disaster"; /** only disaster tweets */
	public static final String TWEETS_SOURCE_ALL = "all"; /** both, normal and disaster tweets */
	public static final String TWEETS_ID = "id"; /** a specific tweet */
	public static final String SEARCH = "search"; /** a search request */
	
	// here start the column names
	public static final String COL_TEXT = "text"; /** the tweet text */
	public static final String COL_USER = "user"; /** the user id of the author */
	public static final String COL_SCREENNAME = "user_screenname"; /** the user screenname of the author */
	public static final String COL_TID = "t_id"; /** the "official" tweet ID from twitter */
	public static final String COL_REPLYTO = "reply_to"; /** the tweet ID to which this tweet replies */
	public static final String COL_FAVORITED = "favorited"; /** is it a favorite? */
	public static final String COL_RETWEETED = "retweeted"; /** did we retweet the tweet? */
	public static final String COL_RETWEETCOUNT = "retweet_count"; /** how many retweets does twitter report for this tweet */
	public static final String COL_MENTIONS = "mentions"; /** does the tweet mention the local user */
	public static final String COL_LAT = "lat"; /** latitude in case of geo-tagging */
	public static final String COL_LNG = "lng"; /** longitude in case of geo-tagging */
	public static final String COL_CREATED = "created"; /** the creation timestamp (millisecs since 1970) */
	public static final String COL_RECEIVED = "received"; /** timestamp we insert the tweet into the DB */
	public static final String COL_SOURCE = "source"; /** the application with which the tweet was created (as reported by twitter) */
	public static final String COL_BUFFER = "buffer_flags"; /** which buffer(s) is the tweet in */
	public static final String COL_FLAGS = "flags"; /** Transactional flags */
	
	// for disaster mode
	public static final String COL_ISDISASTER = "is_disaster_tweet"; /** disaster or normal tweet? */
	public static final String COL_DISASTERID = "d_id"; /** the disaster ID of the tweet (for both, disaster and normal tweets) this is the java hashcode (32Bit) of the String TWEETS_COLUMNS_TEXT+TWEETS_COLUMNS_USER */
	public static final String COL_ISVERIFIED = "is_verified"; /** is the signature of the disaster tweet valid? */
	public static final String COL_SIGNATURE = "signature"; /** the signature of the disaster tweet */
	public static final String COL_CERTIFICATE = "certificate"; /** the certificate of the user */
	
	public static final String DEFAULT_SORT_ORDER = COL_CREATED + " DESC";
	
	// flags for synchronizing with twitter
	public static final int FLAG_TO_INSERT = 1; /** The tweet is new and should be posted to twitter */
	public static final int FLAG_TO_FAVORITE = 2; /** The tweet was marked as a favorite locally */
	public static final int FLAG_TO_UNFAVORITE = 4; /** The tweet was un-favorited locally */
	public static final int FLAG_TO_RETWEET = 8; /** The tweet was marked for re-tweeting locally */ 
	public static final int FLAG_TO_DELETE = 16; /** The tweet was marked for deletion locally */
	public static final int FLAG_TO_UPDATE = 32; /** The tweet should be reloaded from twitter */
	
	// flags to mark which buffer(s) a tweet belongs to. (Buffer sizes are defined in class Constants)
	public static final int BUFFER_TIMELINE = 1; /** The normal timeline */
	public static final int BUFFER_DISASTER = 2; /** Disaster tweets of other users */
	public static final int BUFFER_MYDISASTER = 4; /** The disaster tweets of the local user */
	public static final int BUFFER_FAVORITES = 8; /** All favorites of the local user */
	public static final int BUFFER_MENTIONS = 16; /** All mentions of the local user */
	public static final int BUFFER_USERS = 32; /** All mentions of the local user */
	public static final int BUFFER_SEARCH = 64; /** Results obtained from searching on Twitter */

}
