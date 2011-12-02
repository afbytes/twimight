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
package ch.ethz.twimight.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This is where all global constants for configuration go!
 * @author thossmann
 *
 */
public final class Constants { 
    
	
	/**
	 * Do not instantiate!
	 */
	private Constants() { throw new AssertionError("Constants is uninstantiable"); }

	// TDS configuration
	public static final int TDS_MESSAGE_VERSION = 1; /** Which message version */
    public static final long TDS_UPDATE_INTERVAL = 60*1000L; /** Interval (millisec) for updating MAC list from the TDS */
    public static final long TDS_UPDATE_RETRY_INTERVAL = 60*1000L; /** Initial interval for re-trying to connect to TDS */
    public static final boolean TDS_DEFAULT_ON = true; /** Opt in or opt out for TDS communication? */
    public static final long WAIT_FOR_CONNECTIVITY = 5*1000L; /** After waking up we have to wait to get connectivity */
    public static final String TDS_BASE_URL = "https://api.twimight.com"; /** The URL of the TDS */
    public static final int HTTP_CONNECTION_TIMEOUT = 3*1000; /** How long do we wait for a connection? */
    public static final int HTTP_SOCKET_TIMEOUT = 20*1000; /** How long do we wait for data? HINT: We have to wait long, since this includes authentication in the Twitter server*/
    
    // Bluetooth scanning configuration
    public static final long SCANNING_INTERVAL = 30*1000L; /** Interval for Bluetooth scans */
    public static final long MIN_LISTEN_TIME = 10*1000L; /** Interval for Bluetooth scans */
    public static final long RANDOMIZATION_INTERVAL = 10*1000L; /** Randomization interval for scanning */
	public static final boolean DISASTER_DEFAULT_ON = false; /** are we in disaster mode by default? */
	public static final long WAIT_FOR_BLUETOOTH = 20*1000L; /** If Bluetooth is off, how long to we wait for it to enable? */
    
	//Message types from the BluetoothService Handler	  
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_CONNECTION_SUCCEEDED = 4;
	public static final int MESSAGE_DELAY = 6;
	public static final int MESSAGE_CONNECTION_FAILED =8;
	public static final int MESSAGE_CONNECTION_LOST =10;
	
	// Key names from the BluetoothService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String DEVICE_ADDRESS = "device address";
	public static final String TOAST = "toast";
	public static final String DEVICE = "device";
	public static final String PEERS_MET = "peers_met";
	
	// Location
	public static final long LOCATION_UPDATE_TIME = 10*60*1000L; /** Location update interval */
	public static final long LOCATION_WAIT = 60*1000L; /** How long should we wait for a location. This MUST NOT be larger than LOCATION_UPDATE_TIME */
	public static final int LOCATION_ACCURACY = 150; /** What is a satisfying accuracy? */
	public static final boolean LOCATION_DEFAULT_ON = true; /** Do we send location updates by default? */
	public static Map<String, Integer> locationProvidersMap = new HashMap<String, Integer>();
	static {
        locationProvidersMap.put("gps", 1);
        locationProvidersMap.put("network", 2);
        locationProvidersMap = Collections.unmodifiableMap(locationProvidersMap);
    }	
	
	// Security
	public static final long DISASTER_DURATION = 24*60*60*1000L; /** How long do we need the certificate to be valid during loss of connectivity? */
	public static final int SECURITY_KEY_SIZE = 2048; /** RSA Key length */
	
	// Twitter
	public static final int CONSUMER_ID = 1;
	public static final int LOGIN_ATTEMPTS = 5; /** How many times do we attempt to log in before giving up? */
	public static final int TWEET_LENGTH = 140; /** The max length of a tweet */
	public static final boolean TWEET_DEFAULT_LOCATION = false; /** Are tweets by default geo-tagged or not? */
	
	public static final int NR_TWEETS = 50; /** how many tweets to request from twitter in timeline update */
	public static final int NR_FAVORITES = 50; /** how many favorites to request from twitter */
	public static final int NR_MENTIONS = 50; /** how many mentions to request from twitter */
	
	public static final long TIMELINE_MIN_SYNCH = 60*1000L; /** Minimum time between two timeline updates */
	public static final long FAVORITES_MIN_SYNCH = 60*1000L; /** Minimum time between two favorite updates */
	public static final long MENTIONS_MIN_SYNCH = 60*1000L; /** Minimum time between two mentions updates */
	public static final long FRIENDS_MIN_SYNCH = 60*1000L; /** Minimum time between two updates of the friends list */
	public static final long FOLLOWERS_MIN_SYNCH = 60*1000L; /** Minimum time between two updates of the list of followers */
	public static final long USERS_MIN_SYNCH = 24*3600*1000L; /** Minmum time between two updates of a user profile */
	
	public static final int TIMELINE_BUFFER_SIZE = 100; /** How many "normal" tweets (not favorites, mentions, etc) to store locally */
	public static final int FAVORITES_BUFFER_SIZE = 100; /** How many favorites to store locally */
	public static final int MENTIONS_BUFFER_SIZE = 100; /** How many mentions to store locally */




	
}
