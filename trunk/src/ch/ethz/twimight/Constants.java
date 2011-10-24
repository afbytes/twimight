package ch.ethz.twimight;

// This is where all global constants go
final class Constants { 
    
	private Constants() { throw new AssertionError("Constants is uninstantiable"); }
    
    public static final String TWHISPER_HASHTAG = "#twhisper";
    public static final String TWINTERNAL_HASHTAG = "#twinternal";
    
    // Intent Actions
    public static final String ACTION_NEW_DISASTER_TWEET = "New disaster tweet";
    public static final String ACTION_DISASTER_MODE = "Disaster Mode";
}