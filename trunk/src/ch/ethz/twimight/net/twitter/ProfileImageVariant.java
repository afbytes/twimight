package ch.ethz.twimight.net.twitter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the four profile image sizes served by Twitter.
 * 
 * @author msteven
 * 
 */
public enum ProfileImageVariant {
	NORMAL(
			"_normal"),
	BIGGER(
			"_bigger"),
	MINI(
			"_mini"),
	ORIGINAL(
			"");

	private final String mSuffix;
	/*
	 * start of the url (everything up to the underscore): ^(.*?) variant suffix
	 * (not captured): (?:_[a-z]+)? fietype suffix (e.g. ".png"): (\\.[a-z]+)$
	 */
	private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:_[a-z]+)?(\\.[a-z]+)$");

	private ProfileImageVariant(String suffix) {
		mSuffix = suffix;
	}

	/**
	 * Transforms a Twitter profile image URL into the URL of the desired size
	 * variant.
	 * 
	 * @param imageUrl
	 *            a Twitter profile image URL of any variant
	 * @param desiredVariant
	 *            the desired variant
	 * @return the URL of the desired image variant
	 */
	public static String getVariantUrl(String imageUrl, ProfileImageVariant desiredVariant) {
		String result = null;
		if (imageUrl != null) {
			Matcher matcher = SUFFIX_PATTERN.matcher(imageUrl);
			result = matcher.replaceAll("$1" + desiredVariant.mSuffix + "$2");
		}
		return result;
	}
}