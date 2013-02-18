package ch.ethz.twimight.net.opportunistic;

import java.util.Set;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import ch.ethz.twimight.data.MacsDBHelper;

public class DevicesReceiver extends BroadcastReceiver {
	
	public static interface ScanningFinished {
		
		public void onScanningFinished();
	}
	
	private static final String TAG = "DevicesReceiver";
	private ScanningFinished sf;
	MacsDBHelper dbHelper;
	BluetoothAdapter mBtAdapter ;
	SharedPreferences sharedPref;
	
	private static final String DISCOVERY_FINISHED_TIMESTAMP = "discovery_finished_timestamp" ;
	
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

			if (device.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.PHONE_SMART &&
					device.getBondState() != BluetoothDevice.BOND_BONDED) {
				
				if (!dbHelper.updateMacActive(device.getAddress().toString(), 1)) {
					dbHelper.createMac(device.getAddress().toString(), 1);					
				} 
				

			} 


			// When discovery is finished...
		} else  if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {        
			if ( (System.currentTimeMillis() - sharedPref.getLong(DISCOVERY_FINISHED_TIMESTAMP, 0)) > 10000
					) {
			    	SharedPreferences.Editor edit = sharedPref.edit();
			    	edit.putLong(DISCOVERY_FINISHED_TIMESTAMP, System.currentTimeMillis());
			    	edit.commit();
			    	addPairedDevices();
			    	sf.onScanningFinished();
			    }
				
        } 

	}
	
	public void setListener(ScanningFinished sf) {
		this.sf = sf;
	}

}
