package ch.ethz.twimight.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.widget.ImageView;

public class AsyncImageLoader {

	private final Map<Uri, Bitmap> mBitmapCache = new HashMap<Uri, Bitmap>();
	private final Map<Uri, List<ImageView>> mImageViewsByUri = new HashMap<Uri, List<ImageView>>();
	private final Context mContext;

	public AsyncImageLoader(Context context) {
		mContext = context;
	}

	/**
	 * Loads the image at the specified URI and sets it to the ImageView.
	 * 
	 * @param uri
	 *            the uri of the image
	 * @param imageView
	 *            the image view to assign the image to
	 */
	public void loadImage(Uri uri, ImageView imageView) {
		Bitmap bitmap = mBitmapCache.get(uri);
		if (bitmap != null) {
			// cache hit --> set bitmap and be done
			imageView.setImageBitmap(bitmap);
			imageView.setBackgroundColor(Color.TRANSPARENT);
		} else {
			// cache miss --> start loading
			List<ImageView> imageViewsforUri = mImageViewsByUri.get(uri);
			if (imageViewsforUri == null) {
				imageViewsforUri = new LinkedList<ImageView>();
				mImageViewsByUri.put(uri, imageViewsforUri);
				// if no ImageViews have been added for this URI that means
				// there is also no loader task yet -> start one
				new BitmapLoadTask(uri).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
			}
			imageView.setTag(uri);
			imageViewsforUri.add(imageView);
		}
	}

	private class BitmapLoadTask extends AsyncTask<Void, Void, Bitmap> {

		private final Uri mUri;

		private BitmapLoadTask(Uri uri) {
			mUri = uri;
		}

		@Override
		protected Bitmap doInBackground(Void... unused) {
			InputStream inputStream = null;
			Bitmap bitmap = null;
			try {
				inputStream = mContext.getContentResolver().openInputStream(mUri);
				bitmap = BitmapFactory.decodeStream(inputStream);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} finally {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			List<ImageView> imageViews = mImageViewsByUri.remove(mUri);
			if (bitmap != null) {
				mBitmapCache.put(mUri, bitmap);
				if (imageViews != null) {
					for (ImageView imageView : imageViews) {
						Uri tagUri = (Uri) imageView.getTag();
						if (mUri.equals(tagUri)) {
							imageView.setImageBitmap(bitmap);
							imageView.setBackgroundColor(Color.TRANSPARENT);
						}
					}
				}
			}
		}

	}
}
