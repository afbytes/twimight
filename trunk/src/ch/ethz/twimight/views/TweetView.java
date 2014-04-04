package ch.ethz.twimight.views;

import java.util.Locale;

import twitter4j.MediaEntity;
import twitter4j.TweetEntity;
import twitter4j.URLEntity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.activities.PhotoViewActivity;
import ch.ethz.twimight.activities.UserProfileActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.twitter.EntityQueue;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TweetsContentProvider;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.ImageUrlHelper;
import ch.ethz.twimight.util.Serialization;

public class TweetView extends FrameLayout {

	private final LinearLayout mContainer;
	private final LinearLayout mImageContainer;
	private final View mModeStripe;
	private final TextView mTvUsername;
	private final TextView mTvCreatedAt;
	private final TextView mTvTweetText;
	private final TextView mTvRetweetedBy;
	private final ClickableImageView mIvProfileImage;
	private final ImageView mIvPendingIcon;
	private final ImageView mIvVerifiedIcon;
	private final ImageView mIvRetweetedIcon;
	private final ImageView mIvFavoriteIcon;
	private final ImageView mIvDownloadIcon;
	private TweetButtonBar mTweetButtonBar;

	private final int mAccentColorDisasterMode2;
	private final int mAccentColorNormalMode2;

	private final String mOwnHandle;
	private final String mOwnTwitterId;

	public TweetView(Context context) {
		super(context);
		mAccentColorNormalMode2 = context.getResources().getColor(R.color.accent_normalmode_2);
		mAccentColorDisasterMode2 = context.getResources().getColor(R.color.accent_disastermode_2);

		mOwnHandle = "@" + LoginActivity.getTwitterScreenname(context).toLowerCase(Locale.getDefault());
		mOwnTwitterId = LoginActivity.getTwitterId(context);

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.tweet_row, this, true);
		mModeStripe = findViewById(R.id.modeStripe);
		mTvUsername = (TextView) findViewById(R.id.tvUsername);
		mTvCreatedAt = (TextView) findViewById(R.id.tvCreatedAt);
		mTvTweetText = (TextView) findViewById(R.id.tvTweetText);
		mTvRetweetedBy = (TextView) findViewById(R.id.tvRetweetedBy);
		mIvProfileImage = (ClickableImageView) findViewById(R.id.ivProfileImage);
		mIvPendingIcon = (ImageView) findViewById(R.id.ivPendingIcon);
		mIvVerifiedIcon = (ImageView) findViewById(R.id.ivVerifiedIcon);
		mIvRetweetedIcon = (ImageView) findViewById(R.id.ivRetweetedIcon);
		mIvFavoriteIcon = (ImageView) findViewById(R.id.ivFavoriteIcon);
		mIvDownloadIcon = (ImageView) findViewById(R.id.ivDownloadIcon);
		mContainer = (LinearLayout) findViewById(R.id.container);
		mImageContainer = (LinearLayout) findViewById(R.id.imageContainer);
	}

	@Override
	public void setBackgroundResource(int resid) {
		mContainer.setBackgroundResource(resid);
	}

	public void update(Cursor cursor, boolean showButtonBar, boolean showModeStripe) {
		// set profile image
		mIvProfileImage.setBackgroundResource(R.drawable.profile_image_placeholder);
		mIvProfileImage.setImageDrawable(null);
		String profileImageUrl = cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_PROFILE_IMAGE_URI));
		mIvProfileImage.setImageUri(profileImageUrl);
		long userRowId = cursor.getLong(cursor.getColumnIndex(TweetsContentProvider.COL_USER_ROW_ID));
		Intent profileImageClickIntent = new Intent(getContext(), UserProfileActivity.class);
		profileImageClickIntent.putExtra(UserProfileActivity.EXTRA_KEY_ROW_ID, userRowId);
		mIvProfileImage.setOnClickActivityIntent(profileImageClickIntent);

		// if we don't have a real name, we use the screen name
		if (cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME)) == null) {
			mTvUsername.setText("@" + cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_SCREEN_NAME)));
		} else {
			mTvUsername.setText(cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME)));
		}

		// set tweet text
		String tweetText = cursor.getString(cursor.getColumnIndex(Tweets.COL_TEXT));
		mTvTweetText.setText(tweetText);

		// set "created at"
		long createdAt = cursor.getLong(cursor.getColumnIndex(Tweets.COL_CREATED_AT));

		mTvCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));

		// set "retweeted by"
		int col = cursor.getColumnIndex(Tweets.COL_RETWEETED_BY);
		if (col > -1) {
			String retweeted_by = cursor.getString(col);

			if (retweeted_by != null) {
				mTvRetweetedBy.setText(getContext().getString(R.string.retweeted_by) + " " + retweeted_by);
				mTvRetweetedBy.setVisibility(View.VISIBLE);
			} else {
				mTvRetweetedBy.setVisibility(View.GONE);
			}
		}

		// downloading / downloaded?
		int col_html = cursor.getColumnIndex(Tweets.COL_HTML_PAGES);
		if (col_html > -1) {
			int hasHtml = cursor.getInt(col_html);

			mIvDownloadIcon.setVisibility(View.GONE);

			if (hasHtml == 1) {

				HtmlPagesDbHelper htmlDbHelper = new HtmlPagesDbHelper(getContext().getApplicationContext());
				htmlDbHelper.open();

				long disId = cursor.getLong(cursor.getColumnIndex(Tweets.COL_DISASTER_ID));
				Cursor curHtml = htmlDbHelper.getTweetUrls(disId);

				if (curHtml != null && curHtml.getCount() > 0) {
					mIvDownloadIcon.setVisibility(View.VISIBLE);

					if (!htmlDbHelper.allPagesStored(curHtml)) {
						// downloading
						mIvDownloadIcon.setImageResource(R.drawable.ic_small_downloading);
					} else {
						// downloaded
						mIvDownloadIcon.setImageResource(R.drawable.ic_small_downloaded);
					}
				}

			}
		}

		// pending?
		int flags = cursor.getInt(cursor.getColumnIndex(Tweets.COL_FLAGS));
		boolean isPending = (flags > 0);
		if (isPending) {
			mIvPendingIcon.setVisibility(View.VISIBLE);
		} else {
			mIvPendingIcon.setVisibility(View.GONE);
		}

		// retweeted?
		boolean retweeted = cursor.getInt(cursor.getColumnIndex(Tweets.COL_RETWEETED)) > 0;
		if (retweeted) {
			mIvRetweetedIcon.setVisibility(View.VISIBLE);
		} else {
			mIvRetweetedIcon.setVisibility(View.GONE);
		}

		// favorited?
		int buffer = cursor.getInt(cursor.getColumnIndex(Tweets.COL_BUFFER));
		boolean favorited = (((buffer & Tweets.BUFFER_FAVORITES) != 0) && ((flags & Tweets.FLAG_TO_UNFAVORITE) == 0))
				|| ((flags & Tweets.FLAG_TO_FAVORITE) > 0);
		if (favorited) {
			mIvFavoriteIcon.setVisibility(View.VISIBLE);
		} else {
			mIvFavoriteIcon.setVisibility(View.GONE);
		}

		// disaster/normal? -> select accent color / set verified icon
		int accentColor;
		if ((buffer & Tweets.BUFFER_DISASTER) != 0 || (buffer & Tweets.BUFFER_MYDISASTER) != 0) {
			// set pressed state background color
			// select accent color
			accentColor = mAccentColorDisasterMode2;
			// set verified icon for disaster tweets
			mIvVerifiedIcon.setVisibility(View.VISIBLE);
			if (cursor.getInt(cursor.getColumnIndex(Tweets.COL_IS_VERIFIED)) > 0) {
				mIvVerifiedIcon.setImageResource(R.drawable.ic_small_verified);
			} else {
				mIvVerifiedIcon.setImageResource(R.drawable.ic_small_unverified);
			}
		} else {
			// set pressed state background color
			// select accent color
			mIvVerifiedIcon.setVisibility(View.GONE);
			accentColor = mAccentColorNormalMode2;
		}

		// set side stripe color
		if (showModeStripe) {
			mModeStripe.setVisibility(VISIBLE);
			mModeStripe.setBackgroundColor(accentColor);
		} else {
			mModeStripe.setVisibility(GONE);
		}

		// highlight own tweet
		boolean ownTweet = Long.toString(cursor.getLong(cursor.getColumnIndex(Tweets.COL_USER_TID))).equals(
				mOwnTwitterId);
		if (ownTweet) {
			mTvUsername.setTextColor(accentColor);
			// no verified icon for own tweets
			mIvVerifiedIcon.setVisibility(ImageView.GONE);
		} else {
			mTvUsername.setTextColor(getContext().getResources().getColor(R.color.dark_text));
		}

		// highlight mentions
		Spannable tweetSpannable = new SpannableString(tweetText);
		String lowerCaseTweetText = tweetText.toLowerCase(Locale.getDefault());
		int start = lowerCaseTweetText.indexOf(mOwnHandle);
		int end;
		while (start != -1) {
			end = start + mOwnHandle.length();
			tweetSpannable
					.setSpan(new ForegroundColorSpan(accentColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			start = lowerCaseTweetText.indexOf(mOwnHandle, end);
		}
		mTvTweetText.setText(tweetSpannable);

		long rowId = cursor.getLong(cursor.getColumnIndex(Tweets.COL_ROW_ID));
		if (showButtonBar) {
			if (mTweetButtonBar == null) {
				mTweetButtonBar = new TweetButtonBar(getContext(), rowId);
				mContainer.addView(mTweetButtonBar);
			}
		} else {
			if (mTweetButtonBar != null) {
				mContainer.removeView(mTweetButtonBar);
				mTweetButtonBar = null;
			}
		}

		// display media
		mImageContainer.removeAllViews();
		byte[] serializedMediaEntities = cursor.getBlob(cursor.getColumnIndex(Tweets.COL_MEDIA_ENTITIES));
		MediaEntity[] mediaEntities = Serialization.deserialize(serializedMediaEntities);
		byte[] serializedUrlEntities = cursor.getBlob(cursor.getColumnIndex(Tweets.COL_URL_ENTITIES));
		URLEntity[] urlEntities = Serialization.deserialize(serializedUrlEntities);
		EntityQueue allEntities = new EntityQueue(mediaEntities, urlEntities);
		while (!allEntities.isEmpty()) {
			TweetEntity entity = allEntities.remove();
			if (entity instanceof MediaEntity) {
				showImage(((MediaEntity) entity).getMediaURL());
			} else if (entity instanceof URLEntity) {
				String imageUrl = ImageUrlHelper.getImageUrl(((URLEntity) entity).getExpandedURL());
				if (imageUrl != null) {
					showImage(imageUrl);
				}
			}

		}
	}

	/**
	 * Creates an image view, sets it to display the specified source, attaches
	 * a click listener that launches the PhotoViewActivity and adds it to the
	 * image container.
	 * 
	 * @param imageUri
	 *            remote picture URL of a picture contained in the tweet or
	 *            local file URI
	 */
	private void showImage(String imageUri) {
		Intent imageClickIntent = new Intent(getContext(), PhotoViewActivity.class);
		imageClickIntent.putExtra(PhotoViewActivity.EXTRA_KEY_IMAGE_URI, imageUri);
		ClickableImageView imageView = new ClickableImageView(getContext(), imageUri, imageClickIntent);
		imageView.setScaleType(ScaleType.CENTER_INSIDE);
		imageView.setAdjustViewBounds(true);
		LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT);
		layoutParams.setMargins(0, (int) getContext().getResources().getDimension(R.dimen.unit_step), 0, 0);
		mImageContainer.addView(imageView, 0, layoutParams);
	}
}
