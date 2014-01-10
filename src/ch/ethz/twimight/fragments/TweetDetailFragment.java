/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. activity program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies activity distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/
package ch.ethz.twimight.fragments;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import twitter4j.HashtagEntity;
import twitter4j.MediaEntity;
import twitter4j.TweetEntity;
import twitter4j.URLEntity;
import twitter4j.UserMentionEntity;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.PhotoViewActivity;
import ch.ethz.twimight.activities.SearchableActivity;
import ch.ethz.twimight.activities.TwimightBaseActivity;
import ch.ethz.twimight.activities.UserProfileActivity;
import ch.ethz.twimight.activities.WebViewActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.twitter.EntityQueue;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TweetsContentProvider;
import ch.ethz.twimight.net.twitter.TwitterSyncService;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.util.Serialization;
import ch.ethz.twimight.views.TweetButtonBar;

/**
 * Display a tweet
 * 
 * @author thossmann
 * @author pcarta
 */
@SuppressLint("ValidFragment")
public class TweetDetailFragment extends Fragment {

	private static final String ARG_KEY_ROWID = "rowId";

	private Cursor mCursor;

	// Views
	private TextView mTvScreenName;
	private TextView mTvRealName;
	private TextView mTvTweetText;
	private TextView mTvTweetCreationDetails;
	private TextView mTvRetweetedBy;
	private LinearLayout mUnverifiedInfo;

	private WebView mImageWebView;

	private String mPhotoPath;

	private LinearLayout mUserInfoView;
	private LinearLayout mShowTweetLayout;

	private Uri mUri;
	private ContentObserver mObserver;

	private int mFlags;
	private int mBuffer;
	private int mUserRowId;
	private long mRowId;
	private String mText;
	private String mScreenName;

	protected String TAG = "TweetDetailFragment";

	// LOGS
	private LocationHelper mLocationHelper;
	private ConnectivityManager mConnectivityManager;
	private StatisticsDBHelper mStatsDbHelper;

	private ContentResolver mContentResolver;
	private View mView;

	private String mPhotoDirectoryPath;

	// SDcard helper
	private SDCardHelper sdCardHelper;

	private String mTwitterUserId;

	// offline html pages
	private HtmlPagesDbHelper mHtmlDbHelper;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
	}

	public static TweetDetailFragment newInstance(long rowId) {
		TweetDetailFragment instance = new TweetDetailFragment();
		Bundle args = new Bundle();
		args.putLong(ARG_KEY_ROWID, rowId);
		instance.setArguments(args);
		return instance;
	}

	private void updateCursor() {
		discardCursor();
		mCursor = mContentResolver.query(mUri, null, null, null, null);
		if (mCursor != null && mCursor.getCount() > 0) {
			mCursor.moveToFirst();
			mCursor.registerContentObserver(mObserver);
			updateContent();
		} else {
			getActivity().finish();
		}
	}

	private void updateContent() {
		mBuffer = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER));
		mFlags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
		mTwitterUserId = String.valueOf(mCursor.getLong(mCursor.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID)));
		mPhotoDirectoryPath = Tweets.PHOTO_PATH + "/" + mTwitterUserId;
		setTweetInfo();
		setUserInfo();
		setProfilePicture();
		setPhotoAttached();
		mUnverifiedInfo.setVisibility(LinearLayout.GONE);
		if ((mBuffer & Tweets.BUFFER_DISASTER) != 0) {

			if (mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_ISVERIFIED)) == 0) {
				mUnverifiedInfo.setVisibility(LinearLayout.VISIBLE);
			}
		}
		handleTweetFlags();
		// If there are any flags, schedule the Tweet for synch
		if (mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS)) > 0) {
			Log.i(TAG, "requesting tweet update to twitter");
			Intent i = new Intent(getActivity(), TwitterSyncService.class);
			i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_TWEET);
			i.putExtra(TwitterSyncService.EXTRA_TWEET_ROW_ID, Long.valueOf(mUri.getLastPathSegment()));
			getActivity().startService(i);
		}
	}

	private void discardCursor() {
		if (mCursor != null) {
			mCursor.unregisterContentObserver(mObserver);
			mCursor.close();
			mCursor = null;
		}
	}

	private void captureViews() {
		mShowTweetLayout = (LinearLayout) mView.findViewById(R.id.showTweetLayout);
		mTvScreenName = (TextView) mView.findViewById(R.id.showTweetScreenName);
		mTvRealName = (TextView) mView.findViewById(R.id.showTweetRealName);
		mImageWebView = (WebView) mView.findViewById(R.id.photoWebView);
		mTvTweetText = (TextView) mView.findViewById(R.id.showTweetText);
		mTvTweetCreationDetails = (TextView) mView.findViewById(R.id.tvTweetCreationDetails);
		mTvRetweetedBy = (TextView) mView.findViewById(R.id.showTweetRetweeted_by);
		mUnverifiedInfo = (LinearLayout) mView.findViewById(R.id.showTweetUnverified);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);
		// Inflate the layout for activity fragment
		mView = inflater.inflate(R.layout.tweet_detail, container, false);
		captureViews();
		TweetButtonBar buttonBar = new TweetButtonBar(getActivity(), mRowId);
		mShowTweetLayout.addView(buttonBar);
		return mView;
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// if we are creating a new instance, get row id from arguments,
		// otherwise from saved instance state
		if (savedInstanceState == null) {
			mRowId = getArguments().getLong(ARG_KEY_ROWID);
		} else {
			mRowId = savedInstanceState.getLong(ARG_KEY_ROWID);
		}

		mContentResolver = getActivity().getContentResolver();
		mUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + mRowId);
		mObserver = new TweetContentObserver(new Handler());

		mStatsDbHelper = new StatisticsDBHelper(getActivity().getApplicationContext());
		mStatsDbHelper.open();

		sdCardHelper = new SDCardHelper();

		// html database
		mHtmlDbHelper = new HtmlPagesDbHelper(getActivity());
		mHtmlDbHelper.open();

		mConnectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
		mLocationHelper = LocationHelper.getInstance(getActivity());
	}

	/**
	 * method to set the photo attached with the tweet
	 * 
	 */
	private void setPhotoAttached() {
		if (mImageWebView.getVisibility() != View.VISIBLE) {
			// Profile image
			ImageView photoView = (ImageView) mView.findViewById(R.id.showPhotoAttached);
			photoView.setVisibility(View.GONE);
			String[] filePath = { mPhotoDirectoryPath };
			if (sdCardHelper.checkSDState(filePath)) {
				if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_MEDIA))) {
					String photoFileName = mCursor.getString(mCursor.getColumnIndex(Tweets.COL_MEDIA));
					Uri photoUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(mPhotoDirectoryPath, photoFileName));// photoFileParent,
					// photoFilename));
					mPhotoPath = photoUri.getPath();
					Bitmap photo = sdCardHelper.decodeBitmapFile(mPhotoPath);
					photoView.setImageBitmap(photo);
					photoView.setVisibility(View.VISIBLE);
					photoView.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View v) {
							viewPhoto();
						}
					});
				}
			}
		}
	}

	public void viewPhoto() {
		Intent intent = new Intent(getActivity(), PhotoViewActivity.class);
		intent.putExtra(PhotoViewActivity.PHOTO_PATH_EXTRA, mPhotoPath);
		startActivity(intent);
	}

	/**
	 * Sets the visibility of the info icons according to the tweet's flags.
	 */
	private void handleTweetFlags() {
		LinearLayout toSendNotification = (LinearLayout) mView.findViewById(R.id.showTweetTosend);
		LinearLayout toDeleteNotification = (LinearLayout) mView.findViewById(R.id.showTweetTodelete);
		LinearLayout toFavoriteNotification = (LinearLayout) mView.findViewById(R.id.showTweetTofavorite);
		LinearLayout toUnfavoriteNotification = (LinearLayout) mView.findViewById(R.id.showTweetTounfavorite);
		LinearLayout toRetweetNotification = (LinearLayout) mView.findViewById(R.id.showTweetToretweet);
		if (toSendNotification != null) {
			if ((mFlags & Tweets.FLAG_TO_INSERT) == 0) {
				toSendNotification.setVisibility(LinearLayout.GONE);
			} else
				toSendNotification.setVisibility(LinearLayout.VISIBLE);
		} else
			Log.i(TAG, "toSendNotification");

		if (toDeleteNotification != null) {
			if ((mFlags & Tweets.FLAG_TO_DELETE) == 0) {
				toDeleteNotification.setVisibility(LinearLayout.GONE);

			} else {
				toDeleteNotification.setVisibility(LinearLayout.VISIBLE);
				TextView toDeleteText = (TextView) mView.findViewById(R.id.showTweetInfoText2);
				if (toDeleteText != null) {
					toDeleteText.setBackgroundResource(android.R.drawable.list_selector_background);
					toDeleteText.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View v) {
							LinearLayout toDeleteNotification = (LinearLayout) mView
									.findViewById(R.id.showTweetTodelete);
							if (toDeleteNotification != null) {

								int num = mContentResolver.update(mUri, removeDeleteFlag(mFlags), null, null);
								toDeleteNotification.setVisibility(LinearLayout.GONE);
								if (num > 0) {
									if (mCursor != null) {
										mFlags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
									}
								}
							} else
								Log.i(TAG, "toDeleteNotification");

						}
					});
				} else
					Log.i(TAG, "toSendNotification");
			}
		} else
			Log.i(TAG, "toDeleteNotification");

		if (toFavoriteNotification != null) {
			if ((mFlags & Tweets.FLAG_TO_FAVORITE) == 0) {
				toFavoriteNotification.setVisibility(LinearLayout.GONE);

			} else
				toFavoriteNotification.setVisibility(LinearLayout.VISIBLE);
		} else
			Log.i(TAG, "toFavoriteNotification");

		if (toUnfavoriteNotification != null) {
			if ((mFlags & Tweets.FLAG_TO_UNFAVORITE) == 0) {
				toUnfavoriteNotification.setVisibility(LinearLayout.GONE);

			} else
				toUnfavoriteNotification.setVisibility(LinearLayout.VISIBLE);
		} else
			Log.i(TAG, "toUnFavoriteNotification");

		if (toRetweetNotification != null) {
			if ((mFlags & Tweets.FLAG_TO_RETWEET) == 0) {
				toRetweetNotification.setVisibility(LinearLayout.GONE);

			} else
				toRetweetNotification.setVisibility(LinearLayout.VISIBLE);
		}
	}

	/**
	 * method to set the profile picture
	 * 
	 */
	private void setProfilePicture() {
		// Profile image
		if (!mCursor.isNull(mCursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH))) {

			ImageView picture = (ImageView) mView.findViewById(R.id.showTweetProfileImage);
			int userId = mCursor.getInt(mCursor.getColumnIndex(TweetsContentProvider.COL_USER_ROW_ID));
			Uri imageUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS + "/" + userId);
			InputStream is;

			try {
				is = mContentResolver.openInputStream(imageUri);
				if (is != null) {
					Bitmap bm = BitmapFactory.decodeStream(is);
					picture.setImageBitmap(bm);
				} else
					picture.setImageResource(R.drawable.profile_image_placeholder);
			} catch (FileNotFoundException e) {
				Log.e(TAG, "error opening input stream", e);
				picture.setImageResource(R.drawable.profile_image_placeholder);
			}
		}

	}

	/**
	 * The user info
	 * 
	 */
	private void setUserInfo() {

		mUserRowId = mCursor.getInt(mCursor.getColumnIndex(TweetsContentProvider.COL_USER_ROW_ID));
		mUserInfoView = (LinearLayout) mView.findViewById(R.id.showTweetUserInfo);

		mUserInfoView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent i = new Intent(getActivity(), UserProfileActivity.class);
				i.putExtra("rowId", mUserRowId);
				i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(i);
			}

		});

	}

	private class InternalURLSpan extends TouchableSpan {
		String url;

		public InternalURLSpan(String url, int normalTextColor, int pressedTextColor, int pressedBackgroundColor) {
			super(normalTextColor, pressedTextColor, pressedBackgroundColor);
			this.url = url;
		}

		@Override
		public void onClick(View widget) {

			if ((mLocationHelper != null && mLocationHelper.getCount() > 0) && mStatsDbHelper != null
					&& mConnectivityManager.getActiveNetworkInfo() != null) {
				mLocationHelper.unRegisterLocationListener();
				mStatsDbHelper.insertRow(mLocationHelper.getLocation(), mConnectivityManager.getActiveNetworkInfo()
						.getTypeName(), StatisticsDBHelper.LINK_CLICKED, url, System.currentTimeMillis());
			}

			if (mConnectivityManager.getActiveNetworkInfo() != null
					&& mConnectivityManager.getActiveNetworkInfo().isConnected()) {
				// if there is active internet access, use normal browser
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(intent);
			} else {
				String[] filePath = { HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(getActivity()) };
				PackageManager pm;
				Cursor c;

				switch (sdCardHelper.checkFileType(url)) {

				case SDCardHelper.TYPE_XML:

					Cursor cursorInfo = mHtmlDbHelper.getPageInfo(url);
					if (cursorInfo != null) {

						String filename = null;
						if (!cursorInfo.isNull(cursorInfo.getColumnIndex(HtmlPage.COL_FILENAME))) {
							filename = cursorInfo.getString(cursorInfo.getColumnIndex(HtmlPage.COL_FILENAME));

							Log.i(TAG, "length: " + sdCardHelper.getFileFromSDCard(filePath[0], filename).length()
									+ " bytes");

							if (sdCardHelper.getFileFromSDCard(filePath[0], filename).length() > 0) {

								// set up our own web view
								Intent intentToWeb = new Intent(getActivity(), WebViewActivity.class);
								intentToWeb.putExtra("url", url);
								intentToWeb.putExtra("filename", filename);
								startActivity(intentToWeb);

							} else {
								Toast.makeText(getActivity(), getString(R.string.faulty_page), Toast.LENGTH_LONG)
										.show();
							}

						} else
							Toast.makeText(getActivity(), getString(R.string.file_not_exists), Toast.LENGTH_LONG)
									.show();

					} else {
						Log.i(TAG, "content values null");
					}

					break;

				case SDCardHelper.TYPE_PDF:
					Log.i(TAG, "view pdf");
					c = mHtmlDbHelper.getPageInfo(url);
					if (c != null) {
						Intent intentToPDF = new Intent(Intent.ACTION_VIEW, Uri.fromFile(sdCardHelper
								.getFileFromSDCard(filePath[0], c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)))));
						pm = getActivity().getPackageManager();
						List<ResolveInfo> activitiesPDF = pm.queryIntentActivities(intentToPDF, 0);
						if (activitiesPDF.size() > 0) {
							startActivity(intentToPDF);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(getActivity(), R.string.no_valid_pdf, Toast.LENGTH_LONG).show();
						}
						c.close();
					}

					break;
				case SDCardHelper.TYPE_PNG:
				case SDCardHelper.TYPE_GIF:
				case SDCardHelper.TYPE_JPG:
					Log.i(TAG, "view picture");
					c = mHtmlDbHelper.getPageInfo(url);
					if (c != null) {

						File picFile = sdCardHelper.getFileFromSDCard(filePath[0],
								c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)));
						Intent intentToPic = new Intent(Intent.ACTION_VIEW);
						intentToPic.setDataAndType(Uri.parse("file://" + Uri.fromFile(picFile).getPath()), "image/*");
						pm = getActivity().getPackageManager();
						List<ResolveInfo> activitiesPic = pm.queryIntentActivities(intentToPic, 0);
						if (activitiesPic.size() > 0) {
							startActivity(intentToPic);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(getActivity(), R.string.no_valid_pictures, Toast.LENGTH_LONG).show();
						}
						c.close();
					}

					break;
				case SDCardHelper.TYPE_MP3:
					Log.i(TAG, "play audio");

					c = mHtmlDbHelper.getPageInfo(url);
					if (c != null) {

						File mp3File = sdCardHelper.getFileFromSDCard(filePath[0],
								c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)));
						Intent intentToMp3 = new Intent(Intent.ACTION_VIEW);
						intentToMp3.setDataAndType(Uri.parse("file://" + Uri.fromFile(mp3File).getPath()), "audio/mp3");
						pm = getActivity().getPackageManager();
						List<ResolveInfo> activitiesAudio = pm.queryIntentActivities(intentToMp3, 0);
						if (activitiesAudio.size() > 0) {
							startActivity(intentToMp3);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(getActivity(), R.string.no_valid_audio, Toast.LENGTH_LONG).show();
						}
						c.close();
					}

					break;
				case SDCardHelper.TYPE_MP4:
				case SDCardHelper.TYPE_RMVB:
				case SDCardHelper.TYPE_FLV:
					Log.i(TAG, "play video");

					c = mHtmlDbHelper.getPageInfo(url);
					if (c != null) {

						File videoFile = sdCardHelper.getFileFromSDCard(filePath[0],
								c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME)));
						Intent intentToVideo = new Intent(Intent.ACTION_VIEW);
						intentToVideo.setDataAndType(Uri.parse("file://" + Uri.fromFile(videoFile).getPath()),
								"video/flv");
						pm = getActivity().getPackageManager();
						List<ResolveInfo> activitiesVideo = pm.queryIntentActivities(intentToVideo, 0);
						if (activitiesVideo.size() > 0) {
							startActivity(intentToVideo);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(getActivity(), R.string.no_valid_video, Toast.LENGTH_LONG).show();
						}
						c.close();
					}
					break;

				}

			}

		}
	}

	/**
	 * The tweet info
	 * 
	 */
	private void setTweetInfo() {

		mScreenName = mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_SCREENNAME));
		mTvScreenName.setText("@" + mScreenName);
		mTvRealName.setText(mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_NAME)));
		mText = mCursor.getString(mCursor.getColumnIndex(Tweets.COL_TEXT_PLAIN));

		byte[] serializedMentionEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_USER_MENTION_ENTITIES));
		UserMentionEntity[] userMentionEntities = Serialization.deserialize(serializedMentionEntities);

		byte[] serializedHashtagEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_HASHTAG_ENTITIES));
		HashtagEntity[] hashtagEntities = Serialization.deserialize(serializedHashtagEntities);

		byte[] serializedMediaEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_MEDIA_ENTITIES));
		MediaEntity[] mediaEntities = Serialization.deserialize(serializedMediaEntities);

		byte[] serializedUrlEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_URL_ENTITIES));
		URLEntity[] urlEntities = Serialization.deserialize(serializedUrlEntities);

		EntityQueue allEntities = new EntityQueue(userMentionEntities, hashtagEntities, mediaEntities, urlEntities);

		SpannableStringBuilder tweetTextSpannable = new SpannableStringBuilder(mText);

		int normalLinkColor = getResources().getColor(R.color.medium_gray);
		int pressedLinkColor = getResources().getColor(R.color.medium_dark_gray);
		int pressedLinkBackground = getResources().getColor(R.color.lighter_gray);

		while (!allEntities.isEmpty()) {
			TweetEntity entity = allEntities.remove();
			if (entity instanceof UserMentionEntity) {
				UserMentionEntity userMentionEntity = (UserMentionEntity) entity;
				tweetTextSpannable.setSpan(new MentionClickableSpan(userMentionEntity.getScreenName(), normalLinkColor,
						pressedLinkColor, pressedLinkBackground), userMentionEntity.getStart(), userMentionEntity
						.getEnd(), Spannable.SPAN_MARK_MARK);

			} else if (entity instanceof HashtagEntity) {
				HashtagEntity hashtagEntity = (HashtagEntity) entity;
				tweetTextSpannable.setSpan(new HashtagClickableSpan(hashtagEntity.getText(), normalLinkColor,
						pressedLinkColor, pressedLinkBackground), hashtagEntity.getStart(), hashtagEntity.getEnd(),
						Spannable.SPAN_MARK_MARK);
			} else if (entity instanceof MediaEntity) {
				// MediaEntities must come before URLEntities because they are a
				// specialized version of URLEntities
				MediaEntity mediaEntity = (MediaEntity) entity;
				tweetTextSpannable.setSpan(new InternalURLSpan(mediaEntity.getURL(), normalLinkColor, pressedLinkColor,
						pressedLinkBackground), mediaEntity.getStart(), mediaEntity.getEnd(), Spannable.SPAN_MARK_MARK);

				tweetTextSpannable.replace(mediaEntity.getStart(), mediaEntity.getEnd(), mediaEntity.getDisplayURL());

				String html = "<html><head><meta name=\"viewport\" content=\"width=device-width, user-scalable=no\"></head><body style=\"margin:0px; padding:0px\"></body><img width=\"100%%\" src=\"%s\"/></body></html>";
				mImageWebView.setVisibility(View.VISIBLE);
				mImageWebView.loadData(String.format(html, mediaEntity.getMediaURL()), "text/html", null);
			} else if (entity instanceof URLEntity) {
				URLEntity urlEntity = (URLEntity) entity;
				tweetTextSpannable.setSpan(new InternalURLSpan(urlEntity.getURL(), normalLinkColor, pressedLinkColor,
						pressedLinkBackground), urlEntity.getStart(), urlEntity.getEnd(), Spannable.SPAN_MARK_MARK);
				tweetTextSpannable.replace(urlEntity.getStart(), urlEntity.getEnd(), urlEntity.getDisplayURL());
			}
		}

		mTvTweetText.setText(tweetTextSpannable);
		mTvTweetText.setMovementMethod(new LinkTouchMovementMethod());

		StringBuffer tweetCreationDetails = new StringBuffer();

		// created at
		tweetCreationDetails.append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
				.format(new Date(mCursor.getLong(mCursor.getColumnIndex(Tweets.COL_CREATED)))).toString());

		// created via (if available)
		if (mCursor.getString(mCursor.getColumnIndex(Tweets.COL_SOURCE)) != null) {
			tweetCreationDetails.append(getResources().getString(R.string.via));
			tweetCreationDetails.append(Html.fromHtml(mCursor.getString(mCursor.getColumnIndex(Tweets.COL_SOURCE))));
		}
		mTvTweetCreationDetails.setText(tweetCreationDetails);

		// retweeted by
		String retweeted_by = mCursor.getString(mCursor.getColumnIndex(Tweets.COL_RETWEETED_BY));
		if (retweeted_by != null) {
			mTvRetweetedBy.setText(getString(R.string.retweeted_by) + " " + retweeted_by);
			mTvRetweetedBy.setVisibility(View.VISIBLE);
		} else {
			mTvRetweetedBy.setVisibility(View.GONE);
		}

	}

	/**
	 * On resume
	 */
	@Override
	public void onResume() {
		super.onResume();
		Log.d(TAG, "onResume");
		updateCursor();
		mLocationHelper.registerLocationListener();
	}

	/**
	 * On Pause
	 */
	@Override
	public void onPause() {
		Log.d(TAG, "onPause");
		super.onPause();
		discardCursor();
		if (mLocationHelper != null) {
			mLocationHelper.unRegisterLocationListener();
		}
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mUserInfoView != null) {
			mUserInfoView.setOnClickListener(null);
		}
		TwimightBaseActivity.unbindDrawables(getActivity().findViewById(R.id.showTweetRoot));
	}

	/**
	 * Removes the delete flag and returns the flags in a content value
	 * structure to send to the content provider
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues removeDeleteFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.COL_FLAGS, flags & (~Tweets.FLAG_TO_DELETE));
		cv.put(Tweets.COL_BUFFER, mBuffer);
		return cv;
	}

	/**
	 * Calls methods if tweet data has been updated
	 * 
	 * @author pcarta
	 * 
	 */
	class TweetContentObserver extends ContentObserver {
		public TweetContentObserver(Handler h) {
			super(h);
		}

		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
			updateCursor();
		}
	}

	private class HashtagClickableSpan extends TouchableSpan {
		private final String mHashtag;

		public HashtagClickableSpan(String hashtag, int normalTextColor, int pressedTextColor,
				int pressedBackgroundColor) {
			super(normalTextColor, pressedTextColor, pressedBackgroundColor);
			mHashtag = "#" + hashtag;
		}

		@Override
		public void onClick(View view) {
			Intent i = new Intent(view.getContext(), SearchableActivity.class);
			i.putExtra(SearchManager.QUERY, mHashtag);
			view.getContext().startActivity(i);
		}
	}

	private class MentionClickableSpan extends TouchableSpan {
		private final String mScreenName;

		public MentionClickableSpan(String screenName, int normalTextColor, int pressedTextColor,
				int pressedBackgroundColor) {
			super(normalTextColor, pressedTextColor, pressedBackgroundColor);
			mScreenName = screenName;
		}

		@Override
		public void onClick(View view) {
			Intent i = new Intent(view.getContext(), UserProfileActivity.class);
			i.putExtra("screenname", mScreenName);
			view.getContext().startActivity(i);
		}
	}

	/**
	 * A specialized Clickable span that updates its appearance when it is
	 * pressed.
	 * 
	 * @author msteven
	 * 
	 */
	private abstract class TouchableSpan extends ClickableSpan {
		private boolean mIsPressed;
		private int mPressedBackgroundColor;
		private int mNormalTextColor;
		private int mPressedTextColor;

		public TouchableSpan(int normalTextColor, int pressedTextColor, int pressedBackgroundColor) {
			mNormalTextColor = normalTextColor;
			mPressedTextColor = pressedTextColor;
			mPressedBackgroundColor = pressedBackgroundColor;
		}

		public void setPressed(boolean isSelected) {
			mIsPressed = isSelected;
		}

		@Override
		public void updateDrawState(TextPaint ds) {
			super.updateDrawState(ds);
			ds.setColor(mIsPressed ? mPressedTextColor : mNormalTextColor);
			ds.bgColor = mIsPressed ? mPressedBackgroundColor : 0xffffffff;
			ds.setUnderlineText(false);
		}
	}

	/**
	 * Interprets touch events on a TextView so that the pressed span can be
	 * highlighted.
	 * 
	 * @author msteven
	 * 
	 */
	private class LinkTouchMovementMethod extends LinkMovementMethod {
		private TouchableSpan mPressedSpan;

		@Override
		public boolean onTouchEvent(TextView textView, Spannable spannable, MotionEvent event) {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				mPressedSpan = getPressedSpan(textView, spannable, event);
				if (mPressedSpan != null) {
					mPressedSpan.setPressed(true);
					Selection.setSelection(spannable, spannable.getSpanStart(mPressedSpan),
							spannable.getSpanEnd(mPressedSpan));
				}
			} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
				TouchableSpan touchedSpan = getPressedSpan(textView, spannable, event);
				if (mPressedSpan != null && touchedSpan != mPressedSpan) {
					mPressedSpan.setPressed(false);
					mPressedSpan = null;
					Selection.removeSelection(spannable);
				}
			} else {
				if (mPressedSpan != null) {
					mPressedSpan.setPressed(false);
					super.onTouchEvent(textView, spannable, event);
				}
				mPressedSpan = null;
				Selection.removeSelection(spannable);
			}
			return true;
		}

		private TouchableSpan getPressedSpan(TextView textView, Spannable spannable, MotionEvent event) {

			int x = (int) event.getX();
			int y = (int) event.getY();

			x -= textView.getTotalPaddingLeft();
			y -= textView.getTotalPaddingTop();

			x += textView.getScrollX();
			y += textView.getScrollY();

			Layout layout = textView.getLayout();
			int line = layout.getLineForVertical(y);
			int off = layout.getOffsetForHorizontal(line, x);

			TouchableSpan[] link = spannable.getSpans(off, off, TouchableSpan.class);
			TouchableSpan touchedSpan = null;
			if (link.length > 0) {
				touchedSpan = link[0];
			}
			return touchedSpan;
		}

	}

}
