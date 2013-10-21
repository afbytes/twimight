package ch.ethz.twimight.activities;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.util.SDCardHelper;

public class PhotoViewActivity extends Activity {

	private ImageView mImageView;
	public static final String PHOTO_PATH_EXTRA = "photo_path_extra";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.photo_view);
		mImageView = (ImageView) findViewById(R.id.iv_photo);
		
		String photoPath = getIntent().getStringExtra(PHOTO_PATH_EXTRA);
		
		SDCardHelper sdCardHelper = new SDCardHelper();
		Bitmap photo = sdCardHelper.decodeBitmapFile(photoPath);
		mImageView.setImageBitmap(photo);
	}
}
