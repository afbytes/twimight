package ch.ethz.twimight;

// This is where all global constants go
final class Constants { 
    
	private Constants() { throw new AssertionError("Constants is uninstantiable"); }
    
	// Special Hashtags
    public static final String TWHISPER_HASHTAG = "#twhisper";
    public static final String TWINTERNAL_HASHTAG = "#twinternal";
    
    // Intent Actions
    public static final String ACTION_NEW_DISASTER_TWEET = "New disaster tweet";
    public static final String ACTION_DISASTER_MODE = "Disaster Mode";
    
    // Update intervals
    public static final long TWEET_UPDATE_INTERVAL = 110000L; // this is also for mentions and DMs!
    public static final long FRIENDS_UPDATE_INTERVAL = 3600000L; //290000L; // for followers and followees 
}