package ch.ethz.twimight.util;

import java.util.List;
import java.util.regex.Pattern;

import android.content.UriMatcher;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class ImageUrlHelper {

	private static final String TAG = ImageUrlHelper.class.getSimpleName();

	private static final String[] sImageSuffixes = new String[] { ".jpg", ".jpeg", ".png", ".gif" };
	
	private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
	
	private static final String sUrlSeparator = "|";
	private static final Pattern sSeparatorPattern = Pattern.compile(Pattern.quote(sUrlSeparator));

	private static final int TYPE_INSTAGRAM = 1;
	private static final int TYPE_IMGUR = 2;
	private static final int TYPE_TWITPIC = 3;
	private static final int TYPE_YFROG = 4;

	static {
		sUriMatcher.addURI("instagr.am", "p/*", TYPE_INSTAGRAM);
		sUriMatcher.addURI("instagram.com", "p/*", TYPE_INSTAGRAM);
		sUriMatcher.addURI("imgur.com", "*", TYPE_IMGUR);
		sUriMatcher.addURI("twitpic.com", "*", TYPE_TWITPIC);
		sUriMatcher.addURI("yfrog.com", "*", TYPE_YFROG);
	}

	/**
	 * Determines the URL of an image the belongs to the given URL (e.g. the
	 * direct image URL for an Instagram link)
	 * 
	 * @param url
	 *            a URL
	 * @return the image URL or null if the URL does not belong to an image
	 */
	public static String getImageUrl(String url) {
		if (url != null) {
			Uri uri = Uri.parse(url);
			Log.d(TAG, "url=" + url);
			Log.d(TAG, "uri=" + uri);
			// first check if it's already an image URL
			for (String imageSuffix : sImageSuffixes) {
				Log.d(TAG, "last path segment: " + uri.getLastPathSegment());
				String lastSegment = uri.getLastPathSegment();
				if (lastSegment != null && lastSegment.endsWith(imageSuffix)) {
					return uri.toString();
				}
			}

			// no match so far? bring out the big guns...
			int type = sUriMatcher.match(uri);

			switch (type) {
			case TYPE_INSTAGRAM:
				Uri.Builder uriBuilder = uri.buildUpon();
				uriBuilder.appendPath("media");
				uriBuilder.appendQueryParameter("size", "l");
				return uriBuilder.toString();
			case TYPE_IMGUR:
				return uri.toString() + ".jpg";
			case TYPE_TWITPIC:
				return "http://twitpic.com/show/full/" + uri.getLastPathSegment();
			case TYPE_YFROG:
				return uri.toString() + ":medium";
			}
		}

		return null;
	}
	
	public static String serializeUrlList(List<String> urls){
		return TextUtils.join(sUrlSeparator, urls);
	}
	
	public static String[] deserializeUrlList(String urls){
		return TextUtils.split(urls, sSeparatorPattern);
	}
}
