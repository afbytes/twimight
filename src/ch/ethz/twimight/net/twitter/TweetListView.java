package ch.ethz.twimight.net.twitter;

import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ListView;

public class TweetListView extends ListView {
	private final String TAG = "TweetListView";
	private int maxOverscroll = 100;
	private int curOverscroll = 0;
	private Intent overscrollIntent;
	private Context context;

	public TweetListView(Context context) {
		super(context);
		setOverScrollMode(OVER_SCROLL_ALWAYS);
		this.context = context;
	}

	public TweetListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOverScrollMode(OVER_SCROLL_ALWAYS);
		this.context = context;
	}
	
	public void setOverscrollIntent(Intent i){
		Log.e(TAG, "SEEEEEEEEEEEET");
		overscrollIntent = i;
	}

	private void sendOverscrollIntent(){
		Log.v(TAG, "send?");
		if(overscrollIntent!=null && context!=null) {
			context.startService(overscrollIntent);
			Log.v(TAG, "intent sent!");
		}
	}
	
	
	/*
	 * 
	 *
	private void initBounceListView()
	{
		//get the density of the screen and do some maths with it on the max overscroll distance
		//variable so that you get similar behaviors no matter what the screen size
		
		final DisplayMetrics metrics = mContext.getResources().getDisplayMetrics();
        	final float density = metrics.density;
        
		mMaxYOverscrollDistance = (int) (density * MAX_Y_OVERSCROLL_DISTANCE);
	}
	*
	*/

	@Override
	protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {

		return super.overScrollBy(0, deltaY, 0, scrollY, 0, scrollRangeY, 0, maxOverscroll, isTouchEvent);

	}

	@Override
	protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {

		// did we reach the max just now?
		if(curOverscroll>(-maxOverscroll) && scrollY==(-maxOverscroll)){
			Log.v(TAG, "now!!");
			sendOverscrollIntent();
		}
		curOverscroll=scrollY;
		//Log.v(TAG, "scrollX:" + scrollX + " scrollY:" + scrollY + " clampedX:" + clampedX + " clampedY:" + clampedX);

		super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);

	}
}