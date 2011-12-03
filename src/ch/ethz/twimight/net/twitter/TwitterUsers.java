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
 * Twitter Users columns
 * @author thossmann
 *
 */
public class TwitterUsers implements BaseColumns {

	// This class cannot be instantiated
	private TwitterUsers(){ }

	/**
	 * The authority part of the URI
	 */
	public static final String TWITTERUSERS_AUTHORITY = "ch.ethz.twimight.TwitterUsers";

	/**
	 * The twitter users
	 */
	public static final String TWITTERUSERS = "users";

	/**
	 *  URI to reference all twitter users
	 */
	public static final Uri TWITTERUSERS_URI = Uri.parse("content://" + TWITTERUSERS_AUTHORITY + "/" + TWITTERUSERS);
	
	/**
	 * The content:// style URL
	 */
	public static final Uri CONTENT_URI = TWITTERUSERS_URI;
	
	
	// MIME type definitions
	/**
	 * The MIME type for a set of twitter users
	 */
	public static final String TWITTERUSERS_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.twimight.twitteruser";
	
	/**
	 * The MIME type of a single twitter user
	 */
	public static final String TWITTERUSER_CONTENT_TYPE = "vnd.android.cursor.item/vnd.twimight.twitteruser";
	
	// URI name definitions

	/**
	 * Selecting only friends
	 */
	public static final String TWITTERUSERS_FRIENDS = "friends";
	/**
	 * Selecting only followers
	 */
	public static final String TWITTERUSERS_FOLLOWERS = "followers";
	/**
	 * Only the screenname of a user
	 */
	public static final String TWITTERUSERS_SCREENNAME = null;
	
	/**
	 * The name of the twitter users id
	 */
	public static final String TWITTERUSERS_ID = "id";
	
	// here start the column names
	public static final String TWITTERUSERS_COLUMNS_SCREENNAME = "screen_name";
	public static final String TWITTERUSERS_COLUMNS_ID = "u_id";
	public static final String TWITTERUSERS_COLUMNS_NAME = "name";
	public static final String TWITTERUSERS_COLUMNS_LANG = "lang";
	public static final String TWITTERUSERS_COLUMNS_DESCRIPTION = "description";
	public static final String TWITTERUSERS_COLUMNS_IMAGEURL = "profile_image_url";
	public static final String TWITTERUSERS_COLUMNS_STATUSES = "statuses_count";
	public static final String TWITTERUSERS_COLUMNS_FOLLOWERS = "followers_count";
	public static final String TWITTERUSERS_COLUMNS_FRIENDS = "friends_count";
	public static final String TWITTERUSERS_COLUMNS_LISTED = "listed_count";
	public static final String TWITTERUSERS_COLUMNS_FAVORITES = "favorites_count";
	public static final String TWITTERUSERS_COLUMNS_LOCATION = "location";
	public static final String TWITTERUSERS_COLUMNS_UTCOFFSET = "utc_offset";
	public static final String TWITTERUSERS_COLUMNS_TIMEZONE = "timezone";
	public static final String TWITTERUSERS_COLUMNS_URL = "url";
	public static final String TWITTERUSERS_CREATED = "created_at";
	public static final String TWITTERUSERS_COLUMNS_PROTECTED = "protected";
	public static final String TWITTERUSERS_COLUMNS_VERIFIED = "verified";
	
	public static final String TWITTERUSERS_COLUMNS_FOLLOWING = "following"; /** is the user following us? */
	public static final String TWITTERUSERS_COLUMNS_FOLLOW = "follow"; /** are we following the user? */
	public static final String TWITTERUSERS_COLUMNS_FOLLOWREQUEST = "follow_request_sent"; /** was a following request sent to twitter? */
	public static final String TWITTERUSERS_COLUMNS_PROFILEIMAGE = "profile_image";
	
	public static final String TWITTERUSERS_COLUMNS_LASTUPDATE = "last_update";
	public static final String TWITTERUSERS_COLUMNS_FLAGS = "flags";
	
	public static final String DEFAULT_SORT_ORDER = TWITTERUSERS_COLUMNS_SCREENNAME;
	
	// flags for synchronizing with twitter
	public static final int FLAG_TO_UPDATE = 1;
	public static final int FLAG_TO_FOLLOW = 2;
	public static final int FLAG_TO_UNFOLLOW = 4;


	
}
