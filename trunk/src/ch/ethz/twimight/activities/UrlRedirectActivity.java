package ch.ethz.twimight.activities;

import java.util.List;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * Is launched via intent filter for twitter.com urls and starts the
 * corresponding activities.
 * 
 * @author Steven Meliopoulos
 * 
 */
public class UrlRedirectActivity extends Activity {

	private static final String TAG = UrlRedirectActivity.class.getName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		finish();
		Uri uri = getIntent().getData();
		List<String> segments = uri.getPathSegments();

		Log.d(TAG, "uri: " + uri);
		Log.d(TAG, "" + segments.size() + " segments");

		for (String segment : segments) {
			Log.d(TAG, segment);
		}
		Log.d(TAG, "==========================");

		if (segments.isEmpty()) {
			launchTimeline();
		} else if (segments.get(0).equals("favorites")) {
			launchFavorites();
		} else if (segments.get(0).equals("mentions")) {
			launchMentions();
		} else if (segments.get(0).equals("following")) {
			launchFollowing();
		} else if (segments.get(0).equals("followers")) {
			launchFollowers();
		} else if (segments.get(0).equals("messages")) {
			launchMessages();
		} else if (segments.get(0).equals("search")) {
			launchSearch();
		} else if (segments.size() == 3 && segments.get(1).equals("status")) {
			launchTweet();
		} else if (segments.size() == 1) {
			launchProfile();
		}
		finish();
	}

	private void launchTimeline() {
		Intent intent = new Intent(this, HomeScreenActivity.class);
		intent.putExtra(HomeScreenActivity.EXTRA_KEY_INITIAL_TAB, HomeScreenActivity.EXTRA_INITIAL_TAB_TIMELINE);
		startActivity(intent);
	}

	private void launchFavorites() {
		Intent intent = new Intent(this, HomeScreenActivity.class);
		intent.putExtra(HomeScreenActivity.EXTRA_KEY_INITIAL_TAB, HomeScreenActivity.EXTRA_INITIAL_TAB_FAVORITES);
		startActivity(intent);
	}

	private void launchMentions() {
		Intent intent = new Intent(this, HomeScreenActivity.class);
		intent.putExtra(HomeScreenActivity.EXTRA_KEY_INITIAL_TAB, HomeScreenActivity.EXTRA_INITIAL_TAB_MENTIONS);
		startActivity(intent);
	}

	private void launchFollowers() {
		Intent intent = new Intent(this, UserListActivity.class);
		intent.putExtra(UserListActivity.EXTRA_KEY_INITIAL_TAB, UserListActivity.EXTRA_INITIAL_TAB_FOLLOWERS);
		startActivity(intent);
	}

	private void launchFollowing() {
		Intent intent = new Intent(this, UserListActivity.class);
		intent.putExtra(UserListActivity.EXTRA_KEY_INITIAL_TAB, UserListActivity.EXTRA_INITIAL_TAB_FOLLOWING);
		startActivity(intent);
	}

	private void launchMessages() {
		Intent intent = new Intent(this, DmConversationListActivity.class);
		startActivity(intent);
	}

	private void launchSearch() {
		Uri uri = getIntent().getData();
		String query = uri.getQueryParameter("q");
		Intent intent = new Intent(this, SearchableActivity.class);
		intent.putExtra(SearchManager.QUERY, query);
		String mode = uri.getQueryParameter("mode");
		if("users".equals(mode)){
			intent.putExtra(SearchableActivity.EXTRA_KEY_INITIAL_TAB, SearchableActivity.EXTRA_INITIAL_TAB_USERS);
		}
		startActivity(intent);
	}

	private void launchTweet() {
		Uri uri = getIntent().getData();
		Long tid = Long.valueOf(uri.getPathSegments().get(2));
		Intent intent = new Intent(this, TweetDetailActivity.class);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_CONTEXT, TweetDetailActivity.EXTRA_CONTEXT_SINGLE_TWEET_TID);
		intent.putExtra(TweetDetailActivity.EXTRA_KEY_TID, tid);
		startActivity(intent);
	}

	private void launchProfile() {

	}
}
