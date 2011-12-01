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

import java.util.Date;

import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.util.Constants;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * This is the thread for scanning for Bluetooth peers.
 * @author theus
 *
 */
public class ScanningThread implements Runnable{

	
	private static final String TAG = "ScanningThread"; /** For Debugging */
	
	
	public Handler handler; /** Handler for delayed execution of the thread */
	
	// manage bluetooth communication
	static BluetoothComms bluetoothHelper = null;

	//private Date lastScan;
			
	private MacsDBHelper dbHelper;
	
	private Context context;
	
	private Cursor cursor;
	
	private Date scanStartTime;
	
	private static ScanningThread instance;
	
	/**
	 * Constructor
	 */
	public ScanningThread(Context context){
		this.context = context;
		handler = new Handler();
		
        // set up Bluetooth
        bluetoothHelper = new BluetoothComms(this.context, mHandler);
		
		dbHelper = new MacsDBHelper(this.context);
		dbHelper.open();
		
	}
	
	public static ScanningThread getInstance(Context context){

		if(instance == null){
			instance = new ScanningThread(context);
		}
		return instance;
	}
	
	/**
	 * The run function of the thread. Triggers and reschedules Bluetooth connection attempts.
	 */
	@Override
	public void run() {

		ScanningService.stopScanning();
		
		startScanning();
		
	}
	
	/**
	 * Start the scanning.
	 * @return true if the connection with the TDS was successful, false otherwise.
	 */
	private boolean startScanning(){
		
		// Get a cursor over all "active" MACs in the DB
		cursor = dbHelper.fetchActiveMacs();
		
		
		// Stop listening mode
		bluetoothHelper.stop();
		
		// Log the date for later rescheduling of the next scanning
		scanStartTime = new Date();
		
		if (cursor.moveToFirst()) {
            // Get the field values
            long mac = cursor.getLong(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
            displayState("Scanning for: " + dbHelper.long2mac(mac) + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
            bluetoothHelper.connect(dbHelper.long2mac(mac));
            
        } else {
        	stopScanning();
        }
		
		return false;
	}
	
	/**
	 * Proceed to the next MAC address
	 */
	private void nextScanning() {
		if(cursor == null || bluetoothHelper.getState()==BluetoothComms.STATE_CONNECTED){
			stopScanning();
		} else {
			// do we have another MAC in the cursor?
			if(cursor.moveToNext()){
	            long mac = cursor.getLong(cursor.getColumnIndex(MacsDBHelper.KEY_MAC));
	            displayState("Scanning for: " + dbHelper.long2mac(mac) + " (" + dbHelper.fetchMacSuccessful(mac) + "/" + dbHelper.fetchMacAttempts(mac) + ")");
	            bluetoothHelper.connect(dbHelper.long2mac(mac));
			} else {
				stopScanning();
			}
		}
		
	}
	
	/**
	 * Terminates one round of scanning: cleans up and reschedules next scan
	 */
	private void stopScanning() {
		cursor = null;
		
		if(isDisasterMode()){
			bluetoothHelper.stop();
			
			// reschedule next scan (randomized)
			if(scanStartTime == null || scanStartTime.getTime() + Constants.SCANNING_INTERVAL - System.currentTimeMillis() < Constants.MIN_LISTEN_TIME){
				ScanningService.scheduleScanning(Constants.MIN_LISTEN_TIME);
			} else {
				long delay = Math.round(Math.random()*Constants.RANDOMIZATION_INTERVAL) - Math.round(0.5*Constants.RANDOMIZATION_INTERVAL);
				ScanningService.scheduleScanning(scanStartTime.getTime() + Constants.SCANNING_INTERVAL - System.currentTimeMillis() + delay);
			}
			
			// start listening mode
			bluetoothHelper.start();
			displayState("Listening...");
		}
	}
	
	/**
	 * Cancel all Bluetooth actions
	 */
	public void stopOperation(){
		bluetoothHelper.stop();
	}

	/**
	 *  The Handler that gets information back from the BluetoothService
	 */
	private final Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {          

			case Constants.MESSAGE_READ:         	 
				displayState(msg.obj.toString());        	              		               
				break;             
				
			case Constants.MESSAGE_CONNECTION_SUCCEEDED:
				displayState("connection succeeded");   
				
				// Cancel future scans
				ScanningService.stopScanning();
				
				// Insert successful connection into DB
				dbHelper.updateMacSuccessful(dbHelper.mac2long(msg.obj.toString()), 1);
				
				// TODO: Here starts the protocol for Tweet exchange.
				// For testing resasons we just restart the scanning after a random time
				bluetoothHelper.write("Hello, I'm " + bluetoothHelper.getMac());
				if(isDisasterMode()){
					ScanningService.scheduleScanning(Math.round(Math.random()*2*Constants.SCANNING_INTERVAL));
				}
				break;   
			case Constants.MESSAGE_CONNECTION_FAILED:             
				displayState("connection failed");
				
				// Insert failed connection into DB
				dbHelper.updateMacAttempts(dbHelper.mac2long(msg.obj.toString()), 1);
				
				// Next scan
				nextScanning();
				break;
				
			case Constants.MESSAGE_CONNECTION_LOST:         	 
				displayState("connection lost");   
				if(isDisasterMode()){
					ScanningService.scheduleScanning(Math.round(Math.random()*2*Constants.SCANNING_INTERVAL));
					bluetoothHelper.start();
				}
				
				break;
			}
			
		}

	};
	
	/**
	 * True if the disaster mode is on
	 */
	private boolean isDisasterMode(){
		return (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON) == true);
	}
	
	/**
	 * Display a message to the debugger.
	 * @param s
	 */
	private void displayState(String s){
				
		// Log entry
		Log.i(TAG, s);
		
	}

};
