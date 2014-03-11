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
/**
 * 
 */
package ch.ethz.twimight.net.twitter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.util.Preferences;

/**
 * Regularly schedules and handles alarms to fetch updates from twitter
 * 
 * @author pcarta
 * 
 */
public class TwitterAlarm extends BroadcastReceiver {

	private static final String WAKE_LOCK = "TwitterLock";
	private static final String TAG = "TwitterAlarm";
	private static WakeLock wakeLock;


	public static void initialize(Context context) {
		stopTwitterAlarm(context);
		long intervalMinutes = Long.valueOf(Preferences.getString(context, R.string.pref_key_update_interval, "5"));
		if (intervalMinutes > 0) {
			long intervalMillis = intervalMinutes * 60 * 1000;
			AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
			Intent intent = new Intent(context, TwitterAlarm.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
					PendingIntent.FLAG_UPDATE_CURRENT);
			alarmMgr.setInexactRepeating(AlarmManager.RTC_WAKEUP, intervalMillis,
					intervalMillis, pendingIntent);
		}
	}


	/**
	 * This is executed when the alarm goes off.
	 * 
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		// getWakeLock(context);
		Log.d(TAG, "TwitterAlarm onReceive()");
		// sync timeline
		Intent i = new Intent(context, TwitterSyncService.class);
		i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_TIMELINE);
		context.startService(i);
		// sync mentions
		i = new Intent(context, TwitterSyncService.class);
		i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_MENTIONS);
		context.startService(i);
		// sync messages
		i = new Intent(context, TwitterSyncService.class);
		i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_MESSAGES);
		context.startService(i);
		// tentatively removing transactional sync (should be done in every
		// step)
		// // sync transactional
		// i = new Intent(context, TransactionalSyncService.class);
		// context.startService(i);
		// sync friends
		i = new Intent(context, TwitterSyncService.class);
		i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_FRIENDS);
		context.startService(i);
		// sync followers
		i = new Intent(context, TwitterSyncService.class);
		i.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_FOLLOWERS);
		context.startService(i);
	}

	/**
	 * Stop the scheduled alarm
	 * 
	 * @param context
	 */
	public static void stopTwitterAlarm(Context context) {
		AlarmManager alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

		Intent intent = new Intent(context, TwitterAlarm.class);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		alarmMgr.cancel(pendingIntent);
	}

	/**
	 * Acquire the Wake Lock
	 * 
	 * @param context
	 */
	public static void getWakeLock(Context context) {

		// releaseWakeLock();

		PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
		wakeLock.acquire();
	}

	/**
	 * release the wake lock after onReceive is done!
	 * 
	 * @param context
	 */
	public static void releaseWakeLock() {
		if (wakeLock != null)
			if (wakeLock.isHeld())
				wakeLock.release();
	}

}
