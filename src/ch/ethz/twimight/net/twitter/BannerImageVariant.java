package ch.ethz.twimight.net.twitter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Log;

/**
 * Represents the four banner image sizes served by Twitter.
 * 
 * @author Steven Meliopoulos
 * 
 */
public enum BannerImageVariant {

	WEB(
			"web",
			520,
			260),
	WEB_RETINA(
			"web_retina",
			1040,
			520),
	IPAD(
			"ipad",
			626,
			313),
	IPAD_RETINA(
			"ipad_retina",
			1252,
			626),
	MOBILE(
			"mobile",
			320,
			160),
	MOBILE_RETINA(
			"mobile_retina",
			640,
			320);

	private final String mSuffix;
	private final int mWidth;
	private final int mHeight;
	private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.*/)[a-z]+$");

	private static final String TAG = BannerImageVariant.class.getSimpleName();

	private BannerImageVariant(String suffix, int width, int height) {
		mSuffix = suffix;
		mWidth = width;
		mHeight = height;
	}

	/**
	 * Transforms a Twitter banner image URL into the URL of the desired size
	 * variant.
	 * 
	 * @param imageUrl
	 *            a Twitter profile image URL of any variant
	 * @param desiredVariant
	 *            the desired variant
	 * @return the URL of the desired image variant
	 */
	public static String getVariantUrl(String imageUrl, BannerImageVariant desiredVariant) {
		String result = null;
		if (imageUrl != null) {
			Matcher matcher = SUFFIX_PATTERN.matcher(imageUrl);
			result = matcher.replaceAll("$1" + desiredVariant.mSuffix);
			Log.d(TAG, "result: " + result + " desired variant: " + desiredVariant.name());
		}
		return result;
	}

	/**
	 * Transforms a Twitter banner image URL into the URL of the size variant
	 * that satisfies the required target with the best.
	 * 
	 * @param imageUrl
	 *            a Twitter profile image URL of any variant
	 * @param targetWidth
	 *            the minimal required width
	 * @return the URL of the desired image variant
	 */
	public static String getVariantUrl(String imageUrl, int targetWidth) {
		// get the smallest variant that has at least the required width
		BannerImageVariant optimalVariant = null;
		for (BannerImageVariant variant : values()) {
			optimalVariant = getBetterVariant(optimalVariant, variant, targetWidth);
			Log.d(TAG, "optimal variant: " + optimalVariant.name());
		}
		return getVariantUrl(imageUrl, optimalVariant);
	}

	private static BannerImageVariant getBetterVariant(BannerImageVariant a, BannerImageVariant b, int targetWidth) {
		if (a == null) {
			return b;
		} else if (b == null) {
			return a;
		} else if (a.mWidth >= targetWidth && b.mWidth >= targetWidth) {
			// both bigger than target? -> get smaller one
			return a.mWidth < b.mWidth ? a : b;
		} else if (a.mWidth >= targetWidth && b.mWidth < targetWidth) {
			// only a is bigger than target -> get a
			return a;
		} else if (a.mWidth < targetWidth && b.mWidth >= targetWidth) {
			// only b is bigger than target -> get b
			return b;
		} else {
			// both are smaller -> get the bigger one
			return a.mWidth > b.mWidth ? a : b;
		}
	}
}