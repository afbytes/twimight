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

package ch.ethz.twimight.net.twitter;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.util.AsyncImageLoader;

/**
 * Cursor adapter for a cursor containing tweets.
 */
public class TweetAdapter extends CursorAdapter {

	// private static final String TAG = "TweetAdapter";

	private HtmlPagesDbHelper htmlDbHelper;

	private final AsyncImageLoader mImageLoader;

	private final String mOwnHandle;
	private final String mOwnTwitterId;

	private static class ViewHolder {
		private final View modeStripe;
		private final TextView tvUsername;
		private final TextView tvCreatedAt;
		private final TextView tvTweetText;
		private final TextView tvRetweetedBy;
		private final ImageView ivProfileImage;
		private final ImageView ivPendingIcon;
		private final ImageView ivVerifiedIcon;
		private final ImageView ivRetweetedIcon;
		private final ImageView ivFavoriteIcon;
		private final ImageView ivDownloadIcon;

		private ViewHolder(View row) {
			modeStripe = row.findViewById(R.id.modeStripe);
			tvUsername = (TextView) row.findViewById(R.id.tvUsername);
			tvCreatedAt = (TextView) row.findViewById(R.id.tvCreatedAt);
			tvTweetText = (TextView) row.findViewById(R.id.tvTweetText);
			tvRetweetedBy = (TextView) row.findViewById(R.id.tvRetweetedBy);
			ivProfileImage = (ImageView) row.findViewById(R.id.ivProfileImage);
			ivPendingIcon = (ImageView) row.findViewById(R.id.ivPendingIcon);
			ivVerifiedIcon = (ImageView) row.findViewById(R.id.ivVerifiedIcon);
			ivRetweetedIcon = (ImageView) row.findViewById(R.id.ivRetweetedIcon);
			ivFavoriteIcon = (ImageView) row.findViewById(R.id.ivFavoriteIcon);
			ivDownloadIcon = (ImageView) row.findViewById(R.id.ivDownloadIcon);
		}
	}

	/** Constructor */
	public TweetAdapter(Context context, Cursor c) {
		super(context, c, true);
		mImageLoader = new AsyncImageLoader(context);
		mOwnHandle = "@" + LoginActivity.getTwitterScreenname(context).toLowerCase();
		mOwnTwitterId = LoginActivity.getTwitterId(context);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) parent.getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View row = inflater.inflate(R.layout.tweet_row, null);
		ViewHolder viewHolder = new ViewHolder(row);
		row.setTag(viewHolder);

		return row;
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		// super.bindView(row, context, cursor);

		ViewHolder holder = (ViewHolder) row.getTag();

		// set profile image
		holder.ivProfileImage.setBackgroundResource(R.drawable.profile_image_placeholder);
		holder.ivProfileImage.setImageDrawable(null);
		if (!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH))) {
			int userRowId = cursor.getInt(cursor.getColumnIndex(TweetsContentProvider.COL_USER_ROW_ID));
			Uri imageUri = Uri.parse("content://" + TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
					+ TwitterUsers.TWITTERUSERS + "/" + userRowId);
			mImageLoader.loadImage(imageUri, holder.ivProfileImage);
		}

		// if we don't have a real name, we use the screen name
		if (cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME)) == null) {
			holder.tvUsername.setText("@" + cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
		} else {
			holder.tvUsername.setText(cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME)));
		}

		// set tweet text
		String tweetText = cursor.getString(cursor.getColumnIndex(Tweets.COL_TEXT_PLAIN));
		holder.tvTweetText.setText(tweetText);

		// set "created at"
		long createdAt = cursor.getLong(cursor.getColumnIndex(Tweets.COL_CREATED));

		holder.tvCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));

		// set "retweeted by"
		int col = cursor.getColumnIndex(Tweets.COL_RETWEETED_BY);
		if (col > -1) {
			String retweeted_by = cursor.getString(col);

			if (retweeted_by != null) {
				holder.tvRetweetedBy.setText(context.getString(R.string.retweeted_by) + " @" + retweeted_by);
				holder.tvRetweetedBy.setVisibility(View.VISIBLE);
			} else {
				holder.tvRetweetedBy.setVisibility(View.GONE);
			}
		}

		// downloading / downloaded?
		int col_html = cursor.getColumnIndex(Tweets.COL_HTML_PAGES);
		if (col_html > -1) {
			int hasHtml = cursor.getInt(col_html);

			holder.ivDownloadIcon.setVisibility(View.GONE);

			if (hasHtml == 1) {

				htmlDbHelper = new HtmlPagesDbHelper(context.getApplicationContext());
				htmlDbHelper.open();

				long disId = cursor.getLong(cursor.getColumnIndex(Tweets.COL_DISASTERID));
				Cursor curHtml = htmlDbHelper.getTweetUrls(disId);

				if (curHtml != null && curHtml.getCount() > 0) {
					holder.ivDownloadIcon.setVisibility(View.VISIBLE);

					if (!htmlDbHelper.allPagesStored(curHtml)) {
						// downloading
						holder.ivDownloadIcon.setImageResource(R.drawable.ic_small_downloading);
					} else {
						// downloaded
						holder.ivDownloadIcon.setImageResource(R.drawable.ic_small_downloaded);
					}
				}

			}
		}

		// pending?
		int flags = cursor.getInt(cursor.getColumnIndex(Tweets.COL_FLAGS));
		boolean isPending = (flags > 0);
		if (isPending) {
			holder.ivPendingIcon.setVisibility(View.VISIBLE);
		} else {
			holder.ivPendingIcon.setVisibility(View.GONE);
		}

		// retweeted?
		boolean retweeted = cursor.getInt(cursor.getColumnIndex(Tweets.COL_RETWEETED)) > 0;
		if (retweeted) {
			holder.ivRetweetedIcon.setVisibility(View.VISIBLE);
		} else {
			holder.ivRetweetedIcon.setVisibility(View.GONE);
		}

		// favorited?
		int buffer = cursor.getInt(cursor.getColumnIndex(Tweets.COL_BUFFER));
		boolean favorited = (((buffer & Tweets.BUFFER_FAVORITES) != 0) && ((flags & Tweets.FLAG_TO_UNFAVORITE) == 0))
				|| ((flags & Tweets.FLAG_TO_FAVORITE) > 0);
		if (favorited) {
			holder.ivFavoriteIcon.setVisibility(View.VISIBLE);
		} else {
			holder.ivFavoriteIcon.setVisibility(View.GONE);
		}

		// disaster/normal? -> select accent color / set verified icon
		int accentColor;
		if ((buffer & Tweets.BUFFER_DISASTER) != 0 || (buffer & Tweets.BUFFER_MYDISASTER) != 0) {
			// set pressed state background color
			// select accent color
			accentColor = context.getResources().getColor(R.color.accent_disastermode_2);
			// set verified icon for disaster tweets
			holder.ivVerifiedIcon.setVisibility(View.VISIBLE);
			if (cursor.getInt(cursor.getColumnIndex(Tweets.COL_ISVERIFIED)) > 0) {
				holder.ivVerifiedIcon.setImageResource(R.drawable.ic_small_verified);
			} else {
				holder.ivVerifiedIcon.setImageResource(R.drawable.ic_small_unverified);
			}
		} else {
			// set pressed state background color
			// select accent color
			holder.ivVerifiedIcon.setVisibility(View.GONE);
			accentColor = context.getResources().getColor(R.color.accent_normalmode_2);
		}

		// set side stripe color
		holder.modeStripe.setBackgroundColor(accentColor);

		// highlight own tweet
		boolean ownTweet = Long.toString(cursor.getLong(cursor.getColumnIndex(Tweets.COL_TWITTERUSER))).equals(
				mOwnTwitterId);
		if (ownTweet) {
			holder.tvUsername.setTextColor(accentColor);
			// no verified icon for own tweets
			holder.ivVerifiedIcon.setVisibility(ImageView.GONE);
		} else {
			holder.tvUsername.setTextColor(context.getResources().getColor(R.color.dark_text));
		}

		// higlight mentions
		Spannable tweetSpannable = new SpannableString(tweetText);
		String lowerCaseTweetText = tweetText.toLowerCase();
		int start = lowerCaseTweetText.indexOf(mOwnHandle);
		int end;
		while(start!=-1){
			end = start + mOwnHandle.length();
			tweetSpannable.setSpan(new ForegroundColorSpan(accentColor), start, end,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			start = lowerCaseTweetText.indexOf(mOwnHandle, end);
		}
		holder.tvTweetText.setText(tweetSpannable);
	}

}
