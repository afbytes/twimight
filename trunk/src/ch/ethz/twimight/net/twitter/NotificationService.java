package ch.ethz.twimight.net.twitter;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.DmConversationListActivity;
import ch.ethz.twimight.activities.HomeScreenActivity;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.util.Preferences;

import com.nostra13.universalimageloader.core.ImageLoader;

public class NotificationService extends IntentService {

	private static final String TAG = NotificationService.class.getSimpleName();

	public static final String EXTRA_KEY_ACTION = "EXTRA_KEY_ACTION";
	public static final String ACTION_NOTIFY_PENDING = "ACTION_NOTIFY_PENDING";
	public static final String ACTION_MARK_TIMELINE_SEEN = "ACTION_MARK_TIMELINE_SEEN";
	public static final String ACTION_MARK_MENTIONS_SEEN = "ACTION_MARK_MENTIONS_SEEN";
	public static final String ACTION_MARK_DIRECT_MESSAGES_SEEN = "ACTION_MARK_DIRECT_MESSAGES_SEEN";

	private static final int ID_TIMELINE = 1;
	private static final int ID_MENTIONS = 2;
	private static final int ID_DIRECT_MESSAGES = 3;

	private static final long[] VIBRATION_PATTERN = new long[] { 0, 200, 100, 200 };

	public NotificationService() {
		super(NotificationService.class.getName());
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String action = intent.getStringExtra(EXTRA_KEY_ACTION);
		Log.d(TAG, "action: " + action);
		if (ACTION_NOTIFY_PENDING.equals(action)) {
			notifyTimeline();
			notifyMentions();
			notifyDirectMessages();
		} else if (ACTION_MARK_TIMELINE_SEEN.equals(action)) {
			markTimelineSeen();
		} else if (ACTION_MARK_MENTIONS_SEEN.equals(action)) {
			markMentionsSeen();
		} else if (ACTION_MARK_DIRECT_MESSAGES_SEEN.equals(action)) {
			markDirectMessagesSeen();
		}
	}

	private void notifyTimeline() {
		NotificationMaker notificationMaker = new TimelineNotificationMaker(this);
		notificationMaker.makeNotification();
	}

	private void notifyMentions() {
		NotificationMaker notificationMaker = new MentionsNotificationMaker(this);
		notificationMaker.makeNotification();
	}

	private void notifyDirectMessages() {
		NotificationMaker notificationMaker = new DirectMessagesNotificationMaker(this);
		notificationMaker.makeNotification();
	}

	private void markTimelineSeen() {
		long timestamp = System.currentTimeMillis();
		Preferences.update(this, R.string.pref_key_timeline_notified_until, timestamp);
		Preferences.update(this, R.string.pref_key_timeline_seen_until, timestamp);
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(ID_TIMELINE);
	}

	private void markMentionsSeen() {
		long timestamp = System.currentTimeMillis();
		Preferences.update(this, R.string.pref_key_mentions_notified_until, timestamp);
		Preferences.update(this, R.string.pref_key_mentions_seen_until, timestamp);
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(ID_MENTIONS);
	}

	private void markDirectMessagesSeen() {
		long timestamp = System.currentTimeMillis();
		Preferences.update(this, R.string.pref_key_direct_messages_notified_until, timestamp);
		Preferences.update(this, R.string.pref_key_direct_messages_seen_until, timestamp);
		NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		notificationManager.cancel(ID_DIRECT_MESSAGES);
	}

	private abstract class NotificationMaker {

		Context mContext;

		public NotificationMaker(Context context) {
			mContext = context;
		}

		public void makeNotification() {
			if (notificationsAllowed()) {
				Cursor unnotified = getUnnotified();
				if (unnotified != null && unnotified.getCount() > 0) {
					Cursor unseen = getUnseen();
					if (unseen != null && unseen.getCount() > 0) {
						// setup notification builder
						NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
						builder.setSmallIcon(R.drawable.ic_notification);
						builder.setNumber(unseen.getCount());
						// vibration
						boolean vibrate = Preferences.getBoolean(mContext, R.string.pref_key_notification_vibrate,
								false);
						if (vibrate) {
							builder.setVibrate(VIBRATION_PATTERN);
						}
						// sound
						String soundUriString = Preferences.getString(mContext,
								R.string.pref_key_notification_ringtone, null);
						Uri soundUri = Uri.parse(soundUriString);
						builder.setSound(soundUri, AudioManager.STREAM_NOTIFICATION);
						// content intent
						Intent contentIntent = getContentIntent();
						PendingIntent pendingContentIntent = PendingIntent.getActivity(mContext, 0, contentIntent, 0);
						builder.setContentIntent(pendingContentIntent);
						// delete intent
						Intent deleteIntent = getDeleteIntent();
						PendingIntent pendingDeleteIntent = PendingIntent.getService(mContext, 0, deleteIntent, 0);
						builder.setDeleteIntent(pendingDeleteIntent);
						// content specific setup
						if (unseen.getCount() == 1) {
							// only 1 item
							unseen.moveToFirst();
							builder.setContentTitle(getSingleItemTitle(unseen));
							builder.setContentText(getSingleItemText(unseen));
							builder.setLargeIcon(getSingleItemLargeIcon(unseen));
						} else {
							// multiple items:
							Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_notification);
							builder.setLargeIcon(largeIcon);
							builder.setContentTitle(getMultiItemTitle(unseen));
							builder.setContentText("@" + LoginActivity.getTwitterScreenname(mContext));
							NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
							while (unseen.moveToNext()) {
								inboxStyle.addLine(getMultiItemLine(unseen));
							}
							inboxStyle.setSummaryText("@" + LoginActivity.getTwitterScreenname(mContext));
							builder.setStyle(inboxStyle);
						}
						// notify
						NotificationManager notificationmanager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
						notificationmanager.notify(getNotificationId(), builder.build());
						updateNotifiedTimestamp();
						unseen.close();
					}
					unnotified.close();
				}
			}
		}

		abstract boolean notificationsAllowed();

		abstract Cursor getUnseen();

		abstract Cursor getUnnotified();

		abstract Intent getContentIntent();

		abstract Intent getDeleteIntent();

		abstract CharSequence getSingleItemTitle(Cursor c);

		abstract CharSequence getMultiItemTitle(Cursor c);

		abstract CharSequence getSingleItemText(Cursor c);

		private Bitmap getSingleItemLargeIcon(Cursor c) {
			String imageUri = c.getString(c.getColumnIndex(TwitterUsers.COL_PROFILE_IMAGE_URI));
			Bitmap bitmap = ImageLoader.getInstance().loadImageSync(imageUri);
			return bitmap;
		}

		abstract CharSequence getMultiItemLine(Cursor c);

		abstract void updateNotifiedTimestamp();

		abstract int getNotificationId();
	}

	private abstract class TweetNotificationMaker extends NotificationMaker {

		public TweetNotificationMaker(Context context) {
			super(context);
		}

		@Override
		CharSequence getSingleItemTitle(Cursor c) {
			String title = c.getString(c.getColumnIndex(TwitterUsers.COL_NAME));
			return title;
		}

		@Override
		CharSequence getSingleItemText(Cursor c) {
			String text = c.getString(c.getColumnIndex(Tweets.COL_TEXT));
			return text;
		}


		@Override
		CharSequence getMultiItemLine(Cursor c) {
			String userName = c.getString(c.getColumnIndex(TwitterUsers.COL_NAME));
			String tweetText = c.getString(c.getColumnIndex(Tweets.COL_TEXT));
			CharSequence line = Html.fromHtml("<b>" + userName + "</b> " + tweetText);
			return line;
		}
	}

	private class TimelineNotificationMaker extends TweetNotificationMaker {

		public TimelineNotificationMaker(Context context) {
			super(context);
		}

		@Override
		boolean notificationsAllowed() {
			return HomeScreenActivity.running == false
					&& Preferences.getBoolean(mContext, R.string.pref_key_notify_tweets, false);
		}

		private Cursor getTimelineSince(long timestamp) {
			Uri uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_TIMELINE + "/" + Tweets.FILTER_RECEIVED_AFTER + "/" + timestamp);
			Cursor cursor = getContentResolver().query(uri, null, null, null, null);
			return cursor;
		}

		@Override
		Cursor getUnseen() {
			long seenUntilTimestamp = Preferences.getLong(mContext, R.string.pref_key_timeline_seen_until,
					System.currentTimeMillis());
			return getTimelineSince(seenUntilTimestamp);
		}

		@Override
		Cursor getUnnotified() {
			long notifiedUntilTimestamp = Preferences.getLong(mContext, R.string.pref_key_timeline_notified_until,
					System.currentTimeMillis());
			return getTimelineSince(notifiedUntilTimestamp);
		}

		@Override
		Intent getContentIntent() {
			Intent intent = new Intent(mContext, HomeScreenActivity.class);
			intent.putExtra(HomeScreenActivity.EXTRA_KEY_INITIAL_TAB, HomeScreenActivity.EXTRA_INITIAL_TAB_TIMELINE);
			return intent;
		}

		@Override
		Intent getDeleteIntent() {
			Intent intent = new Intent(mContext, NotificationService.class);
			intent.putExtra(EXTRA_KEY_ACTION, ACTION_MARK_TIMELINE_SEEN);
			return intent;
		}

		@Override
		CharSequence getMultiItemTitle(Cursor c) {
			String title = String.format(getString(R.string.notification_title_new_tweets), c.getCount());
			return title;
		}

		@Override
		void updateNotifiedTimestamp() {
			Preferences.update(mContext, R.string.pref_key_timeline_notified_until, System.currentTimeMillis());
		}

		@Override
		int getNotificationId() {
			return ID_TIMELINE;
		}
	}

	private class MentionsNotificationMaker extends TweetNotificationMaker {

		public MentionsNotificationMaker(Context context) {
			super(context);
		}

		@Override
		boolean notificationsAllowed() {
			return HomeScreenActivity.running == false
					&& Preferences.getBoolean(mContext, R.string.pref_key_notify_tweets, false)
					&& TwitterSyncService.firstMentionsSyncCompleted(mContext);
		}

		private Cursor getMentionsSince(long timestamp) {
			Uri uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/"
					+ Tweets.TWEETS_TABLE_MENTIONS + "/" + Tweets.FILTER_RECEIVED_AFTER + "/" + timestamp);
			Cursor cursor = getContentResolver().query(uri, null, null, null, null);
			return cursor;
		}

		@Override
		Cursor getUnseen() {
			long seenUntilTimestamp = Preferences.getLong(mContext, R.string.pref_key_mentions_seen_until,
					System.currentTimeMillis());
			return getMentionsSince(seenUntilTimestamp);
		}

		@Override
		Cursor getUnnotified() {
			long notifiedUntilTimestamp = Preferences.getLong(mContext, R.string.pref_key_mentions_notified_until,
					System.currentTimeMillis());
			return getMentionsSince(notifiedUntilTimestamp);
		}

		@Override
		Intent getContentIntent() {
			Intent intent = new Intent(mContext, HomeScreenActivity.class);
			intent.putExtra(HomeScreenActivity.EXTRA_KEY_INITIAL_TAB, HomeScreenActivity.EXTRA_INITIAL_TAB_MENTIONS);
			return intent;
		}

		@Override
		Intent getDeleteIntent() {
			Intent intent = new Intent(mContext, NotificationService.class);
			intent.putExtra(EXTRA_KEY_ACTION, ACTION_MARK_MENTIONS_SEEN);
			return intent;
		}

		@Override
		CharSequence getMultiItemTitle(Cursor c) {
			String title = String.format(getString(R.string.notification_title_new_mentions), c.getCount());
			return title;
		}

		@Override
		void updateNotifiedTimestamp() {
			Preferences.update(mContext, R.string.pref_key_mentions_notified_until, System.currentTimeMillis());
		}

		@Override
		int getNotificationId() {
			return ID_MENTIONS;
		}
	}

	private class DirectMessagesNotificationMaker extends NotificationMaker {

		public DirectMessagesNotificationMaker(Context context) {
			super(context);
		}

		@Override
		boolean notificationsAllowed() {
			return DmConversationListActivity.running == false
					&& Preferences.getBoolean(mContext, R.string.pref_key_notify_direct_messages, false)
					&& TwitterSyncService.firstIncomingDmSyncCompleted(mContext);
		}

		private Cursor getDirectMessagesSince(long timestamp) {
			Uri uri = Uri.parse("content://" + DirectMessages.DM_AUTHORITY + "/" + DirectMessages.DMS + "/"
					+ DirectMessages.FILTER_INCOMING_RECEIVED_AFTER + "/" + timestamp);
			Cursor cursor = getContentResolver().query(uri, null, null, null, null);
			return cursor;
		}

		@Override
		Cursor getUnseen() {
			long seenUntilTimestamp = Preferences.getLong(mContext, R.string.pref_key_direct_messages_seen_until,
					System.currentTimeMillis());
			return getDirectMessagesSince(seenUntilTimestamp);
		}

		@Override
		Cursor getUnnotified() {
			long notifiedUntilTimestamp = Preferences.getLong(mContext,
					R.string.pref_key_direct_messages_notified_until, System.currentTimeMillis());
			return getDirectMessagesSince(notifiedUntilTimestamp);
		}

		@Override
		Intent getContentIntent() {
			Intent intent = new Intent(mContext, DmConversationListActivity.class);
			return intent;
		}

		@Override
		Intent getDeleteIntent() {
			Intent intent = new Intent(mContext, NotificationService.class);
			intent.putExtra(EXTRA_KEY_ACTION, ACTION_MARK_DIRECT_MESSAGES_SEEN);
			return intent;
		}

		@Override
		CharSequence getSingleItemTitle(Cursor c) {
			String title = c.getString(c.getColumnIndex(TwitterUsers.COL_NAME));
			return title;
		}

		@Override
		CharSequence getMultiItemTitle(Cursor c) {
			String title = String.format(getString(R.string.notification_title_new_direct_messages), c.getCount());
			return title;
		}

		@Override
		CharSequence getSingleItemText(Cursor c) {
			String text = c.getString(c.getColumnIndex(DirectMessages.COL_TEXT));
			return text;
		}

		@Override
		CharSequence getMultiItemLine(Cursor c) {
			String userName = c.getString(c.getColumnIndex(TwitterUsers.COL_NAME));
			String directMessageText = c.getString(c.getColumnIndex(DirectMessages.COL_TEXT));
			CharSequence line = Html.fromHtml("<b>" + userName + "</b> " + directMessageText);
			return line;
		}

		@Override
		void updateNotifiedTimestamp() {
			Preferences.update(mContext, R.string.pref_key_direct_messages_notified_until, System.currentTimeMillis());
		}

		@Override
		int getNotificationId() {
			return ID_DIRECT_MESSAGES;
		}

	}
}
