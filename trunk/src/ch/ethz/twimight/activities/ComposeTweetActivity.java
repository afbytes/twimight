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

import twitter4j.util.CharacterUtil;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterSyncService;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.Preferences;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.views.ClickableImageView;

import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * The activity to write a new tweet.
 * 
 * @author thossmann
 * @author pcarta
 */
public class ComposeTweetActivity extends ThemeSelectorActivity {

	private static final String TAG = ComposeTweetActivity.class.getSimpleName();

	public static final String EXTRA_KEY_TEXT = "EXTRA_KEY_TEXT";
	public static final String EXTRA_KEY_IS_REPLY_TO = "EXTRA_KEY_IS_REPLY_TO";

	private boolean mUseLocation;
	private EditText mEtTweetText;
	private TextView mTvCharacterCounter;
	private Button mBtnSend;
	private View mPhotoPreviewContainer;
	private ClickableImageView mPhotoPreview;

	private long mIsReplyTo;

	// the following are all to deal with location
	private ImageButton mBtnLocation;

	private TextWatcher mTextWatcher;

	// uploading photos
	private static final int IMAGE_FROM_CAMERA_REQUEST_CODE = 1;
	private static final int IMAGE_FROM_FILE_REQUEST_CODE = 2;
	private String mTempPhotoPath; // path storing photos on SDcard
	private String mFinalPhotoPath; // path storing photos on SDcard
	private String mFinalPhotoName; // file name of uploaded photo
	private Uri mTempPhotoUri; // uri storing temp photos
	private Uri mPhotoUri; // uri storing photos

	private boolean mHasMedia = false;
	private Bitmap mPhotoBitmap = null;

	// SDcard helper
	private SDCardHelper mSdCardHelper;

	// LOGS
	LocationHelper mLocationHelper;
	long mTimestamp;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.compose_tweet);

		captureViews();
		hideImagePreview();

		// User settings: do we use location or not?
		mUseLocation = Preferences.getBoolean(this, R.string.pref_key_use_location, Constants.TWEET_DEFAULT_LOCATION);

		if (mUseLocation) {
			mBtnLocation.setImageResource(R.drawable.ic_location_on);
		} else {
			mBtnLocation.setImageResource(R.drawable.ic_location_off);
		}
		// get username and picture
		Uri uri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/" + TwitterUsers.TWITTERUSERS);
		Cursor c = getContentResolver().query(uri, null,
				TwitterUsers.COL_TWITTER_USER_ID + "=" + LoginActivity.getTwitterId(this), null, null);
		if (c.moveToFirst()) {

			ImageView ivProfileImage = (ImageView) findViewById(R.id.ivProfileImage);
			String profilePictureUri = c.getString(c.getColumnIndex(TwitterUsers.COL_PROFILE_IMAGE_URI));
			ImageLoader.getInstance().displayImage(profilePictureUri, ivProfileImage);

			TextView tvName = (TextView) findViewById(R.id.tvName);
			String userName = c.getString(c.getColumnIndex(TwitterUsers.COL_NAME));
			tvName.setText(userName);

			TextView tvScreenName = (TextView) findViewById(R.id.tvScreenname);
			String userScreenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREEN_NAME));
			tvScreenName.setText("@" + userScreenName);
		}

		mLocationHelper = LocationHelper.getInstance(this);

		// SDCard helper
		mSdCardHelper = new SDCardHelper();

		// prepare image saving

		mTempPhotoPath = Tweets.PHOTO_PATH + "/" + "tmp";
		mFinalPhotoPath = Tweets.PHOTO_PATH + "/" + LoginActivity.getTwitterId(this);
		String[] filePaths = { mTempPhotoPath, mFinalPhotoPath };
		if (mSdCardHelper.checkSDState(filePaths)) {
			mSdCardHelper.clearTempDirectory(mTempPhotoPath);
		}

		// Did we get some extras in the intent?
		Intent i = getIntent();
		if (i.hasExtra(EXTRA_KEY_TEXT)) {
			mEtTweetText.setText(Html.fromHtml("<i>" + i.getStringExtra(EXTRA_KEY_TEXT) + "</i>"));
		}
		if (mEtTweetText.getText().length() == 0) {
			mBtnSend.setEnabled(false);
		}

		mTvCharacterCounter = (TextView) findViewById(R.id.tweet_characters);
		checkTweetLength();

		if (i.hasExtra(EXTRA_KEY_IS_REPLY_TO)) {
			mIsReplyTo = i.getLongExtra(EXTRA_KEY_IS_REPLY_TO, 0);
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
		int usedMediaChars = mHasMedia ? Constants.CHARACTERS_RESERVED_PER_MEDIA : 0;
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

	private void captureViews() {
		mPhotoPreviewContainer = findViewById(R.id.photoPreviewContainer);
		mPhotoPreview = (ClickableImageView) findViewById(R.id.ivPhotoPreview);
		mBtnSend = (Button) findViewById(R.id.tweet_send);
		mBtnLocation = (ImageButton) findViewById(R.id.tweet_location);
		mEtTweetText = (EditText) findViewById(R.id.tweetText);
	}

	@Override
	protected void setDisasterTheme() {
		setTheme(R.style.TwimightHolo_DisasterMode_Translucent);
	}

	@Override
	protected void setNormalTheme() {
		setTheme(R.style.TwimightHolo_NormalMode_Translucent);
	}

	/**
	 * OnClick callback for the cancel button (set in xml layout)
	 * 
	 * @param unused
	 */
	public void cancel(View unused) {
		finish();
	}

	/**
	 * OnClick callback for the delete photo button (set in xml layout)
	 * 
	 * @param unused
	 */
	public void deletePhoto(View unused) {
		mSdCardHelper.deleteFile(mTempPhotoUri.getPath());
		mHasMedia = false;
		hideImagePreview();
		checkTweetLength();
	}

	public void sendTweet(View unused) {
		new SendTweetTask().execute();
	}

	public void toggleLocation(View unused) {
		if (mUseLocation) {
			mLocationHelper.unRegisterLocationListener();
			Toast.makeText(ComposeTweetActivity.this, getString(R.string.location_off), Toast.LENGTH_SHORT).show();
			mBtnLocation.setImageResource(R.drawable.ic_location_off);
			mUseLocation = false;
		} else {
			mLocationHelper.registerLocationListener();
			Toast.makeText(ComposeTweetActivity.this, getString(R.string.location_on), Toast.LENGTH_SHORT).show();
			mBtnLocation.setImageResource(R.drawable.ic_location_on);
			mUseLocation = true;
		}
	}

	/**
	 * onResume
	 */
	@Override
	public void onResume() {
		super.onResume();
		if (mUseLocation) {
			mLocationHelper.registerLocationListener();
		}
	}

	/**
	 * onPause
	 */
	@Override
	public void onPause() {
		super.onPause();
		mLocationHelper.unRegisterLocationListener();
	}

	/**
	 * On Destroy
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "onDestroy");
		if (mHasMedia) {
			mSdCardHelper.deleteFile(mTempPhotoUri.getPath());
			mHasMedia = false;
		}
		if (mLocationHelper != null) {
			mLocationHelper.unRegisterLocationListener();
		}

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

		@Override
		protected Boolean doInBackground(Void... params) {
			boolean result = false;

			// Statistics
			mTimestamp = System.currentTimeMillis();

			if (mHasMedia) {
				try {
					mFinalPhotoName = "twimight" + String.valueOf(mTimestamp) + ".jpg";
					mPhotoUri = Uri.fromFile(mSdCardHelper.getFileFromSDCard(mFinalPhotoPath, mFinalPhotoName));// photoFileParent,
					// photoFilename));
					String fromFile = mTempPhotoUri.getPath();
					String toFile = mPhotoUri.getPath();
					if (mSdCardHelper.copyFile(fromFile, toFile)) {
						if (TwimightBaseActivity.D) {
							Log.i(TAG, "file copy successful");
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			// if no connectivity, notify user that the tweet will be send later

			ContentValues cv = createContentValues();

			if (Preferences.getBoolean(ComposeTweetActivity.this, R.string.pref_key_disaster_mode, false)) {

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

			return result;

		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result)
				Toast.makeText(ComposeTweetActivity.this, getString(R.string.no_connection_upload_tweet),
						Toast.LENGTH_SHORT).show();

			if (insertUri != null) {
				// schedule the tweet for uploading to twitter
				Intent i = new Intent(ComposeTweetActivity.this, TwitterSyncService.class);
				i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_LOCAL_TWEET);
				i.putExtra(TwitterSyncService.EXTRA_KEY_TWEET_ROW_ID, Long.valueOf(insertUri.getLastPathSegment()));
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
		tweetContentValues.put(Tweets.COL_USER_TID, LoginActivity.getTwitterId(this));
		tweetContentValues.put(Tweets.COL_SCREEN_NAME, LoginActivity.getTwitterScreenname(this));
		if (mIsReplyTo > 0) {
			tweetContentValues.put(Tweets.COL_REPLY_TO_TWEET_TID, mIsReplyTo);
		}
		// set the current timestamp
		tweetContentValues.put(Tweets.COL_CREATED_AT, System.currentTimeMillis());

		if (mUseLocation) {
			Location loc = mLocationHelper.getLocation();
			if (loc != null) {
				tweetContentValues.put(Tweets.COL_LAT, loc.getLatitude());
				tweetContentValues.put(Tweets.COL_LNG, loc.getLongitude());
			}
		}
		// if there is a photo, put the path of photo in the cv
		if (mHasMedia) {
			tweetContentValues.put(Tweets.COL_MEDIA_URIS, mFinalPhotoName);
			Log.i(TAG, Tweets.COL_MEDIA_URIS + ":" + mFinalPhotoName);
		}

		return tweetContentValues;
	}

	// methods photo uploading

	/**
	 * upload photo from camera
	 */
	public void uploadFromCamera(View unused) {

		if ((mTempPhotoUri = mSdCardHelper.createTmpPhotoStoragePath(mTempPhotoPath)) != null) {
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

			intent.putExtra(MediaStore.EXTRA_OUTPUT, mTempPhotoUri);

			try {
				intent.putExtra("return-data", true);
				startActivityForResult(intent, IMAGE_FROM_CAMERA_REQUEST_CODE);
			} catch (ActivityNotFoundException e) {
				e.printStackTrace();
			}
		} else {
			Log.i(TAG, "path for storing photos cannot be created!");
			hideImagePreview();
		}

	}

	/**
	 * upload photo by taking a picture
	 */
	public void uploadFromGallery(View unused) {
		if ((mTempPhotoUri = mSdCardHelper.createTmpPhotoStoragePath(mTempPhotoPath)) != null) {
			Intent intent = new Intent();
			intent.setType("image/*");
			intent.setAction(Intent.ACTION_GET_CONTENT);
			startActivityForResult(Intent.createChooser(intent, getString(R.string.picker)),
					IMAGE_FROM_FILE_REQUEST_CODE);
		} else {
			Log.i(TAG, "path for storing photos cannot be created!");
			hideImagePreview();
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode != RESULT_OK)
			return;

		mHasMedia = true;
		switch (requestCode) {
		case IMAGE_FROM_CAMERA_REQUEST_CODE:

			// display the picture
			mPhotoBitmap = mSdCardHelper.decodeBitmapFile(mTempPhotoUri.getPath());
			showImagePreview();
			break;

		case IMAGE_FROM_FILE_REQUEST_CODE:

			// display the photo
			Uri mImageGalleryUri = data.getData();

			// get the real path for chosen photo
			mImageGalleryUri = Uri.parse(mSdCardHelper.getRealPathFromUri((Activity) ComposeTweetActivity.this,
					mImageGalleryUri));

			// copy the photo from gallery to tmp directory
			String fromFile = mImageGalleryUri.getPath();
			String toFile = mTempPhotoUri.getPath();
			if (mSdCardHelper.copyFile(fromFile, toFile)) {
				mPhotoBitmap = mSdCardHelper.decodeBitmapFile(toFile);
				showImagePreview();
			}
			break;
		}
	}

	private void hideImagePreview() {
		mPhotoPreviewContainer.setVisibility(View.GONE);
	}

	private void showImagePreview() {
		mPhotoPreview.setImageBitmap(mPhotoBitmap);
		Intent imageClickIntent = new Intent(this, PhotoViewActivity.class);
		imageClickIntent.putExtra(PhotoViewActivity.EXTRA_KEY_IMAGE_URI, mTempPhotoUri.toString());
		mPhotoPreview.setOnClickActivityIntent(imageClickIntent);
		mPhotoPreviewContainer.setVisibility(View.VISIBLE);
		checkTweetLength();
	}
}
