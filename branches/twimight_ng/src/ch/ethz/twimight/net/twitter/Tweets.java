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
 * Tweet columns
 * @author thossmann
 *
 */
public class Tweets implements BaseColumns {

	// This class cannot be instantiated
	private Tweets(){ }

	/**
	 * The authority part of the URI
	 */
	public static final String TWEET_AUTHORITY = "ch.ethz.twimight.Tweets";

	/**
	 * The tweets
	 */
	public static final String TWEETS = "tweets";

	/**
	 *  URI to reference all tweets
	 */
	public static final Uri TWEETS_URI = Uri.parse("content://" + TWEET_AUTHORITY + "/" + TWEETS);
	
	/**
	 * The content:// style URL
	 */
	public static final Uri CONTENT_URI = TWEETS_URI;
	
	
	// MIME type definitions
	/**
	 * The MIME type for a set of tweets
	 */
	public static final String TWEETS_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.twimight.tweet";
	
	/**
	 * The MIME type of a single tweet
	 */
	public static final String TWEET_CONTENT_TYPE = "vnd.android.cursor.item/vnd.twimight.tweet";
	
	// URI name definitions
	/**
	 * The name of the table for timeline tweets
	 */
	public static final String TWEETS_TABLE_TIMELINE = "timeline";
	
	/**
	 * The name of the table for favorites
	 */
	public static final String TWEETS_TABLE_FAVORITES = "favorites";
	
	/**
	 * The name of the table for mentions
	 */
	public static final String TWEETS_TABLE_MENTIONS = "mentions";
	
	/**
	 * The name of the source for normal tweets
	 */
	public static final String TWEETS_SOURCE_NORMAL = "normal";
	
	/**
	 * The name of the source for disaster tweets
	 */
	public static final String TWEETS_SOURCE_DISASTER = "disaster";
	
	/**
	 * The name of the source for disaster AND normal tweets
	 */
	public static final String TWEETS_SOURCE_ALL = "all";
	
	/**
	 * The name of the tweet id
	 */
	public static final String TWEETS_ID = "id";
	
	// here start the column names
	public static final String TWEETS_COLUMNS_TEXT = "text";
	public static final String TWEETS_COLUMNS_USER = "user";
	public static final String TWEETS_COLUMNS_TID = "t_id";
	public static final String TWEETS_COLUMNS_REPLYTO = "reply_to";
	public static final String TWEETS_COLUMNS_FAVORITED = "favorited";
	public static final String TWEETS_COLUMNS_RETWEETED = "retweeted";
	public static final String TWEETS_COLUMNS_RETWEETCOUNT = "retweet_count";
	public static final String TWEETS_COLUMNS_MENTIONS = "mentions";
	public static final String TWEETS_COLUMNS_LAT = "lat";
	public static final String TWEETS_COLUMNS_LNG = "lng";
	public static final String TWEETS_COLUMNS_CREATED = "created";
	public static final String TWEETS_COLUMNS_SOURCE = "source";
	public static final String TWEETS_COLUMNS_FLAGS = "flags"; /** Transactional flags */
	
	public static final String DEFAULT_SORT_ORDER = TWEETS_COLUMNS_CREATED + " DESC";
	
	// flags for synchronizing with twitter
	public static final int FLAG_TO_INSERT = 1; /** The tweet is new and should be posted to twitter */
	public static final int FLAG_TO_FAVORITE = 2; /** The tweet was marked as a favorite locally */
	public static final int FLAG_TO_UNFAVORITE = 4; /** The tweet was un-favorited locally */
	public static final int FLAG_TO_RETWEET = 8; /** The tweet was marked for re-tweeting locally */ 
	public static final int FLAG_TO_DELETE = 16; /** The tweet was marked for deletion locally */
	public static final int FLAG_TO_UPDATE = 32; /** The tweet should be reloaded from twitter */




	
}
