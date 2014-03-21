package ch.ethz.twimight.activities;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

	private static final Set<String> sNonUsernames = new HashSet<String>();
	
	static{
		// reserved usernames
		// https://dev.twitter.com/docs/api/1.1/get/help/configuration
		sNonUsernames.add("about");
		sNonUsernames.add("account");
		sNonUsernames.add("accounts");
		sNonUsernames.add("activity");
		sNonUsernames.add("all");
		sNonUsernames.add("announcements");
		sNonUsernames.add("anywhere");
		sNonUsernames.add("api_rules");
		sNonUsernames.add("api_terms");
		sNonUsernames.add("apirules");
		sNonUsernames.add("apps");
		sNonUsernames.add("auth");
		sNonUsernames.add("badges");
		sNonUsernames.add("blog");
		sNonUsernames.add("business");
		sNonUsernames.add("buttons");
		sNonUsernames.add("contacts");
		sNonUsernames.add("devices");
		sNonUsernames.add("direct_messages");
		sNonUsernames.add("download");
		sNonUsernames.add("downloads");
		sNonUsernames.add("edit_announcements");
		sNonUsernames.add("faq");
		sNonUsernames.add("favorites");
		sNonUsernames.add("find_sources");
		sNonUsernames.add("find_users");
		sNonUsernames.add("followers");
		sNonUsernames.add("following");
		sNonUsernames.add("friend_request");
		sNonUsernames.add("friendrequest");
		sNonUsernames.add("friends");
		sNonUsernames.add("goodies");
		sNonUsernames.add("help");
		sNonUsernames.add("home");
		sNonUsernames.add("im_account");
		sNonUsernames.add("inbox");
		sNonUsernames.add("invitations");
		sNonUsernames.add("invite");
		sNonUsernames.add("jobs");
		sNonUsernames.add("list");
		sNonUsernames.add("login");
		sNonUsernames.add("logo");
		sNonUsernames.add("logout");
		sNonUsernames.add("me");
		sNonUsernames.add("mentions");
		sNonUsernames.add("messages");
		sNonUsernames.add("mockview");
		sNonUsernames.add("newtwitter");
		sNonUsernames.add("notifications");
		sNonUsernames.add("nudge");
		sNonUsernames.add("oauth");
		sNonUsernames.add("phoenix_search");
		sNonUsernames.add("positions");
		sNonUsernames.add("privacy");
		sNonUsernames.add("public_timeline");
		sNonUsernames.add("related_tweets");
		sNonUsernames.add("replies");
		sNonUsernames.add("retweeted_of_mine");
		sNonUsernames.add("retweets");
		sNonUsernames.add("retweets_by_others");
		sNonUsernames.add("rules");
		sNonUsernames.add("saved_searches");
		sNonUsernames.add("search");
		sNonUsernames.add("sent");
		sNonUsernames.add("settings");
		sNonUsernames.add("share");
		sNonUsernames.add("signup");
		sNonUsernames.add("signin");
		sNonUsernames.add("similar_to");
		sNonUsernames.add("statistics");
		sNonUsernames.add("terms");
		sNonUsernames.add("tos");
		sNonUsernames.add("translate");
		sNonUsernames.add("trends");
		sNonUsernames.add("tweetbutton");
		sNonUsernames.add("twttr");
		sNonUsernames.add("update_discoverability");
		sNonUsernames.add("users");
		sNonUsernames.add("welcome");
		sNonUsernames.add("who_to_follow");
		sNonUsernames.add("widgets");
		sNonUsernames.add("zendesk_auth");
		sNonUsernames.add("media_signup");
	}
	
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
		} else if (segments.size() == 1 && !sNonUsernames.contains(segments.get(0))) {
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
		Uri uri = getIntent().getData();
		Log.d(TAG, "launching profile for " + uri.getPathSegments().get(0));
		Intent intent = new Intent(this, UserProfileActivity.class);
		intent.putExtra(UserProfileActivity.EXTRA_KEY_SCREEN_NAME, uri.getPathSegments().get(0));
		startActivity(intent);
	}
}
