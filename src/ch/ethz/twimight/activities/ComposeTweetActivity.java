/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/

package ch.ethz.twimight.activities;

import java.io.FileNotFoundException;
import java.io.InputStream;

import twitter4j.util.CharacterUtil;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterSyncService;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.SDCardHelper;

/**
 * The activity to write a new tweet.
 * 
 * @author thossmann
 * @author pcarta
 */
public class ComposeTweetActivity extends ThemeSelectorActivity {

	private static final String TAG = "TweetActivity";

	private boolean useLocation;
	private EditText mEtTweetText;
	private TextView mTvCharacterCounter;
	private Button mBtnSend;
	private View mPhotoPreviewContainer;
	private ImageView mPhotoPreview;

	private long isReplyTo;

	// the following are all to deal with location
	private ImageButton locationButton;

	private TextWatcher mTextWatcher;

	// uploading photos
	private static final int PICK_FROM_CAMERA = 1;
	private static final int PICK_FROM_FILE = 2;
	private String tmpPhotoPath; // path storing photos on SDcard
	private String finalPhotoPath; // path storing photos on SDcard
	private String finalPhotoName; // file name of uploaded photo
	private Uri tmpPhotoUri; // uri storing temp photos
	private Uri photoUri; // uri storing photos
	private ImageView mImageView; // to display the photo to be uploaded

	private boolean hasMedia = false;
	private Bitmap photo = null;

	// SDcard helper
	private SDCardHelper sdCardHelper;

	// LOGS
	LocationHelper locHelper;
	long timestamp;
	ConnectivityManager cm;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.compose_tweet);

		// find views
		mPhotoPreviewContainer = findViewById(R.id.photoPreviewContainer);
		mPhotoPreviewContainer.setVisibility(View.GONE);
		mPhotoPreview = (ImageView) findViewById(R.id.ivPhotoPreview);
		mBtnSend = (Button) findViewById(R.id.tweet_send);
		// User settings: do we use location or not?

		useLocation = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefUseLocation",
				Constants.TWEET_DEFAULT_LOCATION);

		locationButton = (ImageButton) findViewById(R.id.tweet_location);

		if (useLocation) {
			locationButton.setImageResource(R.drawable.ic_location_on);
		} else {
			locationButton.setImageResource(R.drawable.ic_location_off);
		}
		// get username and picture
		Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
		Cursor c = getContentResolver().query(uri, null,
				TwitterUsers.COL_TWITTERUSER_ID + "=" + LoginActivity.getTwitterId(this), null, null);
		c.moveToFirst();

		if (!c.isNull(c.getColumnIndex(TwitterUsers.COL_SCREENNAME))) {
			int userId = c.getInt(c.getColumnIndex("_id"));
			Uri imageUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS + "/" + userId);
			InputStream is;
			ImageView ivProfileImage = (ImageView) findViewById(R.id.ivProfileImage);
			try {
				is = getContentResolver().openInputStream(imageUri);
				if (is != null) {
					Bitmap bm = BitmapFactory.decodeStream(is);
					ivProfileImage.setImageBitmap(bm);
				} else
					ivProfileImage.setImageResource(R.drawable.profile_image_placeholder);
			} catch (FileNotFoundException e) {
				Log.e(TAG, "error opening input stream", e);
				ivProfileImage.setImageResource(R.drawable.profile_image_placeholder);
			}
		}
		TextView tvName = (TextView) findViewById(R.id.tvName);
		String userName = c.getString(c.getColumnIndex(TwitterUsers.COL_NAME));
		tvName.setText(userName);

		TextView tvScreenName = (TextView) findViewById(R.id.tvScreenname);
		String userScreenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME));
		tvScreenName.setText("@" + userScreenName);

		cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		locHelper = LocationHelper.getInstance(this);

		// SDCard helper
		sdCardHelper = new SDCardHelper();

		// prepare image saving

		tmpPhotoPath = Tweets.PHOTO_PATH + "/" + "tmp";
		finalPhotoPath = Tweets.PHOTO_PATH + "/" + LoginActivity.getTwitterId(this);
		String[] filePaths = { tmpPhotoPath, finalPhotoPath };
		if (sdCardHelper.checkSDState(filePaths)) {

			sdCardHelper.clearTempDirectory(tmpPhotoPath);
		}
		mImageView = new ImageView(this);
		// Did we get some extras in the intent?

		Intent i = getIntent();
		mEtTweetText = (EditText) findViewById(R.id.tweetText);
		if (i.hasExtra("text")) {
			mEtTweetText.setText(Html.fromHtml("<i>" + i.getStringExtra("text") + "</i>"));
		}
		if (mEtTweetText.getText().length() == 0) {
			mBtnSend.setEnabled(false);
		}

		mTvCharacterCounter = (TextView) findViewById(R.id.tweet_characters);
		checkTweetLength();

		if (i.hasExtra("isReplyTo")) {
			isReplyTo = i.getLongExtra("isReplyTo", 0);
		}

		// This makes sure we do not enter more than 140 characters

		mTvCharacterCounter.setText(Integer.toString(Constants.TWEET_LENGTH));
		mTextWatcher = new TextWatcher() {
			public void afterTextChanged(Editable s) {
				checkTweetLength();
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
		};
		mEtTweetText.addTextChangedListener(mTextWatcher);
		mEtTweetText.setSelection(mEtTweetText.getText().length());

	}

	private void checkTweetLength() {
		int usedTextChars = CharacterUtil.count(mEtTweetText.getText().toString());
		int usedMediaChars = hasMedia ? 23 : 0;
		int numCharsLeft = Constants.TWEET_LENGTH - usedTextChars - usedMediaChars;

		if (numCharsLeft < 0) {
			mEtTweetText.setText(mEtTweetText.getText().subSequence(0, Constants.TWEET_LENGTH - usedMediaChars));
			mEtTweetText.setSelection(mEtTweetText.getText().length());
			usedTextChars = CharacterUtil.count(mEtTweetText.getText().toString());
			numCharsLeft = Constants.TWEET_LENGTH - usedTextChars - usedMediaChars;
		}

		if (numCharsLeft <= 0) {
			mTvCharacterCounter.setTextColor(Color.RED);
		} else {
			mTvCharacterCounter.setTextColor(getResources().getColor(R.color.medium_gray));
		}

		if (numCharsLeft == Constants.TWEET_LENGTH) {
			mBtnSend.setEnabled(false);
		} else {
			mBtnSend.setEnabled(true);
		}

		mTvCharacterCounter.setText(Integer.toString(numCharsLeft));
	}

	@Override
	protected void setDisasterTheme() {
		setTheme(R.style.TwimightHolo_DisasterMode_Translucent);
	}

	@Override
	protected void setNormalTheme() {
		setTheme(R.style.TwimightHolo_NormalMode_Translucent);
	}

	public void previewPhoto(View unused) {
		mImageView = new ImageView(ComposeTweetActivity.this);
		mImageView.setImageBitmap(photo);
		Intent intent = new Intent(this, PhotoViewActivity.class);
		intent.putExtra(PhotoViewActivity.PHOTO_PATH_EXTRA, tmpPhotoUri.getPath());
		startActivity(intent);
	}

	public void cancel(View unused) {
		finish();
	}

	public void deletePhoto(View unused) {
		sdCardHelper.deleteFile(tmpPhotoUri.getPath());
		hasMedia = false;
		mPhotoPreviewContainer.setVisibility(View.GONE);
		triggerCharacterCounter();
	}

	private void triggerCharacterCounter() {
		mEtTweetText.setText(mEtTweetText.getText());
	}

	public void sendTweet(View unused) {
		new SendTweetTask().execute();
	}

	public void toggleLocation(View unused) {
		if (useLocation) {
			locHelper.unRegisterLocationListener();
			Toast.makeText(ComposeTweetActivity.this, getString(R.string.location_off), Toast.LENGTH_SHORT).show();
			locationButton.setImageResource(R.drawable.ic_location_off);
			useLocation = false;
		} else {
			locHelper.registerLocationListener();
			Toast.makeText(ComposeTweetActivity.this, getString(R.string.location_on), Toast.LENGTH_SHORT).show();
			locationButton.setImageResource(R.drawable.ic_location_on);
			useLocation = true;
		}
	}

	/**
	 * onResume
	 */
	@Override
	public void onResume() {
		super.onResume();
		if (useLocation) {
			locHelper.registerLocationListener();
		}
	}

	/**
	 * onPause
	 */
	@Override
	public void onPause() {
		super.onPause();
		locHelper.unRegisterLocationListener();
	}

	/**
	 * On Destroy
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (hasMedia) {
			sdCardHelper.deleteFile(tmpPhotoUri.getPath());
			hasMedia = false;
		}
		if (locHelper != null)
			locHelper.unRegisterLocationListener();

		mEtTweetText.removeTextChangedListener(mTextWatcher);
		mTextWatcher = null;

		TwimightBaseActivity.unbindDrawables(findViewById(R.id.composeTweetRoot));
	}

	/**
	 * Checks whether we are in disaster mode and inserts the content values
	 * into the content provider.
	 * 
	 * @author pcarta
	 * 
	 */
	private class SendTweetTask extends AsyncTask<Void, Void, Boolean> {

		Uri insertUri = null;
		StatisticsDBHelper statsDBHelper;

		@Override
		protected Boolean doInBackground(Void... params) {
			boolean result = false;

			// Statistics
			statsDBHelper = new StatisticsDBHelper(getApplicationContext());
			statsDBHelper.open();
			timestamp = System.currentTimeMillis();

			if (hasMedia) {
				try {
					finalPhotoName = "twimight" + String.valueOf(timestamp) + ".jpg";
					photoUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(finalPhotoPath, finalPhotoName));// photoFileParent,
																											// photoFilename));
					String fromFile = tmpPhotoUri.getPath();
					String toFile = photoUri.getPath();
					if (TwimightBaseActivity.D)
						Log.i(TAG, fromFile);
					if (TwimightBaseActivity.D)
						Log.i(TAG, toFile);
					if (sdCardHelper.copyFile(fromFile, toFile)) {

						if (TwimightBaseActivity.D)
							Log.i(TAG, "file copy successful");

					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					if (TwimightBaseActivity.D)
						Log.d("photo", "exception!!!!!");
					e.printStackTrace();
				}
			}
			// if no connectivity, notify user that the tweet will be send later

			ContentValues cv = createContentValues();

			if (PreferenceManager.getDefaultSharedPreferences(ComposeTweetActivity.this).getBoolean("prefDisasterMode",
					false) == true) {

				// our own tweets go into the my disaster tweets buffer
				cv.put(Tweets.COL_BUFFER, Tweets.BUFFER_TIMELINE | Tweets.BUFFER_MYDISASTER);

				insertUri = getContentResolver().insert(
						Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
								+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_DISASTER), cv);
				getContentResolver().notifyChange(Tweets.TABLE_TIMELINE_URI, null);
			} else {

				// our own tweets go into the timeline buffer
				cv.put(Tweets.COL_BUFFER, Tweets.BUFFER_TIMELINE);
				// we publish on twitter directly only normal tweets
				cv.put(Tweets.COL_FLAGS, Tweets.FLAG_TO_INSERT);

				insertUri = getContentResolver().insert(
						Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
								+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.TWEETS_SOURCE_NORMAL), cv);
				getContentResolver().notifyChange(Tweets.TABLE_TIMELINE_URI, null);
				// getContentResolver().notifyChange(insertUri, null);
				ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				if (cm.getActiveNetworkInfo() == null || !cm.getActiveNetworkInfo().isConnected()) {
					result = true;
				}
			}
			if (locHelper.getCount() > 0 && cm.getActiveNetworkInfo() != null) {

				Log.i(TAG, "writing log");
				statsDBHelper.insertRow(locHelper.getLocation(), cm.getActiveNetworkInfo().getTypeName(),
						StatisticsDBHelper.TWEET_WRITTEN, null, timestamp);
				locHelper.unRegisterLocationListener();
				Log.i(TAG, String.valueOf(hasMedia));
			}

			return result;

		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result)
				Toast.makeText(ComposeTweetActivity.this, getString(R.string.no_connection4), Toast.LENGTH_SHORT)
						.show();

			if (insertUri != null) {
				// schedule the tweet for uploading to twitter
				Intent i = new Intent(ComposeTweetActivity.this, TwitterSyncService.class);
				i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_TWEET);
				i.putExtra(TwitterSyncService.EXTRA_TWEET_ROW_ID, Long.valueOf(insertUri.getLastPathSegment()));
				startService(i);
			}
			finish();
		}
	}

	/**
	 * Prepares the content values of the tweet for insertion into the DB.
	 * 
	 * @return
	 */
	private ContentValues createContentValues() {
		ContentValues tweetContentValues = new ContentValues();

		tweetContentValues.put(Tweets.COL_TEXT, mEtTweetText.getText().toString());
		tweetContentValues.put(Tweets.COL_TEXT_PLAIN, mEtTweetText.getText().toString());
		tweetContentValues.put(Tweets.COL_TWITTERUSER, LoginActivity.getTwitterId(this));
		tweetContentValues.put(Tweets.COL_SCREENNAME, LoginActivity.getTwitterScreenname(this));
		if (isReplyTo > 0) {
			tweetContentValues.put(Tweets.COL_REPLYTO, isReplyTo);
		}
		// set the current timestamp
		tweetContentValues.put(Tweets.COL_CREATED, System.currentTimeMillis());

		if (useLocation) {
			Location loc = locHelper.getLocation();
			if (loc != null) {
				tweetContentValues.put(Tweets.COL_LAT, loc.getLatitude());
				tweetContentValues.put(Tweets.COL_LNG, loc.getLongitude());
			}
		}
		// if there is a photo, put the path of photo in the cv
		if (hasMedia) {
			tweetContentValues.put(Tweets.COL_MEDIA, finalPhotoName);
			Log.i(TAG, Tweets.COL_MEDIA + ":" + finalPhotoName);
		}

		return tweetContentValues;
	}

	// methods photo uploading

	/**
	 * upload photo from camera
	 */
	public void uploadFromCamera(View unused) {

		if ((tmpPhotoUri = sdCardHelper.createTmpPhotoStoragePath(tmpPhotoPath)) != null) {
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

			intent.putExtra(MediaStore.EXTRA_OUTPUT, tmpPhotoUri);

			try {
				intent.putExtra("return-data", true);
				startActivityForResult(intent, PICK_FROM_CAMERA);
			} catch (ActivityNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			Log.i(TAG, "path for storing photos cannot be created!");
			mPhotoPreviewContainer.setVisibility(View.GONE);
		}

	}

	/**
	 * upload photo by taking a picture
	 */
	public void uploadFromGallery(View unused) {
		if ((tmpPhotoUri = sdCardHelper.createTmpPhotoStoragePath(tmpPhotoPath)) != null) {
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.picker)), PICK_FROM_FILE);
		} else {
			Log.i(TAG, "path for storing photos cannot be created!");
			mPhotoPreviewContainer.setVisibility(View.GONE);
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK)
			return;

		hasMedia = true;
		switch (requestCode) {
		case PICK_FROM_CAMERA:

			// display the picture
			photo = sdCardHelper.decodeBitmapFile(tmpPhotoUri.getPath());
			mImageView.setImageBitmap(photo);
			enablePreview();
			break;

		case PICK_FROM_FILE:

			// display the photo
			Uri mImageGalleryUri = data.getData();

			// get the real path for chosen photo
			mImageGalleryUri = Uri.parse(sdCardHelper.getRealPathFromUri((Activity) ComposeTweetActivity.this,
					mImageGalleryUri));

			// copy the photo from gallery to tmp directory

			String fromFile = mImageGalleryUri.getPath();
			String toFile = tmpPhotoUri.getPath();
			if (sdCardHelper.copyFile(fromFile, toFile)) {
				photo = sdCardHelper.decodeBitmapFile(toFile);
				mImageView.setImageBitmap(photo);
				enablePreview();
			}
			break;
		}
	}

	private void enablePreview() {
		mPhotoPreview.setImageBitmap(photo);
		mPhotoPreviewContainer.setVisibility(View.VISIBLE);
		triggerCharacterCounter();
	}
}
