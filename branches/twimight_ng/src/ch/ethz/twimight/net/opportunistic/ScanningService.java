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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import ch.ethz.twimight.util.Constants;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Service to scan for Bluetooth peers
 * @author thossmann
 */
public class ScanningService extends Service {
	// Class constants
	static final String TAG = "ScanningService"; /** for logging */
	
	private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	private static ScheduledFuture scanningThreadHandle; /** we need this to cancel the periodic scanning */

	protected static Context context;
	
	private static PowerManager.WakeLock wakeLock; 
	private static final String WAKE_LOCK = "ScanningWakeLock";
	
	private boolean isRunning = false;
		
	/**
	 * Create function, sets the service up
	 */
	@Override
	public void onCreate() {
		
		ScanningService.context = getApplicationContext();
		Log.d(TAG, "onCreate");	
		

	}
	
	/**
	 * Starts the updating threads.
	 */
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		if(!isRunning){
		
			// DisasterService returns false if the phone does not support the disaster mode
			if(!isDisasterModeSupported()){
				Log.d(TAG, "Disaster mode not supported! Not starting Scanning Service.");
				return START_NOT_STICKY;
			}
	
	
			// if Bluetooth is already enabled, start scanning. otherwise enable it now.
			if(BluetoothAdapter.getDefaultAdapter().isEnabled()){
				scheduleScanning(0);
			} else {
				enableBluetooth();
			}
			
			// We don't want to fall asleep and forget abuot scanning!
			PowerManager mgr = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
			wakeLock.acquire();
				
			isRunning = true;
			
			Log.d(TAG, "onStart'ed"); 
		} 

		
		return START_STICKY;
	}
	
	/**
	 * Stop scheduled ScanningThread
	 */
	public static void stopScanning() {
		if(scanningThreadHandle != null){
			scanningThreadHandle.cancel(true);
		}
		
		
	}

	/**
	 * Schedules a Scanning communication
	 */
	public static void scheduleScanning(long delay) {
		
		// cancel previously scheduled scans
		ScanningService.stopScanning();
				
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true){
			
			// is bluetooth on??
			if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
				enableBluetooth();
				return;
			}

			
			Log.d(TAG, "scheduling a new Bluetooth scanning in " + Long.toString(delay));
			scanningThreadHandle = scheduler.schedule(ScanningThread.getInstance(context), delay, TimeUnit.MILLISECONDS);
		} 
	}
	

	/**
	 * onDestroy
	 */
	@Override
	public void onDestroy() {

		super.onDestroy();

		ScanningThread.getInstance(context).stopOperation();
		stopScanning();
		
		// TODO: We should ask the user to switch off Bluetooth
		//BluetoothAdapter.getDefaultAdapter().disable();

		
		if(wakeLock != null) 
			if(wakeLock.isHeld())
				wakeLock.release();
		
		isRunning = false;
		
		Log.d(TAG, "onDestroy'd");
		
	}

	/**
	 * onBind ..
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	
	/**
	 * Enable Bluetooth for scanning
	 */
	protected static void enableBluetooth() {
		
		// as long as we are in disaster mode we turn bluetooth on if we find it off (is this evil?)
		BluetoothAdapter.getDefaultAdapter().enable();
		
		// schedule a new scan
		stopScanning();
		scanningThreadHandle = scheduler.schedule(ScanningThread.getInstance(context), Constants.WAIT_FOR_BLUETOOTH, TimeUnit.MILLISECONDS);
		
	}
	
	/**
	 * Checks if the phone fulfills all requirements for the disaster mode.
	 * @return
	 */
	public static boolean isDisasterModeSupported(){
		if(BluetoothAdapter.getDefaultAdapter() == null)
			return false;
		if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.GINGERBREAD)
			return false;
		
		return true;
	}
		
}
