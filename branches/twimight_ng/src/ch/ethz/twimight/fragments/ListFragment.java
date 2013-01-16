package ch.ethz.twimight.fragments;


import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.ShowTweetActivity;
import ch.ethz.twimight.net.twitter.TweetListView;

public abstract class ListFragment extends Fragment {
	
	Cursor c;
	Intent overscrollIntent ;
	int type ;
	ContentResolver resolver;
		
	
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
		Log.i("ListFragment","inside on create view");
		resolver = getActivity().getContentResolver();
        // Inflate the layout for this fragment	
	    View view = inflater.inflate(R.layout.fragment_layout, container, false);
		TweetListView list = (TweetListView) view.findViewById(R.id.tweetListView);
		
		list.setAdapter(getData(type));
		list.setOverscrollIntent(overscrollIntent);
		
		// Click listener when the user clicks on a tweet
		list.setClickable(true);
		list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
				Cursor c = (Cursor) arg0.getItemAtPosition(position);
				Intent i = new Intent(getActivity(), ShowTweetActivity.class);
				i.putExtra("rowId", c.getInt(c.getColumnIndex("_id")));
				startActivity(i);
			}
		});
        return list;
        
    }	
		
	/**
	 * Which tweets do we show? Timeline, favorites, mentions?
	 * @param filter
	 */
	abstract ListAdapter getData(int filter);
	
	


}
