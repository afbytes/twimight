package ch.ethz.twimight.ui;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import ch.ethz.twimight.R;

public class PullToRefreshListView extends LinearLayout {

//	private static final String TAG = PullToRefreshListView.class.getName();

	/**
	 * Interface to be implemented by refresh event receivers.
	 * 
	 * @author msteven
	 * 
	 */
	public interface PullToRefreshListener {
		public void onTopRefresh();

		public void onBottomRefresh();
	}

	private enum State {
		SCROLL_LIST(0), PULL_TOP_STAGE_1(1), PULL_TOP_STAGE_2(1), PULL_BOTTOM_STAGE_1(-1), PULL_BOTTOM_STAGE_2(-1);

		private final int mDirectionFactor;

		private State(int directionFactor) {
			mDirectionFactor = directionFactor;
		}

		public int getDirectionFactor() {
			return mDirectionFactor;
		}
	}

	// private static final String TAG = "PullToRefreshListView2";

	private ListView mListView;
	private State mState = State.SCROLL_LIST;
	private float mPullStartY;
	private float mLastTouchY;
	private boolean mIsOverscrollEnabled = true;

	private RotateAnimation mHalfRotationCcw;
	private RotateAnimation mHalfRotationCcwReverse;
	private RotateAnimation mHalfRotationCw;
	private RotateAnimation mHalfRotationCwReverse;

	private View mTopRefreshView;
	private TextView mTopRefreshViewText;
	private ImageView mTopRefreshViewIcon;

	private View mBottomRefreshView;
	private TextView mBottomRefreshViewText;
	private ImageView mBottomRefreshViewIcon;

	private static final float FRICTION_STAGE_1 = 2f;
	private static final float FRICTION_STAGE_2 = 8f;
	private static final float STAGE_1_LENGTH = 200;
	private static final int ANIMATION_DURATION_MS = 100;
	private static final float MAX_ABS_PULL_DISTANCE_STAGE_1 = STAGE_1_LENGTH * FRICTION_STAGE_1;

	private final Set<PullToRefreshListener> mListeners = new HashSet<PullToRefreshListener>();

	public PullToRefreshListView(Context context) {
		super(context);
		initialize();
	}

	public PullToRefreshListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize();
	}

	public PullToRefreshListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize();
	}

	private void initialize() {
		setOrientation(VERTICAL);
		setBackgroundColor(getResources().getColor(R.color.list_background));
		// animations
		mHalfRotationCcw = new RotateAnimation(0, -180, RotateAnimation.RELATIVE_TO_SELF, 0.5f,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		mHalfRotationCcw.setInterpolator(new LinearInterpolator());
		mHalfRotationCcw.setDuration(ANIMATION_DURATION_MS);
		mHalfRotationCcw.setFillAfter(true);
		mHalfRotationCcwReverse = new RotateAnimation(-180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		mHalfRotationCcwReverse.setInterpolator(new LinearInterpolator());
		mHalfRotationCcwReverse.setDuration(ANIMATION_DURATION_MS);
		mHalfRotationCcwReverse.setFillAfter(true);
		mHalfRotationCw = new RotateAnimation(0, 180, RotateAnimation.RELATIVE_TO_SELF, 0.5f,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		mHalfRotationCw.setInterpolator(new LinearInterpolator());
		mHalfRotationCw.setDuration(ANIMATION_DURATION_MS);
		mHalfRotationCw.setFillAfter(true);
		mHalfRotationCwReverse = new RotateAnimation(180, 0, RotateAnimation.RELATIVE_TO_SELF, 0.5f,
				RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		mHalfRotationCwReverse.setInterpolator(new LinearInterpolator());
		mHalfRotationCwReverse.setDuration(ANIMATION_DURATION_MS);
		mHalfRotationCwReverse.setFillAfter(true);
		// list view
		mListView = new ListView(getContext());
		mListView.setBackgroundDrawable(null);
		mListView.setFastScrollEnabled(true);

		addView(mListView, -1, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		// top refresh view
		LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mTopRefreshView = inflater.inflate(R.layout.pull_to_refresh_header, this, false);
		mTopRefreshViewText = (TextView) mTopRefreshView.findViewById(R.id.pull_to_refresh_text);
		mTopRefreshViewIcon = (ImageView) mTopRefreshView.findViewById(R.id.pull_to_refresh_image);
		mTopRefreshViewIcon.setImageResource(R.drawable.ic_pull_down);
		addView(mTopRefreshView, 0, new LayoutParams(LayoutParams.MATCH_PARENT, (int) STAGE_1_LENGTH));
		// bottom refresh view
		mBottomRefreshView = inflater.inflate(R.layout.pull_to_refresh_header, this, false);
		mBottomRefreshViewText = (TextView) mBottomRefreshView.findViewById(R.id.pull_to_refresh_text);
		mBottomRefreshViewIcon = (ImageView) mBottomRefreshView.findViewById(R.id.pull_to_refresh_image);
		mBottomRefreshViewIcon.setImageResource(R.drawable.ic_pull_up);
		addView(mBottomRefreshView, new LayoutParams(LayoutParams.MATCH_PARENT, (int) STAGE_1_LENGTH));
		// push header and footer out of sight
		setPadding(getPaddingLeft(), -(int) STAGE_1_LENGTH, getPaddingRight(), 0);
	}

	public void registerListener(PullToRefreshListener listener) {
		mListeners.add(listener);
	}

	public void unregisterListener(PullToRefreshListener listener) {
		mListeners.remove(listener);
	}

	private void topRefresh() {
		for (PullToRefreshListener listener : mListeners) {
			listener.onTopRefresh();
		}
	}

	private void bottomRefresh() {
		for (PullToRefreshListener listener : mListeners) {
			listener.onBottomRefresh();
		}
	}

	public void setFastScrollEnabled(boolean fastScrollEnabled) {
		mListView.setFastScrollEnabled(fastScrollEnabled);
	}

	/**
	 * Forwards call to internal ListView.
	 * 
	 * @param adapter
	 *            the adapter
	 */
	public void setAdapter(ListAdapter adapter) {
		mListView.setAdapter(adapter);
	}

	/**
	 * Forwards call to internal ListView.
	 * 
	 * @param listener
	 *            the listener
	 */
	public void setOnItemClickListener(AdapterView.OnItemClickListener listener) {
		mListView.setOnItemClickListener(listener);
	}

	/**
	 * Determines if the current gesture should initiate overscrolling. If so,
	 * it returns true so that we receive the subsequent touch events of the
	 * current gesture in onTouch().
	 */
	@Override
	public final boolean onInterceptTouchEvent(MotionEvent event) {
		float y = event.getY();
		switch (event.getAction()) {
		case MotionEvent.ACTION_MOVE:
			float pullDistance = Math.abs(y - mLastTouchY);
			if (mIsOverscrollEnabled && pullDistance > 0/*mTouchSlop*/) {
				if (isListAtTop() && y > mLastTouchY) {
					updateState(State.PULL_TOP_STAGE_1);
				} else if (isListAtBottom() && y < mLastTouchY) {
					updateState(State.PULL_BOTTOM_STAGE_1);
				} else {
					updateState(State.SCROLL_LIST);
				}

				mPullStartY = y;
				break;
			}
		case MotionEvent.ACTION_DOWN:
			if (isListAtTop() || isListAtBottom()) {
				mLastTouchY = y;
				updateState(State.SCROLL_LIST);
			}
			break;
		}
		return mState != State.SCROLL_LIST;
	}

	/**
	 * Updates the state and overscroll length based on the length of the
	 * current pull gesture.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float y = event.getY();
		switch (event.getAction()) {
		case MotionEvent.ACTION_MOVE:
			float pullDistance = y - mPullStartY;
			float absScrollDistance = calculateAbsScrollDistance(pullDistance);
			if (absScrollDistance > STAGE_1_LENGTH) {
				if (mState == State.PULL_TOP_STAGE_1) {
					updateState(State.PULL_TOP_STAGE_2);
				} else if (mState == State.PULL_BOTTOM_STAGE_1) {
					updateState(State.PULL_BOTTOM_STAGE_2);
				}
			} else {
				if (mState == State.PULL_TOP_STAGE_2) {
					updateState(State.PULL_TOP_STAGE_1);
				} else if (mState == State.PULL_BOTTOM_STAGE_2) {
					updateState(State.PULL_BOTTOM_STAGE_1);
				}
			}
			scrollTo(0, (int) (-absScrollDistance * mState.getDirectionFactor()));
			return true;
		case MotionEvent.ACTION_UP:
			updateState(State.SCROLL_LIST);
			break;
		}
		return false;
	}

	/**
	 * Performs state transitions with all necessary changes.
	 * 
	 * @param newState
	 *            the new state
	 */
	private void updateState(State newState) {
		switch (newState) {
		case PULL_TOP_STAGE_1:
			mTopRefreshViewText.setText(R.string.pull_to_refresh_label);
			if (mState == State.PULL_TOP_STAGE_2) {
				// PULL_TOP_STAGE_2 -> PULL_TOP_STAGE_1 (scrolled back
				// down)
				mTopRefreshViewIcon.clearAnimation();
				mTopRefreshViewIcon.startAnimation(mHalfRotationCcwReverse);
			}
			break;
		case PULL_TOP_STAGE_2:
			mTopRefreshViewText.setText(R.string.release_to_refresh_label);
			if (mState == State.PULL_TOP_STAGE_1) {
				// PULL_TOP_STAGE_1 -> PULL_TOP_STAGE_2
				mTopRefreshViewIcon.clearAnimation();
				mTopRefreshViewIcon.startAnimation(mHalfRotationCcw);
			}
			break;
		case PULL_BOTTOM_STAGE_1:
			mBottomRefreshViewText.setText(R.string.pull_to_load_more_label);
			if (mState == State.PULL_BOTTOM_STAGE_2) {
				// PULL_BOTTOM_STAGE_2 -> PULL_BOTTOM_STAGE_1 (scrolled back
				// down)
				mBottomRefreshViewIcon.clearAnimation();
				mBottomRefreshViewIcon.startAnimation(mHalfRotationCwReverse);
			}
			break;
		case PULL_BOTTOM_STAGE_2:
			mBottomRefreshViewText.setText(R.string.release_to_load_more_label);
			if (mState == State.PULL_BOTTOM_STAGE_1) {
				// PULL_BOTTOM_STAGE_1 -> PULL_BOTTOM_STAGE_2
				mBottomRefreshViewIcon.clearAnimation();
				mBottomRefreshViewIcon.startAnimation(mHalfRotationCw);
			}
			break;
		case SCROLL_LIST:
			if (mState == State.PULL_TOP_STAGE_2) {
				topRefresh();
			} else if (mState == State.PULL_BOTTOM_STAGE_2) {
				bottomRefresh();
			}
			mTopRefreshViewIcon.clearAnimation();
			mBottomRefreshViewIcon.clearAnimation();
			resetScroll();
			break;
		}

		// finally update the state
		mState = newState;
	}

	/**
	 * Aligns the listview back to the top/bottom of the parent view.
	 */
	private void resetScroll() {
		scrollTo(0, 0);
	}

	private float calculateAbsScrollDistance(float pullDistance) {
		// first clamp according to state
		float absClampedPullDistance = Math.max(0, pullDistance * mState.getDirectionFactor());

		if (absClampedPullDistance <= MAX_ABS_PULL_DISTANCE_STAGE_1) {
			return absClampedPullDistance / FRICTION_STAGE_1;
		} else {
			return STAGE_1_LENGTH + (absClampedPullDistance - MAX_ABS_PULL_DISTANCE_STAGE_1) / FRICTION_STAGE_2;
		}
	}

	/**
	 * Determines if the list is scrolled to the top.
	 * 
	 * @return true if the list is scrolled all the way up
	 */
	private boolean isListAtTop() {
		if (mListView.getFirstVisiblePosition() == 0) {
			View firstItem = mListView.getChildAt(0);
			if (firstItem != null) {
				int firstItemTop = firstItem.getTop();
				int listTop = mListView.getTop();
				return firstItemTop >= listTop;
			}
		}
		return false;
	}

	/**
	 * Determines if the list is scrolled to the bottom.
	 * 
	 * @return true if the list is scrolled all the way down
	 */
	private boolean isListAtBottom() {
		// if list is empty we are at the bottom
		if (mListView.getCount() == 0) {
			return true;
		}
		if (mListView.getLastVisiblePosition() == mListView.getCount() - 1) {
			int lastItemBottom = mListView.getChildAt(
					mListView.getLastVisiblePosition() - mListView.getFirstVisiblePosition()).getBottom();
			int listBottom = mListView.getBottom();
			return lastItemBottom <= listBottom;
		} else {
			return false;
		}
	}

	public void setOverscrollEnabled(boolean enabled) {
		mIsOverscrollEnabled = enabled;
	}
}