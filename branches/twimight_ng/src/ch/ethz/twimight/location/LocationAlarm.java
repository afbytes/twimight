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
package ch.ethz.twimight.location;

import java.util.Date;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.util.Constants;

/**
 * Periodically launches the Location Thread if the settings are accordingly.
 * Inspired by the LocationPollerService https://github.com/commonsguy/cwac-locpoll/blob/master/src/com/commonsware/cwac/locpoll/LocationPollerService.java
 * @author thossmann
 *
 */
public class LocationAlarm extends BroadcastReceiver {

	private static final String WAKE_LOCK = "LocationWakeLock";
	private static WakeLock wakeLock;
	private static final String TAG = "LocationAlarm";
	private static PendingIntent pendingIntent;

	/**
	 * This constructor is called the alarm manager.
	 */
	public LocationAlarm(){}

	/**
	 * Call this constructor to trigger set everything up and immediately trigger the first alarm.
	 * @param context the application context
	 * @param timeOut calling interval in milliseconds
	 */
	public LocationAlarm(Context context, long timeOut){

		AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);

		Intent intent = (new Intent(context, LocationAlarm.class));
		pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		
		alarmMgr.cancel(pendingIntent);

		// schedule one immediately
		scheduleLocationUpdate(context, 0);
		
		Log.i(TAG, "alarm set");

	}
	
	/**
	 * Schedules the next location update
	 * @param context
	 */
	public static void scheduleLocationUpdate(Context context, long delay) {
		stopLocationUpdate(context);
		
		Intent intent = (new Intent(context, LocationAlarm.class));
		pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		alarmMgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis()+delay+ 30000L, pendingIntent);
		
	}
	
	/**
	 * Stop the scheduled alarm
	 * @param context
	 */
	public static void stopLocationUpdate(Context context) {
		
		Intent intent = (new Intent(context, LocationAlarm.class));
		pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

		AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
		alarmMgr.cancel(pendingIntent);
		
	}
	
	/**
	 * This is executed when the alarm goes off.
	 */
	@Override
	public void onReceive(Context context, Intent intent) {

		Log.i(TAG, "woken up!"+ (new Date()).toString());
		
		// Acquire wake lock so we don't fall asleep after onReceive returns
		getWakeLock(context);
		
		if(isLocationEnabled(context)){
			// Launch a new thread to query the location from location manager
			new LocationThread(context).start();
			
		}
	}


	/**
	 * @return the wakeLock
	 */
	public static WakeLock getWakeLock(Context context) {
		
		releaseWakeLock();
		
		PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
		wakeLock.acquire();

		return wakeLock;
	}
	
	/**
	 * We have to make sure to release the wake lock after the LocationThread is done!
	 */
	public static void releaseWakeLock(){
		if(wakeLock != null)
			if(wakeLock.isHeld())
				wakeLock.release();
	}
	
	/**
	 * Check the settings
	 * @return true if enabled, false otherwise
	 */
	private boolean isLocationEnabled(Context context) {
		return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefLocationUpdates", Constants.LOCATION_DEFAULT_ON);
	}
	
}
