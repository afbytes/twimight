package ch.ethz.twimight.net.opportunistic;

import java.util.ArrayList;
import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.net.Html.HtmlPage;

public class DevicesReceiver extends BroadcastReceiver {
	
	public static interface ScanningFinished {
		
		public void onScanningFinished();
	}
	
	private static final String TAG = "DevicesReceiver";
	private ScanningFinished sf;
	MacsDBHelper dbHelper;
	BluetoothAdapter mBtAdapter;
	SharedPreferences sharedPref;
	
	private static final String DISCOVERY_FINISHED_TIMESTAMP = "discovery_finished_timestamp" ;
	private ArrayList<String> newDeviceList = null;
	
	public final String SCAN_PROBABILITY = "scan_probability";
	public final String DEVICE_LIST = "device_list";
	public final String SCAN_COUNT = "scan_count";
	
	private static final float INIT_PROB = (float) 0.5;
	private static final float MAX_PROB = (float) 1.0;
	private static final float MIN_PROB = (float) 0.1;
	private static final int MAX_SCAN_COUNT = 30;
	
	public DevicesReceiver(Context context){
		dbHelper = new MacsDBHelper(context);
		dbHelper.open();		
		sharedPref = PreferenceManager.getDefaultSharedPreferences(context);		
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
		
	}
	
	private void addPairedDevices() {
		// Get a set of currently paired devices
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();	      
        
        if (pairedDevices != null) {
        	// If there are paired devices, add each one to the ArrayAdapter
	        if (pairedDevices.size() > 0) {
	        	
	            for (BluetoothDevice device : pairedDevices) {	 
	            	    if (!dbHelper.updateMacActive(device.getAddress().toString(), 1)) {
	            	    	dbHelper.createMac(device.getAddress().toString(), 1); 
	            	    }
	            	    
	            }
	        } 
        }	
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();	
        // When discovery finds a device
		if (BluetoothDevice.ACTION_FOUND.equals(action)) {
			
			// Get the BluetoothDevice object from the Intent
			BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);    
			
			Log.i(TAG, "found a new device:" + device.getAddress().toString());
			newDeviceList.add(device.getAddress().toString());
			if (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_SMART) {
				
				if (!dbHelper.updateMacActive(device.getAddress().toString(), 1)) {
					dbHelper.createMac(device.getAddress().toString(), 1);					
				}
				
			} 

			// When discovery is finished...
		} else  if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {        
			if ( (System.currentTimeMillis() - sharedPref.getLong(DISCOVERY_FINISHED_TIMESTAMP, 0)) > 10000) {
			    	SharedPreferences.Editor edit = sharedPref.edit();
			    	edit.putLong(DISCOVERY_FINISHED_TIMESTAMP, System.currentTimeMillis());
			    	edit.commit();
			    	sf.onScanningFinished();
			    	compareDevice();
			    }
				
        } 

	}
	
	
	//determine the probability for next scan
	private void compareDevice(){
		Bundle scanInfo = getScanInfo();
		ArrayList<String> oldDeviceList = scanInfo.getStringArrayList(DEVICE_LIST);
		float oldProb = scanInfo.getFloat(SCAN_PROBABILITY);
		int scanCount = scanInfo.getInt(SCAN_COUNT);
		int newDevice = 0;
		int oldDevice = 0;
		
		if(newDeviceList.size() > 0){
			for( String device:newDeviceList ){
				if(oldDeviceList.indexOf(device)!= -1){
					oldDevice ++;
				}
				else{
					newDevice ++;
				}
			}
		}
		
		if(oldDevice == oldDeviceList.size() && newDevice == 0){
			if(scanCount != 0){
				scanCount --;
			}
		}
		else{
			scanCount = MAX_SCAN_COUNT;
		}
		
		float dp = 0;
		if(scanCount == 0){
			oldProb = Math.max((float)oldProb / (float)2, MIN_PROB);
		}
		else{
			if(oldDevice>0 || newDevice >0){

				dp = (float) (MAX_PROB - oldProb) * ((float)newDevice/(float)(newDeviceList.size()) + (1 - (float)oldDevice/(float)(oldDeviceList.size())));
				
				Log.i(TAG, "delta p is:" + String.valueOf(dp));
				oldProb = Math.min(oldProb + dp, MAX_PROB);
				
			}
			else{
				oldProb = Math.max((float)oldProb / (float)2, MIN_PROB);
			}
		}
		
		
		setScanInfo(oldProb, newDeviceList, scanCount);
	}
	
	public void setListener(ScanningFinished sf) {
		this.sf = sf;
	}
	
	//set scan info(device list and probability) to current scan
	public void setScanInfo(float probability, ArrayList<String> deviceList, int scanCount) {

		Log.i(TAG,"set scan probability to:" + String.valueOf(probability));
		
		SharedPreferences.Editor prefEditor = sharedPref.edit();
		
		Log.i(TAG, "set scan count to:" + String.valueOf(scanCount));
		prefEditor.putInt(SCAN_COUNT, scanCount);
		prefEditor.putFloat(SCAN_PROBABILITY, probability);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < deviceList.size(); i++) {
		    if(i==0){
		    	sb.append(deviceList.get(i));
		    }
		    else{
		    	sb.append(",");
		    	sb.append(deviceList.get(i));
		    }
		    
		}
		Log.i(TAG, "set device list to:" + sb.toString());
		prefEditor.putString(DEVICE_LIST, sb.toString());
		prefEditor.commit();
	}
	
	//get scan info(device list and probability) of last scan
	public Bundle getScanInfo() {

		float probability = sharedPref.getFloat(SCAN_PROBABILITY, INIT_PROB);
		String[] tmpList = sharedPref.getString(DEVICE_LIST, "").split(",");
		int scanCount = sharedPref.getInt(SCAN_COUNT, MAX_SCAN_COUNT);

		ArrayList<String> deviceList = new ArrayList<String>();
		for(String device:tmpList){
			deviceList.add(device);
		}
		Log.i(TAG, "current scan probability is:" + String.valueOf(probability));
		Log.i(TAG, "devices scanned during last time are:" + deviceList.toString());
		Log.i(TAG, "scan count now is:" + String.valueOf(scanCount));
		Bundle mBundle = new Bundle();
		mBundle.putFloat(SCAN_PROBABILITY, probability);
		mBundle.putInt(SCAN_COUNT, scanCount);
		mBundle.putStringArrayList(DEVICE_LIST, deviceList);
		return mBundle;
	}
	
	//initialize device list for a new scan
	public void initDeivceList() {

		newDeviceList = new ArrayList<String>();
	}


}
