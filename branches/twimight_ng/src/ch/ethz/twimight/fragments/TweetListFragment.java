package ch.ethz.twimight.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import ch.ethz.twimight.activities.SearchableActivity;
import ch.ethz.twimight.activities.ShowTweetActivity;
import ch.ethz.twimight.activities.ShowTweetListActivity;
import ch.ethz.twimight.activities.ShowUserTweetListActivity;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService;

public class TweetListFragment extends ListFragment {	
	
	long userId;	
	
	public TweetListFragment(){
		
	};
	
	public TweetListFragment(int type) {
		super();
		this.type=type;
		
		
	}    

	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.i("TweetListFragment","onCreate");
		if (savedInstanceState != null) {
			Log.i("TweetListFragment","bundle not null");
			if (type == SearchableActivity.SHOW_SEARCH_TWEETS)
				query = savedInstanceState.getString(ListFragment.EXTRA_DATA);
			else if (type == ShowUserTweetListActivity.USER_TWEETS_KEY)
				userId = savedInstanceState.getLong(ListFragment.EXTRA_DATA);
		}

		
			
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		
		Log.i("TweetListFragment","onCreateView");
		ListView list = (ListView)super.onCreateView(inflater, container, savedInstanceState);
		// Click listener when the user clicks on a tweet
		list.setClickable(true);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long id) {
				Cursor c = (Cursor) arg0.getItemAtPosition(position);
				Intent i = new Intent(getActivity(), ShowTweetActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				i.putExtra("type", type);
				startActivity(i);
			}
		});
		
		return list;
	}

	/**
	 * Which tweets do we show? Timeline, favorites, mentions?
	 * @param filter
	 */
	ListAdapter getData(int filter){
		// set all header button colors to transparent
	
		if(c!=null) c.close();
		overscrollIntent = new Intent(getActivity(), TwitterService.class); 
		Log.i("TweetListFragment","inside getData");
		switch(filter) {
		case ShowTweetListActivity.TIMELINE_KEY: 

			//b = timelineButton;
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
					+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


			break;
		case ShowTweetListActivity.FAVORITES_KEY: 

			//b = favoritesButton;			
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


			break;
		case ShowTweetListActivity.MENTIONS_KEY: 

			//b = mentionsButton;			
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
					+ Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


			break;
		case SearchableActivity.SHOW_SEARCH_TWEETS:
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
					+ Tweets.SEARCH), null, query , null, null);
			break;
			
		case ShowUserTweetListActivity.USER_TWEETS_KEY:
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS +
					"/" + Tweets.TWEETS_TABLE_USER + "/" + userId), null, SearchableActivity.query, null, null);
			break;
		default:

			//b= timelineButton;			
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


		}	
		
		return new TweetAdapter(getActivity(), c);	

		
	}
	
	

}
