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
 * Direct Message definitions: Column names for the DB and MIME types, columns
 * and URIs for the content provider
 * 
 * @author thossmann
 * 
 */
public class DirectMessages implements BaseColumns {

	// This class cannot be instantiated
	private DirectMessages() {
	}

	/** authority part of the URI */
	public static final String DM_AUTHORITY = "ch.ethz.twimight.DMs";
	/** the direct messages part of the URI */
	public static final String DMS = "dms";
	/** URI to reference all direct messages */
	public static final Uri DMS_URI = Uri.parse("content://" + DM_AUTHORITY + "/" + DMS);
	/** the content URI */
	public static final Uri CONTENT_URI = DMS_URI;

	// MIME type definitions
	/** the MIME type of a set of direct messages */
	public static final String DMS_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.twimight.dm";
	/** the MIME type of a single direct message */
	public static final String DM_CONTENT_TYPE = "vnd.android.cursor.item/vnd.twimight.dm";
	/** the MIME type of a set of users with directmessages */
	public static final String DMUSERS_CONTENT_TYPE = "vnd.android.cursor.dir/vnd.twimight.dmuser";

	// URI name definitions
	/** only normal direct messages (no disaster messages) */
	public static final String DMS_SOURCE_NORMAL = "normal";
	/** only disaster direct messages */
	public static final String DMS_SOURCE_DISASTER = "disaster";
	/** both, normal and disaster messages */
	public static final String DMS_SOURCE_ALL = "all";
	/** all messages */
	public static final String DMS_LIST = "list";
	/** all users we have conversations with */
	public static final String DMS_USERS = "users";
	/** all direct message from and to a user */
	public static final String DMS_USER = "user";

	// here start the column names
	/** the dm text */
	public static final String COL_TEXT = "text";
	/** the user id of the sender */
	public static final String COL_SENDER = "user_id";
	/** the user id of the receiver */
	public static final String COL_RECEIVER = "receiver";
	/** the screenname of the receiver (required for sending messages) */
	public static final String COL_RECEIVER_SCREENNAME = "receiver_screenname";
	/** the "official" message ID from twitter */
	public static final String COL_DMID = "dm_id";
	/** the creation timestamp (millisecs since 1970) */
	public static final String COL_CREATED = "created";
	/** timestamp we insert the tweet into the DB */
	public static final String COL_RECEIVED = "received";
	/** which buffer(s) is the message in */
	public static final String COL_BUFFER = "buffer_flags";
	/** Transactional flags */
	public static final String COL_FLAGS = "flags";

	// for disaster mode
	/** disaster or normal message? */
	public static final String COL_ISDISASTER = "is_disaster_dm";

	/** the disaster ID of the message (for both, disaster and normal message) */
	public static final String COL_DISASTERID = "d_id";
	/** is the signature of the disaster message valid? */
	public static final String COL_ISVERIFIED = "is_verified";
	/** the signature of the disaster message */
	public static final String COL_SIGNATURE = "signature";
	/** the certificate of the user */
	public static final String COL_CERTIFICATE = "certificate";
	/** the encrypted message */
	public static final String COL_CRYPTEXT = "cryptext";

	public static final String DEFAULT_SORT_ORDER = COL_CREATED + " DESC";

	// flags for synchronizing with twitter
	/** The message is new and should be posted to twitter */
	public static final int FLAG_TO_INSERT = 1;
	/** Delete the message from Twitter */
	public static final int FLAG_TO_DELETE = 2;

	// flags to mark which buffer(s) a tweet belongs to. (Buffer sizes are
	// defined in class Constants)
	/** Holds all messages of the local user (to and from) */
	public static final int BUFFER_MESSAGES = 1;
	/** Disaster messages written by other users for me */
	public static final int BUFFER_DISASTER_ME = 2;
	/** The disaster messages of the local user */
	public static final int BUFFER_MYDISASTER = 4;
	/** Disaster messages for other users */
	public static final int BUFFER_DISASTER_OTHERS = 8;

}
