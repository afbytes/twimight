package ch.ethz.twimight.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.twitter.TweetAdapter;
import ch.ethz.twimight.net.twitter.TweetListView;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService;

public class TweetListFragment extends ListFragment {	
	
	
	public static final int SHOW_TIMELINE = 10;
	public static final int SHOW_FAVORITES = 20;
	public static final int SHOW_MENTIONS = 30;	
	
		
	public TweetListFragment(){};
	
	public TweetListFragment(Activity activity, String tag) {
		super();
		type = SHOW_TIMELINE;
		
		if (tag.equals("favorites"))
			type = this.SHOW_FAVORITES;
		else if (tag.equals("mentions"))
			type = this.SHOW_MENTIONS;
	}



	/**
	 * Which tweets do we show? Timeline, favorites, mentions?
	 * @param filter
	 */
	ListAdapter getData(int filter){
		// set all header button colors to transparent
		
		if(c!=null) c.close();
		overscrollIntent = new Intent(getActivity(), TwitterService.class); 

		switch(filter) {
		case SHOW_TIMELINE: 

			//b = timelineButton;
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
					+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


			break;
		case SHOW_FAVORITES: 

			//b = favoritesButton;			
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_FAVORITES);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


			break;
		case SHOW_MENTIONS: 

			//b = mentionsButton;			
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_MENTIONS);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
					+ Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


			break;
		default:

			//b= timelineButton;			
			overscrollIntent.putExtra("synch_request", TwitterService.SYNCH_TIMELINE);
			overscrollIntent.putExtra(TwitterService.FORCE_FLAG, true);
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);


		}

		
		Log.i("TLF",overscrollIntent.toString());
		return new TweetAdapter(getActivity(), c);	

		
	}
	
	

}
