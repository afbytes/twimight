package ch.ethz.bluetest.credentials;

public class Obfuscator {
    // Notes: please set the correct values otherwise the app is not able to authorize with Twitter
    static final String CONSUMER_KEY = "CONSUMER_KEY";
    static final String SECRET_INGREDIENT1 = "1111";
    static final String SECRET_INGREDIENT2 = "2222";
    static final String CRITTERCISM_ID = "CRITTERCISM_ID";

    public static String getKey() {
        return CONSUMER_KEY;
    }

    public static String getSecret() {
        return SECRET_INGREDIENT1 + SECRET_INGREDIENT2;
    }

    public static String getCrittercismId() {
        return CRITTERCISM_ID;
    }
}
