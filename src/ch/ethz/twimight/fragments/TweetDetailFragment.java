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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
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
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.ComposeTweetActivity;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.PhotoViewActivity;
import ch.ethz.twimight.activities.TwimightBaseActivity;
import ch.ethz.twimight.activities.UserProfileActivity;
import ch.ethz.twimight.activities.WebViewActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.Html.StartServiceHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TweetsContentProvider;
import ch.ethz.twimight.net.twitter.TwitterSyncService.SyncTweetService;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.util.TweetTagHandler;

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
	private TextView screenNameView;
	private TextView realNameView;
	private TextView tweetTextView;
	private TextView tvTweetCreationDetails;
	private TextView tvRetweetedBy;

	private String mPhotoPath;
	
	private LinearLayout userInfoView;
	ImageButton retweetButton;
	ImageButton deleteButton;
	ImageButton replyButton;
	ImageButton favoriteButton;
	ImageButton offlineButton;

	Uri uri;
	ContentObserver observer;
	Handler handler;

	private boolean favorited;
	private int mFlags;
	private int mBuffer;
	private int mUserRowId;
	private long mRowId;
	private String mText;
	private String mScreenName;

	protected String TAG = "TweetDetailFragment";

	// LOGS
	LocationHelper locHelper;
	Intent intent;
	ConnectivityManager cm;
	StatisticsDBHelper statsDBHelper;

	Activity activity;
	ContentResolver resolver;
	View view;

	// photo
	private String photoPath;

	// SDcard helper
	private SDCardHelper sdCardHelper;

	private String userID = null;

	// offline html pages
	private int htmlStatus;
	private ArrayList<String> htmlUrls;
	private HtmlPagesDbHelper htmlDbHelper;
	private ArrayList<String> htmlsToDownload;

	// Container Activity must implement this interface
	public interface OnTweetDeletedListener {
		public void onDelete();
	}

	OnTweetDeletedListener listener;

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		try {
			listener = (OnTweetDeletedListener) activity;
		} catch (ClassCastException e) {
			e.printStackTrace();
		}
	}

	public static TweetDetailFragment newInstance(long rowId) {
		TweetDetailFragment instance = new TweetDetailFragment();
		Bundle args = new Bundle();
		args.putLong(ARG_KEY_ROWID, rowId);
		instance.setArguments(args);
		return instance;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		super.onCreateView(inflater, container, savedInstanceState);
		// Inflate the layout for activity fragment
		view = inflater.inflate(R.layout.tweet_detail, container, false);
		screenNameView = (TextView) view.findViewById(R.id.showTweetScreenName);
		realNameView = (TextView) view.findViewById(R.id.showTweetRealName);

		tweetTextView = (TextView) view.findViewById(R.id.showTweetText);
		tvTweetCreationDetails = (TextView) view
				.findViewById(R.id.tvTweetCreationDetails);
		tvRetweetedBy = (TextView) view
				.findViewById(R.id.showTweetRetweeted_by);

		// if we are creating a new instance, get row id from arguments,
		// otherwise from saved instance state
		if (savedInstanceState == null) {
			mRowId = getArguments().getLong(ARG_KEY_ROWID);
		} else {
			mRowId = savedInstanceState.getLong(ARG_KEY_ROWID);
		}

		// If we don't know which tweet to show, we stop the activity
		if (mRowId != 0) {

			loadCursor();

			if (mCursor.getCount() == 0) {
				activity.getFragmentManager().beginTransaction().remove(this)
						.commit();
			} else {
				// register content observer to refresh when user was updated
				handler = new Handler();

				userID = String.valueOf(mCursor.getLong(mCursor
						.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID)));
				// locate the directory where the photos are stored
				photoPath = Tweets.PHOTO_PATH + "/" + userID;

				setTweetInfo();
				setUserInfo();
				setProfilePicture();

				setPhotoAttached();
				// disaster info
				LinearLayout unverifiedInfo = (LinearLayout) view
						.findViewById(R.id.showTweetUnverified);
				unverifiedInfo.setVisibility(LinearLayout.GONE);
				if ((mBuffer & Tweets.BUFFER_DISASTER) != 0) {

					if (mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_ISVERIFIED)) == 0) {
						unverifiedInfo.setVisibility(LinearLayout.VISIBLE);
					}
				}

				handleTweetFlags();
				setupButtons();

				setHtml();

				// If there are any flags, schedule the Tweet for synch
				if (mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS)) > 0) {
					Log.i(TAG, "requesting tweet update to twitter");
					Intent i = new Intent(getActivity(), SyncTweetService.class);
					i.putExtra(SyncTweetService.EXTRA_ROW_ID, Long.valueOf(uri.getLastPathSegment()));
					activity.startService(i);
				}
			}
		} else
			activity.getFragmentManager().beginTransaction().remove(this)
					.commit();

		return view;
	}

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		activity = getActivity();
		resolver = activity.getContentResolver();

		statsDBHelper = new StatisticsDBHelper(activity.getApplicationContext());
		statsDBHelper.open();

		sdCardHelper = new SDCardHelper();

		// html database
		htmlDbHelper = new HtmlPagesDbHelper(activity);
		htmlDbHelper.open();
		htmlUrls = new ArrayList<String>();

		cm = (ConnectivityManager) activity
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		locHelper = LocationHelper.getInstance(activity);

	}

	/**
	 * method to set the photo attached with thi tweet
	 * 
	 */

	private void setPhotoAttached() {
		// Profile image
		ImageView photoView = (ImageView) view
				.findViewById(R.id.showPhotoAttached);
		photoView.setVisibility(View.GONE);
		String[] filePath = { photoPath };
		if (sdCardHelper.checkSDState(filePath)) {
			if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_MEDIA))) {
				String photoFileName = mCursor.getString(mCursor
						.getColumnIndex(Tweets.COL_MEDIA));
				Uri photoUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(
						photoPath, photoFileName));// photoFileParent,
													// photoFilename));
				mPhotoPath = photoUri.getPath();
				Bitmap photo = sdCardHelper
						.decodeBitmapFile(mPhotoPath);
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
	
	public void viewPhoto(){
		Intent intent = new Intent(getActivity(), PhotoViewActivity.class);
		intent.putExtra(PhotoViewActivity.PHOTO_PATH_EXTRA, mPhotoPath);
		startActivity(intent);
	}

	private void setHtml() {

		htmlsToDownload = new ArrayList<String>();
		// tweetId = c.getLong(c.getColumnIndex(Tweets.COL_TID));
		boolean buttonStatus = false;
		// try to retrieve the filename of attached html pages
		if (!htmlUrls.isEmpty()) {
			for (String htmlUrl : htmlUrls) {

				Cursor cursorInfo = htmlDbHelper.getPageInfo(htmlUrl);
				String filename = null;
				boolean fileStatusNormal = true;

				if (cursorInfo != null) {

					// check if file status normal, exists and size
					String[] filePath = { HtmlPage.HTML_PATH + "/"
							+ LoginActivity.getTwitterId(activity) };

					if (sdCardHelper.checkSDState(filePath)) {

						if (!cursorInfo.isNull(cursorInfo
								.getColumnIndex(HtmlPage.COL_FILENAME))) {
							filename = cursorInfo.getString(cursorInfo
									.getColumnIndex(HtmlPage.COL_FILENAME));

							if (sdCardHelper.getFileFromSDCard(filePath[0],
									filename).length() <= 1) {
								fileStatusNormal = false;
							}
						}

					}

				}

				// if entry does not exist, add the url in to be downloaded list
				if (cursorInfo == null || (filename == null)
						|| !fileStatusNormal) {

					htmlsToDownload.add(htmlUrl);
					buttonStatus = true;

				}

			}

		}

		if (!buttonStatus) {
			offlineButton.setVisibility(View.GONE);
		}
	}

	// perform downloading task when user click download button
	private void downloadAndInsert() {

		// insert database
		String[] filePath = { HtmlPage.HTML_PATH + "/"
				+ LoginActivity.getTwitterId(activity) };
		if (sdCardHelper.checkSDState(filePath)) {

			Long tweetId = mCursor.getLong(mCursor.getColumnIndex(Tweets.COL_DISASTERID));
			for (int i = 0; i < htmlsToDownload.size(); i++) {

				Cursor cursorInfo = htmlDbHelper.getPageInfo(htmlsToDownload
						.get(i));
				if (cursorInfo != null) {

					int attempts = cursorInfo.getInt(cursorInfo
							.getColumnIndex(HtmlPage.COL_ATTEMPTS));
					if (attempts > HtmlPage.DOWNLOAD_LIMIT) {

						String filename = null;
						if (!cursorInfo.isNull(cursorInfo
								.getColumnIndex(HtmlPage.COL_FILENAME))) {
							filename = cursorInfo.getString(mCursor
									.getColumnIndex(HtmlPage.COL_FILENAME));
							sdCardHelper.deleteFile(filePath[0] + "/"
									+ filename);
						}

						htmlDbHelper.updatePage(htmlsToDownload.get(i), null,
								tweetId, HtmlPagesDbHelper.DOWNLOAD_FORCED, 0);
					}

				} else {
					htmlDbHelper.insertPage(htmlsToDownload.get(i), tweetId,
							HtmlPagesDbHelper.DOWNLOAD_FORCED);
				}
			}

			resolver.notifyChange(Tweets.TABLE_TIMELINE_URI, null);
			// insert database and start downloading service
			StartServiceHelper.startService(activity);
		}

	}

	private void loadCursor() {
		// get data from local DB and mark for update
		if (mCursor != null && !mCursor.isClosed()) {
			mCursor.close();
		}

		uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/"
				+ Tweets.TWEETS + "/" + mRowId);
		mCursor = resolver.query(uri, null, null, null, null);

		if (mCursor != null && mCursor.getCount() > 0) {
			Log.i(TAG, "cursor ok");
			mCursor.moveToFirst();
			mBuffer = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER));
			mFlags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
		} else {
			Log.i(TAG, "cursor not ok");
		}
	}

	/**
	 * Sets the visibility of the info icons according to the tweet's flags.
	 */
	private void handleTweetFlags() {
		LinearLayout toSendNotification = (LinearLayout) view
				.findViewById(R.id.showTweetTosend);
		LinearLayout toDeleteNotification = (LinearLayout) view
				.findViewById(R.id.showTweetTodelete);
		LinearLayout toFavoriteNotification = (LinearLayout) view
				.findViewById(R.id.showTweetTofavorite);
		LinearLayout toUnfavoriteNotification = (LinearLayout) view
				.findViewById(R.id.showTweetTounfavorite);
		LinearLayout toRetweetNotification = (LinearLayout) view
				.findViewById(R.id.showTweetToretweet);
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
				TextView toDeleteText = (TextView) view
						.findViewById(R.id.showTweetInfoText2);
				if (toDeleteText != null) {
					toDeleteText
							.setBackgroundResource(android.R.drawable.list_selector_background);
					toDeleteText.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View v) {
							LinearLayout toDeleteNotification = (LinearLayout) view
									.findViewById(R.id.showTweetTodelete);
							if (toDeleteNotification != null) {

								int num = resolver.update(uri,
										removeDeleteFlag(mFlags), null, null);
								toDeleteNotification
										.setVisibility(LinearLayout.GONE);
								if (num > 0) {

									loadCursor();
									if (mCursor != null) {
										mFlags = mCursor.getInt(mCursor
												.getColumnIndex(Tweets.COL_FLAGS));
										setupButtons();
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
	 * Buttons
	 */
	private void setupButtons() {

		String userString = Long.toString(mCursor.getLong(mCursor
				.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID)));
		String localUserString = LoginActivity.getTwitterId(activity);

		// Retweet Button
		retweetButton = (ImageButton) view.findViewById(R.id.showTweetRetweet);
		// we do not show the retweet button for (1) tweets from the local user,
		// (2) tweets which have been flagged to retweeted and (3) tweets which
		// have been marked as retweeted
		if (userString.equals(localUserString)
				|| ((mFlags & Tweets.FLAG_TO_RETWEET) > 0)
				|| (mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_RETWEETED)) > 0)) {
			retweetButton.setVisibility(Button.GONE);
		} else {
			retweetButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					showRetweetDialog();
					retweetButton.setVisibility(Button.GONE);
				}

			});
		}

		// Delete Button
		deleteButton = (ImageButton) view.findViewById(R.id.showTweetDelete);
		if (userString.equals(localUserString)) {

			deleteButton.setVisibility(ImageButton.VISIBLE);
			if ((mFlags & Tweets.FLAG_TO_DELETE) == 0) {
				deleteButton.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						showDeleteDialog();
					}
				});

			} else {
				deleteButton.setVisibility(ImageButton.GONE);
			}
		} else {
			deleteButton.setVisibility(ImageButton.GONE);
		}

		// Reply button: we show it only if we have a Tweet ID!
		replyButton = (ImageButton) view.findViewById(R.id.showTweetReply);
		if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_TID))
				|| PreferenceManager.getDefaultSharedPreferences(activity)
						.getBoolean("prefDisasterMode",
								Constants.DISASTER_DEFAULT_ON) == true) {
			replyButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(activity, ComposeTweetActivity.class);
					if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_TID)))
						i.putExtra("isReplyTo",
								mCursor.getLong(mCursor.getColumnIndex(Tweets.COL_TID)));
					else
						i.putExtra("isReplyTo", -1);
					i.putExtra(
							"text",
							"@"
									+ mCursor.getString(mCursor
											.getColumnIndex(TwitterUsers.COL_SCREENNAME))
									+ " ");
					startActivity(i);
				}
			});
		} else {
			replyButton.setVisibility(Button.GONE);
		}

		// Favorite button
		favorited = ((mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER)) & Tweets.BUFFER_FAVORITES) != 0)
				|| ((mFlags & Tweets.FLAG_TO_FAVORITE) > 0);
		favoriteButton = (ImageButton) view
				.findViewById(R.id.showTweetFavorite);
		if (favorited && !((mFlags & Tweets.FLAG_TO_UNFAVORITE) > 0)) {
			favoriteButton.setImageResource(R.drawable.ic_favorite_on);
		}
		favoriteButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (favorited) {
					// unfavorite
					resolver.update(uri, clearFavoriteFlag(mFlags), null, null);
					((ImageButton) v)
							.setImageResource(R.drawable.ic_favorite_off);
					favorited = false;

				} else {
					// favorite
					ContentValues cv = setFavoriteFlag(mFlags);
					if (cv != null) {
						resolver.update(uri, cv, null, null);
						((ImageButton) v)
								.setImageResource(R.drawable.ic_favorite_on);
						favorited = true;
					}
				}

			}

		});

		// offline view button

		// get the html status of this tweet
		htmlStatus = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_HTML_PAGES));
		offlineButton = (ImageButton) view
				.findViewById(R.id.showTweetOfflineview);

		if (htmlStatus == 0) {
			offlineButton.setVisibility(View.GONE);
		} else {

			offlineButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {

					downloadAndInsert();
					Toast.makeText(getActivity(), getResources().getString(R.string.download_toast), Toast.LENGTH_SHORT).show();
					// TODO: Fix download functionality and apply appropriate
					// icon
					// offlineButton
					// .setImageResource(R.drawable.btn_twimight_archive_on);

				}

			});
		}

	}

	/**
	 * method to set the profile picture
	 * 
	 */
	private void setProfilePicture() {
		// Profile image
		if (!mCursor.isNull(mCursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH))) {

			ImageView picture = (ImageView) view
					.findViewById(R.id.showTweetProfileImage);
			int userId = mCursor.getInt(mCursor.getColumnIndex(TweetsContentProvider.COL_USER_ROW_ID));
			Uri imageUri = Uri.parse("content://"
					+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS + "/" + userId);
			InputStream is;

			try {
				is = resolver.openInputStream(imageUri);
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
		userInfoView = (LinearLayout) view.findViewById(R.id.showTweetUserInfo);

		userInfoView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent i = new Intent(activity, UserProfileActivity.class);
				i.putExtra("rowId", mUserRowId);
				i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(i);
			}

		});

	}

	private class InternalURLSpan extends ClickableSpan {
		String url;

		public InternalURLSpan(String url) {
			this.url = url;
		}

		@Override
		public void onClick(View widget) {

			if ((locHelper != null && locHelper.getCount() > 0)
					&& statsDBHelper != null
					&& cm.getActiveNetworkInfo() != null) {
				locHelper.unRegisterLocationListener();
				statsDBHelper.insertRow(locHelper.getLocation(), cm
						.getActiveNetworkInfo().getTypeName(),
						StatisticsDBHelper.LINK_CLICKED, url, System
								.currentTimeMillis());
			}

			if (cm.getActiveNetworkInfo() != null
					&& cm.getActiveNetworkInfo().isConnected()) {
				// if there is active internet access, use normal browser
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
				startActivity(intent);
			} else {
				String[] filePath = { HtmlPage.HTML_PATH + "/"
						+ LoginActivity.getTwitterId(activity) };
				PackageManager pm;
				Cursor c;

				switch (sdCardHelper.checkFileType(url)) {

				case SDCardHelper.TYPE_XML:

					Cursor cursorInfo = htmlDbHelper.getPageInfo(url);
					if (cursorInfo != null) {

						String filename = null;
						if (!cursorInfo.isNull(cursorInfo
								.getColumnIndex(HtmlPage.COL_FILENAME))) {
							filename = cursorInfo.getString(cursorInfo
									.getColumnIndex(HtmlPage.COL_FILENAME));

							Log.i(TAG,
									"length: "
											+ sdCardHelper.getFileFromSDCard(
													filePath[0], filename)
													.length() + " bytes");

							if (sdCardHelper.getFileFromSDCard(filePath[0],
									filename).length() > 0) {

								// set up our own web view
								Intent intentToWeb = new Intent(activity,
										WebViewActivity.class);
								intentToWeb.putExtra("url", url);
								intentToWeb.putExtra("filename", filename);
								startActivity(intentToWeb);

							} else {
								Toast.makeText(activity,
										getString(R.string.faulty_page),
										Toast.LENGTH_LONG).show();

							}

						} else
							Toast.makeText(activity,
									getString(R.string.file_not_exists),
									Toast.LENGTH_LONG).show();

					} else {
						Log.i(TAG, "content values null");
					}

					break;

				case SDCardHelper.TYPE_PDF:
					Log.i(TAG, "view pdf");
					c = htmlDbHelper.getPageInfo(url);
					if (c != null) {
						Intent intentToPDF = new Intent(
								Intent.ACTION_VIEW,
								Uri.fromFile(sdCardHelper.getFileFromSDCard(
										filePath[0],
										c.getString(c
												.getColumnIndex(HtmlPage.COL_FILENAME)))));
						pm = activity.getPackageManager();
						List<ResolveInfo> activitiesPDF = pm
								.queryIntentActivities(intentToPDF, 0);
						if (activitiesPDF.size() > 0) {
							startActivity(intentToPDF);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(activity, R.string.no_valid_pdf,
									Toast.LENGTH_LONG).show();
						}
						c.close();
					}

					break;
				case SDCardHelper.TYPE_PNG:
				case SDCardHelper.TYPE_GIF:
				case SDCardHelper.TYPE_JPG:
					Log.i(TAG, "view picture");
					c = htmlDbHelper.getPageInfo(url);
					if (c != null) {

						File picFile = sdCardHelper
								.getFileFromSDCard(filePath[0], c.getString(c
										.getColumnIndex(HtmlPage.COL_FILENAME)));
						Intent intentToPic = new Intent(Intent.ACTION_VIEW);
						intentToPic.setDataAndType(
								Uri.parse("file://"
										+ Uri.fromFile(picFile).getPath()),
								"image/*");
						pm = activity.getPackageManager();
						List<ResolveInfo> activitiesPic = pm
								.queryIntentActivities(intentToPic, 0);
						if (activitiesPic.size() > 0) {
							startActivity(intentToPic);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(activity,
									R.string.no_valid_pictures,
									Toast.LENGTH_LONG).show();
						}
						c.close();
					}

					break;
				case SDCardHelper.TYPE_MP3:
					Log.i(TAG, "play audio");

					c = htmlDbHelper.getPageInfo(url);
					if (c != null) {

						File mp3File = sdCardHelper
								.getFileFromSDCard(filePath[0], c.getString(c
										.getColumnIndex(HtmlPage.COL_FILENAME)));
						Intent intentToMp3 = new Intent(Intent.ACTION_VIEW);
						intentToMp3.setDataAndType(
								Uri.parse("file://"
										+ Uri.fromFile(mp3File).getPath()),
								"audio/mp3");
						pm = activity.getPackageManager();
						List<ResolveInfo> activitiesAudio = pm
								.queryIntentActivities(intentToMp3, 0);
						if (activitiesAudio.size() > 0) {
							startActivity(intentToMp3);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(activity, R.string.no_valid_audio,
									Toast.LENGTH_LONG).show();
						}
						c.close();
					}

					break;
				case SDCardHelper.TYPE_MP4:
				case SDCardHelper.TYPE_RMVB:
				case SDCardHelper.TYPE_FLV:
					Log.i(TAG, "play video");

					c = htmlDbHelper.getPageInfo(url);
					if (c != null) {

						File videoFile = sdCardHelper
								.getFileFromSDCard(filePath[0], c.getString(c
										.getColumnIndex(HtmlPage.COL_FILENAME)));
						Intent intentToVideo = new Intent(Intent.ACTION_VIEW);
						intentToVideo.setDataAndType(
								Uri.parse("file://"
										+ Uri.fromFile(videoFile).getPath()),
								"video/flv");
						pm = activity.getPackageManager();
						List<ResolveInfo> activitiesVideo = pm
								.queryIntentActivities(intentToVideo, 0);
						if (activitiesVideo.size() > 0) {
							startActivity(intentToVideo);
						} else {
							// Do something else here. Maybe pop up a Dialog or
							// Toast
							Toast.makeText(activity, R.string.no_valid_video,
									Toast.LENGTH_LONG).show();
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
		screenNameView.setText("@" + mScreenName);
		realNameView.setText(mCursor.getString(mCursor
				.getColumnIndex(TwitterUsers.COL_NAME)));
		mText = mCursor.getString(mCursor.getColumnIndex(Tweets.COL_TEXT));

		SpannableString str = new SpannableString(Html.fromHtml(mText, null,
				new TweetTagHandler(activity)));

		try {
			String substr = str.toString();
			String[] strarr = substr.split(" ");

			// save the urls of the tweet in a list
			int passedLen = 0;
			for (String subStrarr : strarr) {

				if (subStrarr.indexOf("http://") >= 0
						|| subStrarr.indexOf("https://") >= 0) {
					int offset = Math.max(subStrarr.indexOf("http://"),
							subStrarr.indexOf("https://"));

					htmlUrls.add(subStrarr.substring(offset));
					int startIndex = passedLen + offset;
					int endIndex = passedLen + subStrarr.length() - 1;
					str.setSpan(
							new InternalURLSpan(subStrarr.substring(offset)),
							startIndex, endIndex, Spannable.SPAN_MARK_MARK);
				}
				passedLen = passedLen + subStrarr.length() + 1;
			}

		} catch (Exception ex) {
		}
		tweetTextView.setText(str);
		tweetTextView.setMovementMethod(LinkMovementMethod.getInstance());

		StringBuffer tweetCreationDetails = new StringBuffer();

		// created at
		tweetCreationDetails.append(DateFormat
				.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
				.format(new Date(
						mCursor.getLong(mCursor.getColumnIndex(Tweets.COL_CREATED))))
				.toString());

		// created via (if available)
		if (mCursor.getString(mCursor.getColumnIndex(Tweets.COL_SOURCE)) != null) {
			tweetCreationDetails.append(getResources().getString(R.string.via));
			tweetCreationDetails.append(Html.fromHtml(mCursor.getString(mCursor
					.getColumnIndex(Tweets.COL_SOURCE))));
		}
		tvTweetCreationDetails.setText(tweetCreationDetails);

		// retweeted by
		String retweeted_by = mCursor.getString(mCursor
				.getColumnIndex(Tweets.COL_RETWEETED_BY));
		if (retweeted_by != null) {
			tvRetweetedBy.setText(getString(R.string.retweeted_by) + " @"
					+ retweeted_by);
			tvRetweetedBy.setVisibility(View.VISIBLE);
		} else {
			tvRetweetedBy.setVisibility(View.GONE);
		}

	}

	/**
	 * On resume
	 */
	@Override
	public void onResume() {
		super.onResume();
		locHelper.registerLocationListener();
		observer = new TweetContentObserver(handler);
		mCursor.registerContentObserver(observer);

	}

	/**
	 * On Pause
	 */
	@Override
	public void onPause() {

		super.onPause();
		if (locHelper != null)
			locHelper.unRegisterLocationListener();
		if (mCursor != null) {
			if (observer != null)
				try {
					mCursor.unregisterContentObserver(observer);
				} catch (IllegalStateException ex) {
					// Log.e(TAG,"error unregistering observer",ex);
				}
		}

	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();

		if (userInfoView != null)
			userInfoView.setOnClickListener(null);
		if (retweetButton != null)
			retweetButton.setOnClickListener(null);
		if (deleteButton != null)
			deleteButton.setOnClickListener(null);
		if (replyButton != null)
			replyButton.setOnClickListener(null);
		if (favoriteButton != null)
			favoriteButton.setOnClickListener(null);
		observer = null;
		if (mCursor != null)
			mCursor.close();
		TwimightBaseActivity.unbindDrawables(getActivity().findViewById(
				R.id.showTweetRoot));

	}

	/**
	 * Asks the user if she wants to delete a tweet.
	 */
	private void showDeleteDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(R.string.delete_tweet)
				.setCancelable(false)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {

								loadCursor();

								String delPhotoName = mCursor.getString(mCursor
										.getColumnIndex(Tweets.COL_MEDIA));

								if (delPhotoName != null) {
									photoPath = Tweets.PHOTO_PATH + "/"
											+ userID;
									String[] filePath = { photoPath };
									if (sdCardHelper.checkSDState(filePath)) {
										File photoFile = sdCardHelper
												.getFileFromSDCard(photoPath,
														delPhotoName);// photoFileParent,
																		// photoFilename));
										photoFile.delete();
									}

								}

								// delete html pages
								if (!htmlUrls.isEmpty()) {
									for (String htmlUrl : htmlUrls) {
										Cursor cursorHtml = htmlDbHelper
												.getPageInfo(htmlUrl);
										if (cursorHtml != null) {
											String[] filePath = { HtmlPage.HTML_PATH
													+ "/"
													+ LoginActivity
															.getTwitterId(activity) };
											if (sdCardHelper
													.checkSDState(filePath)) {
												File htmlFile = sdCardHelper
														.getFileFromSDCard(
																filePath[0],
																cursorHtml
																		.getString(cursorHtml
																				.getColumnIndex(HtmlPage.COL_FILENAME)));// photoFileParent,
																															// photoFilename));
												htmlFile.delete();
												htmlDbHelper
														.deletePage(htmlUrl);
											}

											htmlDbHelper.deletePage(htmlUrl);

										}
									}
								}

								if (!mCursor.isNull((mCursor.getColumnIndex(Tweets.COL_TID))))
									resolver.update(uri, setDeleteFlag(mFlags),
											null, null);
								else {
									resolver.delete(uri, null, null);
								}
								listener.onDelete();
							}
						})
				.setNegativeButton(R.string.no,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Asks the user how to retweet a tweet (old or new style)
	 */
	private void showRetweetDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setMessage(R.string.modify_tweet)
				.setCancelable(false)
				.setPositiveButton(R.string.yes,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								Intent i = new Intent(activity,
										ComposeTweetActivity.class);
								i.putExtra("text", "RT @" + mScreenName + " "
										+ mText);
								i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
								startActivity(i);
							}
						})
				.setNegativeButton(R.string.no,
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								resolver.update(uri, setRetweetFlag(mFlags),
										null, null);
								loadCursor();
								dialog.cancel();
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
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
	 * Adds the delete flag and returns the flags in a content value structure
	 * to send to the content provider
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues setDeleteFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.COL_FLAGS, flags | Tweets.FLAG_TO_DELETE);
		cv.put(Tweets.COL_BUFFER, mBuffer);
		return cv;
	}

	/**
	 * Adds the to retweet flag and returns the flags in a content value
	 * structure to send to the content provider
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues setRetweetFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.COL_FLAGS, flags | Tweets.FLAG_TO_RETWEET);

		if (PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
				"prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true) {

			cv.put(Tweets.COL_BUFFER, mBuffer | Tweets.BUFFER_DISASTER);
		} else
			cv.put(Tweets.COL_BUFFER, mBuffer);
		return cv;
	}

	/**
	 * Adds the favorite flag and returns the flags in a content value structure
	 * to send to the content provider
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues setFavoriteFlag(int flags) {
		ContentValues cv = new ContentValues();

		loadCursor();

		try {
			// set favorite flag und clear unfavorite flag
			if ((mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER)) & Tweets.BUFFER_FAVORITES) != 0)
				cv.put(Tweets.COL_FLAGS, (flags & ~Tweets.FLAG_TO_UNFAVORITE));
			else
				cv.put(Tweets.COL_FLAGS, (flags | Tweets.FLAG_TO_FAVORITE)
						& (~Tweets.FLAG_TO_UNFAVORITE));
			// put in favorites bufer
			cv.put(Tweets.COL_BUFFER, mBuffer | Tweets.BUFFER_FAVORITES);
			return cv;
		} catch (Exception ex) {
			Log.e(TAG, "error: ", ex);
			return null;
		}

	}

	/**
	 * Clears the favorite flag and returns the flags in a content value
	 * structure to send to the content provider
	 * 
	 * @param flags
	 * @return
	 */
	private ContentValues clearFavoriteFlag(int flags) {
		ContentValues cv = new ContentValues();

		// clear favorite flag and set unfavorite flag
		if ((mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER)) & Tweets.BUFFER_FAVORITES) != 0)
			cv.put(Tweets.COL_FLAGS, (flags & (~Tweets.FLAG_TO_FAVORITE))
					| Tweets.FLAG_TO_UNFAVORITE);
		else
			cv.put(Tweets.COL_FLAGS, (flags & (~Tweets.FLAG_TO_FAVORITE)));

		if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_TID))) {
			cv.put(Tweets.COL_BUFFER, mBuffer);
		} else {
			cv.put(Tweets.COL_BUFFER, mBuffer & (~Tweets.BUFFER_FAVORITES));
		}
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

			/*
			 * close the old cursor if(c!=null) { c.close(); }
			 */

			// and get a new one
			uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/"
					+ Tweets.TWEETS + "/" + mRowId);
			mCursor = resolver.query(uri, null, null, null, null);
			if (mCursor.getCount() == 1) {

				mCursor.moveToFirst();
				if (mCursor.getColumnIndex(Tweets.COL_FLAGS) > -1)
					mFlags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
				// update the views
				handleTweetFlags();
			}

		}
	}

}
