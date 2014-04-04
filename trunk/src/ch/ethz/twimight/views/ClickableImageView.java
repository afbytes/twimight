package ch.ethz.twimight.views;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.nostra13.universalimageloader.core.ImageLoader;

public class ClickableImageView extends ImageView {
	
	private static final ColorFilter sBrightenFilter = new PorterDuffColorFilter(Color.argb(100, 255, 255, 255), Mode.LIGHTEN);
	
	public ClickableImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ClickableImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		setClickable(true);
		setOnTouchListener(new TouchEffectListener());
	}
	
	public ClickableImageView(Context context, String imageUri, Intent onClickActivityIntent){
		super(context);
		setImageUri(imageUri);
		setOnClickActivityIntent(onClickActivityIntent);
	}

	public void setImageUri(String imageUri) {
		ImageLoader.getInstance().displayImage(imageUri, this);
		setColorFilter(null);
	}

	public void setOnClickActivityIntent(Intent intent) {
		setOnClickListener(new ImageClickListener(intent));
	}

	private class ImageClickListener implements OnClickListener {

		private final Intent mIntent;

		public ImageClickListener(Intent intent) {
			mIntent = intent;
		}

		@Override
		public void onClick(View v) {
			getContext().startActivity(mIntent);
		}
	}
	
	private class TouchEffectListener implements OnTouchListener{
		
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			switch(event.getAction()){
			case MotionEvent.ACTION_DOWN:
				setColorFilter(sBrightenFilter);
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_OUTSIDE:
				setColorFilter(null);
				break;
			}
			return false;
		}
		
	}
}
