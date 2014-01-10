package ch.ethz.twimight.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ProgressBar;
import ch.ethz.twimight.R;

public class LoadingBar extends ProgressBar {

	public LoadingBar(Context context) {
		this(context, null);
	}

	public LoadingBar(Context context, AttributeSet attrs) {
		super(context, attrs, android.R.style.Widget_ProgressBar_Horizontal);

		TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.LoadingBar, 0, 0);
		int color = a.getColor(R.styleable.LoadingBar_color, context.getResources().getColor(R.color.accent_normalmode_1));
		a.recycle();
		LoadingBarDrawable drawable = new LoadingBarDrawable(color);

		setIndeterminateDrawable(drawable);
	}

	private static class LoadingBarDrawable extends Drawable implements Animatable {

		private static final long FRAME_DURATION = 1000 / 60;
		private final static float OFFSET_PER_FRAME = 0.01f;

		private final Interpolator mInterpolator = new AccelerateInterpolator();
		private Rect mBounds;
		private Paint mPaint;
		private int mColor;
		private boolean mRunning;
		private float mCurrentOffset;
		private final int mSeparatorLength = 4;
		private final int mSectionsCount = 4;
		private final float mSpeed = 1.0f;
		private boolean mNewTurn;

		private LoadingBarDrawable(int color) {
			mRunning = false;
			mColor = color;

			mPaint = new Paint();
			mPaint.setStyle(Paint.Style.STROKE);
			mPaint.setDither(false);
			mPaint.setAntiAlias(false);
		}

		@Override
		public void draw(Canvas canvas) {
			mBounds = getBounds();
			canvas.clipRect(mBounds);

			drawStrokes(canvas);
		}

		private void drawStrokes(Canvas canvas) {
			float prevValue = 0f;
			int boundsWidth = mBounds.width();
			mPaint.setStrokeWidth(mBounds.height());
			int width = boundsWidth + mSeparatorLength + mSectionsCount;
			int centerY = mBounds.centerY();
			float xSectionWidth = 1f / mSectionsCount;

			// new turn
			if (mNewTurn) {
				mNewTurn = false;
			}

			float prev;
			float end;
			float spaceLength;
			float xOffset;
			float ratioSectionWidth;
			float sectionWidth;
			float drawLength;

			for (int i = 0; i <= mSectionsCount; ++i) {
				xOffset = xSectionWidth * i + mCurrentOffset;
				prev = Math.max(0f, xOffset - xSectionWidth);
				ratioSectionWidth = Math.abs(mInterpolator.getInterpolation(prev)
						- mInterpolator.getInterpolation(Math.min(xOffset, 1f)));
				sectionWidth = (int) (width * ratioSectionWidth);

				if (sectionWidth + prev < width) {
					spaceLength = Math.min(sectionWidth, mSeparatorLength);
				} else {
					spaceLength = 0f;
				}

				drawLength = sectionWidth > spaceLength ? sectionWidth - spaceLength : 0;
				end = prevValue + drawLength;
				if (end > prevValue) {
					drawLine(canvas, Math.min(boundsWidth, prevValue), centerY,
							Math.min(boundsWidth, end), centerY);
				}
				prevValue = end + spaceLength;
			}
		}

		private void drawLine(Canvas canvas, float startX, float startY, float stopX, float stopY) {
			mPaint.setColor(mColor);
			canvas.drawLine(startX, startY, stopX, stopY, mPaint);
			canvas.save();
		}

		@Override
		public void setAlpha(int alpha) {
			mPaint.setAlpha(alpha);
		}

		@Override
		public void setColorFilter(ColorFilter cf) {
			mPaint.setColorFilter(cf);
		}

		@Override
		public int getOpacity() {
			return PixelFormat.TRANSPARENT;
		}

		@Override
		public void start() {
			if (!isRunning()) {
				scheduleSelf(mUpdater, SystemClock.uptimeMillis() + FRAME_DURATION);
				invalidateSelf();
			}
		}

		@Override
		public void stop() {
			if (isRunning()) {
				mRunning = false;
				unscheduleSelf(mUpdater);
			}
		}

		@Override
		public void scheduleSelf(Runnable what, long when) {
			mRunning = true;
			super.scheduleSelf(what, when);
		}

		@Override
		public boolean isRunning() {
			return mRunning;
		}

		private final Runnable mUpdater = new Runnable() {

			@Override
			public void run() {
				mCurrentOffset += (OFFSET_PER_FRAME * mSpeed);
				if (mCurrentOffset >= (1f / mSectionsCount)) {
					mNewTurn = true;
					mCurrentOffset = 0f;
				}
				scheduleSelf(mUpdater, SystemClock.uptimeMillis() + FRAME_DURATION);
				invalidateSelf();
			}
		};

	}

}
