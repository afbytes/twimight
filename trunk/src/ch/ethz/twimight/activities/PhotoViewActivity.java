package ch.ethz.twimight.activities;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import ch.ethz.twimight.R;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;

/**
 * 
 * @author Steven Meliopoulos
 * 
 */
public class PhotoViewActivity extends Activity {

	private ImageView mImageView;
	
	private static final String TAG = PhotoViewActivity.class.getSimpleName();

	public static final String EXTRA_KEY_IMAGE_URI = "EXTRA_KEY_IMAGE_URI";

	private String mImageUri;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.photo_view);
		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		mImageView = (ImageView) findViewById(R.id.iv_photo);
		mImageUri = getIntent().getStringExtra(EXTRA_KEY_IMAGE_URI);
		ImageLoader.getInstance().displayImage(mImageUri, mImageView);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.photo_view_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_store_image:
			ImageLoader.getInstance().loadImage(mImageUri, new ImageSaveListener());
			break;
		case android.R.id.home:
			finish();
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	private void storeImage(Bitmap bitmap) {
		File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		if (downloadDir.exists() || downloadDir.mkdirs()) {
			File targetFile = new File(downloadDir, System.currentTimeMillis() + ".jpg");
			FileOutputStream out = null;
			try {
				out = new FileOutputStream(targetFile);
				bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
			} catch (FileNotFoundException e) {
				Toast.makeText(PhotoViewActivity.this, R.string.saving_image_failed, Toast.LENGTH_LONG).show();
				e.printStackTrace();
			} finally {
				if (out != null) {
					try {
						out.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			Toast.makeText(PhotoViewActivity.this, R.string.image_saved, Toast.LENGTH_LONG).show();
		} else {
			Log.d(TAG, "mkdirs=false");
			Toast.makeText(PhotoViewActivity.this, R.string.saving_image_failed, Toast.LENGTH_LONG).show();
		}
	}

	private class ImageSaveListener implements ImageLoadingListener {

		@Override
		public void onLoadingStarted(String imageUri, View view) {
			// nothing
		}

		@Override
		public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
			Toast.makeText(PhotoViewActivity.this, R.string.loading_image_failed, Toast.LENGTH_LONG).show();
		}

		@Override
		public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
			storeImage(loadedImage);
		}

		@Override
		public void onLoadingCancelled(String imageUri, View view) {
			// nothing
		}

	}

}
