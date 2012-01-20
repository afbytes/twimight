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
package ch.ethz.twimight.net.opportunistic;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.util.Constants;

/**
 * Service to scan for Bluetooth peers
 * @author thossmann
 */
public class ScanningAlarm extends BroadcastReceiver {
	// Class constants
	static final String TAG = "ScanningAlarm"; /** for logging */	
	
	private static WakeLock wakeLock; 
	private static final String WAKE_LOCK = "ScanningWakeLock";

	public static final String FORCE_SCAN = "force_scan"; /** To force a scan, put this extra in the starting intent */
	public static final String FORCE_SCAN_DELAY = "force_scan_delay"; /** To force a scan after a given delay, put this extra in the starting intent */
	
	private static boolean isRunning = false;

	
	

	/**
	 * This constructor is called the alarm manager.
	 */
	public ScanningAlarm(){}
	
	/**
	 * Starts the alarm.
	 */

	public ScanningAlarm(Context context, long delay, boolean forceScan) {	
		
		if(!isRunning){				
			
			// if Bluetooth is already enabled, start scanning. otherwise enable it now.
			if(BluetoothAdapter.getDefaultAdapter().isEnabled()){
				setBluetoothInitialState(context, true);
				scheduleScanning(context,0);
			} else {
				enableBluetooth(context);
			}	
				
			isRunning = true;
			
			Log.d(TAG, "instantiated"); 
		} else {
			
			if(forceScan){
				scheduleScanning(context,System.currentTimeMillis() + delay);
			}
		}
	}	
	
		
		private void setBluetoothInitialState(Context context, boolean b) {
			
			SharedPreferences.Editor prefEditor = PreferenceManager.getDefaultSharedPreferences(context).edit();
			prefEditor.putBoolean("wasBlueEnabled", b);
			prefEditor.commit();		 
		
	}
		
		

		/**
		 * Acquire the Wake Lock
		 * @param context
		 */
		public static void getWakeLock(Context context){
			
			releaseWakeLock();
			
			PowerManager mgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			wakeLock = mgr.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK , WAKE_LOCK); 
			wakeLock.acquire();
		}
		
		/**
		 * We have to make sure to release the wake lock after the TDSThread is done!
		 * @param context
		 */
		public static void releaseWakeLock(){
			if(wakeLock != null)
				if(wakeLock.isHeld())
					wakeLock.release();
		}

	

		/**
		 * Stop the scheduled alarm
		 * @param context
		 */
		public static void stopScanning(Context context) {
			AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
			
			Intent intent = new Intent(context, ScanningAlarm.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

			alarmMgr.cancel(pendingIntent);
			BluetoothAdapter.getDefaultAdapter().disable();
			releaseWakeLock();
			isRunning = false;
			
			if (getBluetoothInitialState(context) == false) {
				BluetoothAdapter.getDefaultAdapter().disable();
			}
		}

	private static boolean getBluetoothInitialState(Context context) {
		
		SharedPreferences prefEditor = PreferenceManager.getDefaultSharedPreferences(context);		
		return prefEditor.getBoolean("wasBlueEnabled", true);
		
		}

	/**
	 * Schedules a Scanning communication
	 * @param delay after how many milliseconds (0 for immediately)?
	 */
	public static void scheduleScanning(Context context, long time) {
		
		// cancel previously scheduled scans
		stopScanning(context);
				
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true){
			
			Log.d(TAG, "scheduling a new Bluetooth scanning in " + Long.toString( time ));
			AlarmManager alarmMgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
			
			Intent intent = new Intent(context, ScanningAlarm.class);
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			//alarmMgr.cancel(pendingIntent);
			
			alarmMgr.set(AlarmManager.RTC_WAKEUP, time, pendingIntent);
			Log.i(TAG, "alarm set");
		} 
	}
	

	

	
	/**
	 * Enable Bluetooth for scanning
	 */
	protected void enableBluetooth(Context context) {
		
		// as long as we are in disaster mode we turn bluetooth on if we find it off (is this evil?)
		BluetoothAdapter.getDefaultAdapter().enable();
		
		// schedule a new scan
		stopScanning(context);
		scheduleScanning(context,0);
		
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG, "requesting synchronization with twitter");		
		getWakeLock(context);
		
		if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true) {
			Intent i = new Intent(context,ScanningService.class);		
			context.startService(i);
		}
		
	}
	
	
		
}
