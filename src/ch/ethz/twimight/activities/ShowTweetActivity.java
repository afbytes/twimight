package ch.ethz.twimight.activities;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import ch.ethz.twimight.R;
import ch.ethz.twimight.fragments.adapters.TweetPageAdapter;
import ch.ethz.twimight.net.twitter.Tweets;

public class ShowTweetActivity extends TwimightBaseActivity {
	
	ViewPager viewPager;
	ContentResolver resolver;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		resolver = getContentResolver();
		Intent intent = getIntent();
		long rowId = intent.getIntExtra("rowId", 0);
		int type = intent.getIntExtra("type", ShowTweetListActivity.TIMELINE_KEY);
		
		ArrayList<Long> rowIdList = getRowIds(type);
		
		TweetPageAdapter pageAdapter = new TweetPageAdapter(getFragmentManager(), rowIdList );		
		viewPager = (ViewPager)  findViewById(R.id.viewpager);			
		viewPager.setAdapter(pageAdapter);
		viewPager.setOffscreenPageLimit(2);
		viewPager.setCurrentItem(rowIdList.indexOf(rowId));
	}

	private ArrayList<Long> getRowIds(int type) {
		Cursor c ;
		ArrayList<Long> list=null;
		
		c= performQuery(type);
		
		if (c != null && c.getCount() >0){
			
			c.moveToFirst();
			list = cursorToList(c);
		}
		return list;
		
		
	}

	private ArrayList<Long> cursorToList(Cursor c) {
		ArrayList<Long> list = new ArrayList<Long>();
		
		while(c.isAfterLast() != true) {
			long id = c.getLong(c.getColumnIndex("_id"));
			list.add(id);
			c.moveToNext();
		}
		return list;
	}

	private Cursor performQuery(int type) {
		Cursor c= null;
		
		switch(type) {
		case ShowTweetListActivity.TIMELINE_KEY:

			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
					+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);

			break;
		case ShowTweetListActivity.FAVORITES_KEY:			
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_FAVORITES + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			break;
		case ShowTweetListActivity.MENTIONS_KEY: 			
			c = resolver.query(Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" 
					+ Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.TWEETS_SOURCE_ALL), null, null, null, null);
			break;
		}
		return c;
		
	}

	

}
