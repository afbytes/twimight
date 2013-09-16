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

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;

/**
 * Cursor adapter for a cursor containing tweets.
 */
public class TweetAdapter extends SimpleCursorAdapter {

	static final String[] from = { TwitterUsers.COL_NAME };
	static final int[] to = { R.id.textUser };
	private static final String TAG = "tweet adapter";
	private HtmlPagesDbHelper htmlDbHelper;
	private final Drawable mProfileImagePlaceholder;

	private static class ViewHolder {
		View rootView;
		TextView tvUsername;
		TextView tvCreatedAt;
		TextView tvTweetText;
		TextView tvRetweetedBy;
		ImageView ivProfilePicture;
		ImageView ivPendingIcon;
		ImageView ivVerifiedIcon;
		ImageView ivRetweetedIcon;
		ImageView ivFavoriteIcon;
		ImageView ivDownloadIcon;

		long disId = -1;

	}

	/** Constructor */
	public TweetAdapter(Context context, Cursor c) {
		super(context, R.layout.row, c, from, to);
		mProfileImagePlaceholder = context.getResources().getDrawable(R.drawable.profile_image_placeholder);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) parent.getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View view = inflater.inflate(R.layout.tweet_row, null);
		createHolder(view);

		return view;
	}

	private void createHolder(View view) {
		ViewHolder holder = new ViewHolder();
		setHolderFields(view, holder);
		view.setTag(holder);
	}

	private void setHolderFields(View row, ViewHolder holder) {
		holder.rootView = row.findViewById(R.id.rootView);
		holder.tvUsername = (TextView) row.findViewById(R.id.tvUsername);
		holder.tvCreatedAt = (TextView) row.findViewById(R.id.tvCreatedAt);
		holder.tvTweetText = (TextView) row.findViewById(R.id.tvTweetText);
		holder.tvRetweetedBy = (TextView) row.findViewById(R.id.tvRetweetedBy);
		row.findViewById(R.id.tvRetweetedBy);
		holder.ivProfilePicture = (ImageView) row
				.findViewById(R.id.ivProfileImage);
		holder.ivPendingIcon = (ImageView) row.findViewById(R.id.ivPendingIcon);
		holder.ivVerifiedIcon = (ImageView) row
				.findViewById(R.id.ivVerifiedIcon);
		holder.ivRetweetedIcon = (ImageView) row
				.findViewById(R.id.ivRetweetedIcon);
		holder.ivFavoriteIcon = (ImageView) row
				.findViewById(R.id.ivFavoriteIcon);
		holder.ivDownloadIcon = (ImageView) row
				.findViewById(R.id.ivDownloadIcon);

	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View row, Context context, Cursor cursor) {
		super.bindView(row, context, cursor);

		ViewHolder holder = (ViewHolder) row.getTag();

		htmlDbHelper = new HtmlPagesDbHelper(context.getApplicationContext());
		htmlDbHelper.open();
		
		long disId = cursor.getLong(cursor
				.getColumnIndex(Tweets.COL_DISASTERID));

		// set profile image
		if (!cursor.isNull(cursor
				.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE_PATH))) {

			if (holder.disId == -1 || holder.disId != disId) {
				holder.ivProfilePicture.setBackground(mProfileImagePlaceholder);
				holder.disId = disId;
				int userRowId = cursor.getInt(cursor
						.getColumnIndex("userRowId"));
				Uri imageUri = Uri.parse("content://"
						+ TwitterUsers.TWITTERUSERS_AUTHORITY + "/"
						+ TwitterUsers.TWITTERUSERS + "/" + userRowId);
				loadBitmap(imageUri, holder.ivProfilePicture, context);
			}

		} else {
			holder.ivProfilePicture
					.setImageResource(R.drawable.profile_image_placeholder);
		}
		
		// if we don't have a real name, we use the screen name
		if (cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_NAME)) == null) {
			holder.tvUsername.setText("@"
					+ cursor.getString(cursor
							.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
		} else {
			holder.tvUsername.setText(cursor.getString(cursor
					.getColumnIndex(TwitterUsers.COL_NAME)));
		}

		// set tweet text
		holder.tvTweetText.setText(cursor.getString(cursor
				.getColumnIndex(Tweets.COL_TEXT_PLAIN)));

		// set "created at"
		long createdAt = cursor.getLong(cursor
				.getColumnIndex(Tweets.COL_CREATED));

		holder.tvCreatedAt.setText(DateUtils
				.getRelativeTimeSpanString(createdAt));

		// set "retweeted by"
		int col = cursor.getColumnIndex(Tweets.COL_RETWEETED_BY);
		if (col > -1) {
			String retweeted_by = cursor.getString(col);

			if (retweeted_by != null) {
				holder.tvRetweetedBy.setText(context
						.getString(R.string.retweeted_by) + " @" + retweeted_by);
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
			holder.ivPendingIcon.setVisibility(ImageView.VISIBLE);
		} else {
			holder.ivPendingIcon.setVisibility(ImageView.GONE);
		}

		// retweeted?
		boolean retweeted = cursor.getInt(cursor
				.getColumnIndex(Tweets.COL_RETWEETED)) > 0;
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
			holder.ivFavoriteIcon.setVisibility(ImageView.VISIBLE);
		} else {
			holder.ivFavoriteIcon.setVisibility(ImageView.GONE);
		}

		// disaster/normal? -> select accent color / set verified icon
		int accentColor;
		if ((buffer & Tweets.BUFFER_DISASTER) != 0) {
			accentColor = context.getResources().getColor(
					R.color.accentLightDisaster);
			// set verified icon for disaster tweets
			holder.ivVerifiedIcon.setVisibility(ImageView.VISIBLE);
			if (cursor.getInt(cursor.getColumnIndex(Tweets.COL_ISVERIFIED)) > 0) {
				holder.ivVerifiedIcon
						.setImageResource(R.drawable.ic_small_verified);
			} else {
				holder.ivVerifiedIcon
						.setImageResource(R.drawable.ic_small_unverified);
			}
		} else {
			holder.ivVerifiedIcon.setVisibility(ImageView.GONE);
			accentColor = context.getResources().getColor(
					R.color.accentLightNormal);
		}

		// set side stripe color
		holder.rootView.setBackgroundColor(accentColor);

		// highlight own tweet
		boolean ownTweet = Long.toString(
				cursor.getLong(cursor.getColumnIndex(Tweets.COL_TWITTERUSER)))
				.equals(LoginActivity.getTwitterId(context));
		if (ownTweet) {
			holder.tvUsername.setTextColor(accentColor);
			// no verified icon for own tweets
			holder.ivVerifiedIcon.setVisibility(ImageView.GONE);
		} else {
			holder.tvUsername.setTextColor(context.getResources().getColor(
					R.color.darkText));
		}

		// higlight mentions
		String ownHandle = "@" + LoginActivity.getTwitterScreenname(context);
		String tweetText = (String) holder.tvTweetText.getText();
		Pattern pattern = Pattern.compile(ownHandle, Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(tweetText);
		Spannable tweetSpannable = new SpannableString(tweetText);
		while (matcher.find()) {
			tweetSpannable.setSpan(new ForegroundColorSpan(accentColor),
					matcher.start(), matcher.end(),
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		holder.tvTweetText.setText(tweetSpannable);
		// holder.rowLayout
		// .setBackgroundResource(R.drawable.mention_tweet_background);

	}

	public void loadBitmap(Uri uri, ImageView imageView, Context context) {
		if (cancelPotentialWork(uri, imageView)) {
			final BitmapWorkerTask task = new BitmapWorkerTask(imageView,
					context, uri);
			final AsyncDrawable asyncDrawable = new AsyncDrawable(
					context.getResources(), task);
			imageView.setImageDrawable(asyncDrawable);
			task.execute();
		}
	}

	class BitmapWorkerTask extends AsyncTask<AsyncDrawable, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		Context context;
		public Uri uri;
		AsyncDrawable asyncDrawable;

		public BitmapWorkerTask(ImageView imageView, Context context, Uri uri) {
			// Use a WeakReference to ensure the ImageView can be garbage
			// collected
			imageViewReference = new WeakReference<ImageView>(imageView);
			this.context = context;
			this.uri = uri;
		}

		// Decode image in background.
		@Override
		protected Bitmap doInBackground(AsyncDrawable... params) {
			Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
			// this.asyncDrawable = params[0];
			InputStream is;
			try {
				is = context.getContentResolver().openInputStream(uri);
				if (is != null)
					return BitmapFactory.decodeStream(is);
				else
					return null;
			} catch (Exception e) {
				return null;

			}
		}

		// Once complete, see if ImageView is still around and set bitmap.
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled()) {
				bitmap = null;
			}

			if (imageViewReference != null) {
				final ImageView imageView = imageViewReference.get();

				final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

				if (bitmap != null) {
					if (this == bitmapWorkerTask && imageView != null) {
						imageView.setBackground(null);
						imageView.setImageBitmap(bitmap);
					}
				}

			}
		}
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Resources res, BitmapWorkerTask bitmapWorkerTask) {
			super(res);
			bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(
					bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}

	public static boolean cancelPotentialWork(Uri uri, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final Uri bitmapUri = bitmapWorkerTask.uri;
			if (bitmapUri.equals(uri)) {
				// Cancel previous task
				bitmapWorkerTask.cancel(true);
			} else
				// The same work is already in progress
				return false;

		}
		// No task associated with the ImageView, or an existing task was
		// cancelled
		return true;
	}

}
