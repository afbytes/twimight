package ch.ethz.twimight;

/**
 * This is where all global constants (should) go!
 * @author thossmann
 *
 */
final class Constants { 
    
	/**
	 * Do not instantiate!
	 */
	private Constants() { throw new AssertionError("Constants is uninstantiable"); }
    
	// Special Hashtags
    public static final String TWHISPER_HASHTAG = "#twhisper"; /** Special hashtag for tweets that will not be uploaded to Twitter. */
    public static final String TWINTERNAL_HASHTAG = "#twinternal"; /** Special hashtag for tweets that will not be uploaded and not be shown to the user (e.g., internal control messages) */
    
    // Intent Actions
    public static final String ACTION_NEW_DISASTER_TWEET = "New disaster tweet"; /** Action for intent to signal new disaster tweet */
    public static final String ACTION_DISASTER_MODE = "Disaster Mode"; /** Action for intent to signal mode switch (normal <-> disaster) */
    public static final String ACTION_NEW_TWEETS = "ACTION_NEW_TWITTER_STATUS"; /** Action for intent to signal new stuff in timeline */
    
    // Notifications
    public static final int MENTION_NOTIFICATION_ID = 49; /** Pending user notification ID for new mentions */
    static final int DIRECT_NOTIFICATION_ID = 48; /** Pending user notification ID for new DMs */
    public static final int NOTIFICATION_ID = 47; /** Pending user notification ID for new Tweets in timeline */
    
    // Update intervals
    public static final long TWEET_UPDATE_INTERVAL = 110000L; /**  Update interval for loading tweets. This is also for mentions and DMs! */
    public static final long FRIENDS_UPDATE_INTERVAL = 3600000L; /** Update interval for laoding followers and followees */
    
    public static final long KEY_GENERATION_INTERVAL = 172800000L; /** Interval for creating a new key pair */
}