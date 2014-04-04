package ch.ethz.twimight.activities;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import ch.ethz.twimight.R;

import com.nostra13.universalimageloader.core.ImageLoader;

public class PhotoViewActivity extends Activity {

	private ImageView mImageView;
	
	public static final String EXTRA_KEY_IMAGE_URI = "EXTRA_KEY_IMAGE_URI";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.photo_view);
		mImageView = (ImageView) findViewById(R.id.iv_photo);
		
		String imageUri = getIntent().getStringExtra(EXTRA_KEY_IMAGE_URI);
		ImageLoader.getInstance().displayImage(imageUri, mImageView);
		
	}
	
}
