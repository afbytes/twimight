package ch.ethz.twimight.views;

import java.util.LinkedList;
import java.util.List;

import twitter4j.MediaEntity;
import twitter4j.URLEntity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.ComposeTweetActivity;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.twitter.TweetActionService;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.util.Serialization;

public class TweetButtonBar extends FrameLayout {

	private static final String TAG = TweetButtonBar.class.getName();

	private final long mRowId;
	private ContentObserver mObserver;

	private ImageButton mRetweetButton;
	private ImageButton mDeleteButton;
	private ImageButton mReplyButton;
	private ImageButton mFavoriteButton;
	private ImageButton mOfflineButton;

	private int mFlags;
	private boolean mIsFavorited;
	private int mHtmlStatus;
	private String mScreenName;
	private String mText;

	private Uri mUri;

	private Cursor mCursor;

	public TweetButtonBar(Context context, long rowId) {
		super(context);
		((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.tweet_button_bar,
				this, true);
		captureViews();
		mRowId = rowId;
		mUri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + mRowId);
	}

	@Override
	protected void onAttachedToWindow() {
		updateCursor();
		super.onAttachedToWindow();
	}

	@Override
	protected void onDetachedFromWindow() {
		discardCursor();
		super.onDetachedFromWindow();
	}

	private void updateCursor() {
		discardCursor();
		mCursor = getContext().getContentResolver().query(mUri, null, null, null, null);

		if (mCursor != null && mCursor.getCount() > 0) {
			mCursor.moveToFirst();
			mObserver = new TweetObserver(new Handler());
			mCursor.registerContentObserver(mObserver);
			mFlags = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_FLAGS));
			mScreenName = mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_SCREENNAME));
			mText = mCursor.getString(mCursor.getColumnIndex(Tweets.COL_TEXT_PLAIN));
			setupButtons();
		} else {
			mCursor = null; // discard completely so that we don't try to
							// unregister observer from empty cursor
		}
	}

	private final class TweetObserver extends ContentObserver {
		public TweetObserver(Handler handler) {
			super(handler);
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

	private void discardCursor() {
		if (mCursor != null) {
			mCursor.unregisterContentObserver(mObserver);
			mCursor.close();
			mCursor = null;
		}
	}

	private void captureViews() {
		mRetweetButton = (ImageButton) findViewById(R.id.showTweetRetweet);
		mDeleteButton = (ImageButton) findViewById(R.id.showTweetDelete);
		mReplyButton = (ImageButton) findViewById(R.id.showTweetReply);
		mFavoriteButton = (ImageButton) findViewById(R.id.showTweetFavorite);
		mOfflineButton = (ImageButton) findViewById(R.id.showTweetOfflineview);
	}

	private void setupButtons() {
		String userString = Long.toString(mCursor.getLong(mCursor.getColumnIndex(TwitterUsers.COL_TWITTERUSER_ID)));
		String localUserString = LoginActivity.getTwitterId(getContext());

		// Retweet Button

		// we do not show the retweet button for (1) tweets from the local user,
		// (2) tweets which have been flagged to retweeted and (3) tweets which
		// have been marked as retweeted
		if (userString.equals(localUserString) || ((mFlags & Tweets.FLAG_TO_RETWEET) > 0)
				|| (mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_RETWEETED)) > 0)) {
			mRetweetButton.setVisibility(Button.GONE);
		} else {
			mRetweetButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					showRetweetDialog();
					mRetweetButton.setVisibility(Button.GONE);
				}

			});
		}

		// Delete Button

		if (userString.equals(localUserString)) {
			mDeleteButton.setVisibility(ImageButton.VISIBLE);
			if ((mFlags & Tweets.FLAG_TO_DELETE) == 0) {
				mDeleteButton.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						showDeleteDialog();
					}
				});

			} else {
				mDeleteButton.setVisibility(ImageButton.GONE);
			}
		} else {
			mDeleteButton.setVisibility(ImageButton.GONE);
		}

		// Reply button: we show it only if we have a Tweet ID!

		if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_TID))
				|| PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean("prefDisasterMode",
						Constants.DISASTER_DEFAULT_ON) == true) {
			mReplyButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(getContext(), ComposeTweetActivity.class);
					if (!mCursor.isNull(mCursor.getColumnIndex(Tweets.COL_TID)))
						i.putExtra("isReplyTo", mCursor.getLong(mCursor.getColumnIndex(Tweets.COL_TID)));
					else
						i.putExtra("isReplyTo", -1);
					i.putExtra("text", "@" + mCursor.getString(mCursor.getColumnIndex(TwitterUsers.COL_SCREENNAME))
							+ " ");
					getContext().startActivity(i);
				}
			});
		} else {
			mReplyButton.setVisibility(Button.GONE);
		}

		// Favorite button

		mIsFavorited = ((mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_BUFFER)) & Tweets.BUFFER_FAVORITES) != 0)
				|| ((mFlags & Tweets.FLAG_TO_FAVORITE) > 0);
		if (mIsFavorited && !((mFlags & Tweets.FLAG_TO_UNFAVORITE) > 0)) {
			mFavoriteButton.setImageResource(R.drawable.ic_favorite_on);
		}
		mFavoriteButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (mIsFavorited) {
					// unfavorite
					Intent intent = new Intent(getContext(), TweetActionService.class);
					intent.putExtra(TweetActionService.EXTRA_KEY_ROW_ID, mRowId);
					intent.putExtra(TweetActionService.EXTRA_KEY_ACTION, TweetActionService.EXTRA_ACTION_UNFAVORITE);
					getContext().startService(intent);

					((ImageButton) v).setImageResource(R.drawable.ic_favorite_off);
					mIsFavorited = false;

				} else {
					// favorite
					Intent intent = new Intent(getContext(), TweetActionService.class);
					intent.putExtra(TweetActionService.EXTRA_KEY_ROW_ID, mRowId);
					intent.putExtra(TweetActionService.EXTRA_KEY_ACTION, TweetActionService.EXTRA_ACTION_FAVORITE);
					getContext().startService(intent);

					((ImageButton) v).setImageResource(R.drawable.ic_favorite_on);
					mIsFavorited = true;
				}

			}

		});

		// Cache links button

		// get the html status of this tweet
		if (hasUncachedLinks()) {
			mOfflineButton.setVisibility(View.VISIBLE);
			mOfflineButton.setOnClickListener(new OnClickListener() {

				@Override
				public void onClick(View v) {
					Intent intent = new Intent(getContext(), TweetActionService.class);
					intent.putExtra(TweetActionService.EXTRA_KEY_ROW_ID, mRowId);
					intent.putExtra(TweetActionService.EXTRA_KEY_ACTION, TweetActionService.EXTRA_ACTION_CACHE_LINKS);
					getContext().startService(intent);

					Toast.makeText(getContext(), getResources().getString(R.string.download_toast), Toast.LENGTH_SHORT)
							.show();
					// TODO: Fix download functionality and apply appropriate
					// icon
					// offlineButton
					// .setImageResource(R.drawable.btn_twimight_archive_on);
				}

			});
		} else {
			mOfflineButton.setVisibility(View.GONE);
		}
	}

	// TODO: duplicate code in TweetActionService
	private boolean hasUncachedLinks() {
		boolean hasUncachedLinks = false;
		mHtmlStatus = mCursor.getInt(mCursor.getColumnIndex(Tweets.COL_HTML_PAGES));
		if (mHtmlStatus != 0) {
			List<String> linkUrls = new LinkedList<String>();
			byte[] serializedMediaEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_MEDIA_ENTITIES));
			MediaEntity[] mediaEntities = Serialization.deserialize(serializedMediaEntities);
			for (URLEntity mediaEntity : mediaEntities) {
				linkUrls.add(mediaEntity.getURL());
			}
			byte[] serializedUrlEntities = mCursor.getBlob(mCursor.getColumnIndex(Tweets.COL_URL_ENTITIES));
			URLEntity[] urlEntities = Serialization.deserialize(serializedUrlEntities);
			for (URLEntity urlEntity : urlEntities) {
				linkUrls.add(urlEntity.getURL());
			}

			HtmlPagesDbHelper htmlDbHelper = new HtmlPagesDbHelper(getContext());
			htmlDbHelper.open();
			String ownTwitterId = LoginActivity.getTwitterId(getContext());
			for (String linkUrl : linkUrls) {
				Cursor cursorInfo = htmlDbHelper.getPageInfo(linkUrl);
				String filename = null;
				boolean fileStatusNormal = true;
				if (cursorInfo != null) {
					// check if file status normal, exists and size
					String[] filePath = { HtmlPage.HTML_PATH + "/" + ownTwitterId };
					SDCardHelper sdCardHelper = new SDCardHelper();
					if (sdCardHelper.checkSDState(filePath)) {

						if (!cursorInfo.isNull(cursorInfo.getColumnIndex(HtmlPage.COL_FILENAME))) {
							filename = cursorInfo.getString(cursorInfo.getColumnIndex(HtmlPage.COL_FILENAME));

							if (sdCardHelper.getFileFromSDCard(filePath[0], filename).length() <= 1) {
								fileStatusNormal = false;
							}
						}

					}

				}
				if (cursorInfo == null || (filename == null) || !fileStatusNormal) {
					hasUncachedLinks = true;
					break;
				}
			}
		}
		return hasUncachedLinks;
	}

	/**
	 * Asks the user how to retweet a tweet (old or new style)
	 */
	private void showRetweetDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setMessage(R.string.modify_tweet).setCancelable(false)
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					// modify before retweeting
					public void onClick(DialogInterface dialog, int id) {
						Intent i = new Intent(getContext(), ComposeTweetActivity.class);
						i.putExtra("text", "RT @" + mScreenName + " " + mText);
						i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						getContext().startActivity(i);
					}
				}).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					// don't modify, retweet directly
					public void onClick(DialogInterface dialog, int id) {
						Intent intent = new Intent(getContext(), TweetActionService.class);
						intent.putExtra(TweetActionService.EXTRA_KEY_ROW_ID, mRowId);
						intent.putExtra(TweetActionService.EXTRA_KEY_ACTION, TweetActionService.EXTRA_ACTION_RETWEET);
						getContext().startService(intent);
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

	/**
	 * Asks the user if she wants to delete a tweet.
	 */
	private void showDeleteDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		builder.setMessage(R.string.delete_tweet).setCancelable(false)
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int id) {
						Intent intent = new Intent(getContext(), TweetActionService.class);
						intent.putExtra(TweetActionService.EXTRA_KEY_ROW_ID, mRowId);
						intent.putExtra(TweetActionService.EXTRA_KEY_ACTION, TweetActionService.EXTRA_ACTION_DELETE);
						getContext().startService(intent);
						dialog.cancel();
					}
				}).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
		AlertDialog alert = builder.create();
		alert.show();
	}

}
