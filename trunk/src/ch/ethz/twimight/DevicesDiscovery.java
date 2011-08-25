package ch.ethz.twimight;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;



public class DevicesDiscovery extends Service {
	 private BluetoothAdapter mBtAdapter;
	 Discovery discovery;
	 Thread discoveryThread;
	 Handler handler;
	 static final String TAG = "DeviceDiscovery";
     static long discoveryDelay = 100000L;
     WakeLock wakeLock;
	
	 @Override
	public void onCreate() {
		
		super.onCreate();		
		   // Get the local Bluetooth adapter
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler(); 
        if (!mBtAdapter.isEnabled())
	 		mBtAdapter.enable();
		discovery = new Discovery();
	    discoveryThread = new Thread(discovery);
	    discoveryThread.start();
	    PowerManager mgr = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
	    wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");
	    wakeLock.acquire();
	}

	@Override
	  public IBinder onBind(Intent intent) {
	    return null;
	  }
	
	
	 @Override
	    public void onDestroy() {	   	       
	        Log.i(TAG,"on destroy");
	        // Make sure we're not doing discovery anymore
	        if (mBtAdapter != null) {
	            mBtAdapter.cancelDiscovery();
	        }
	        handler.removeCallbacks(discovery);
	        wakeLock.release();
	        super.onDestroy();	       
	    } 
	 
	 
	 @Override
		public int onStartCommand(Intent intent, int flags, int startId) {
		    
		    // We want this service to continue running until it is explicitly
		    // stopped, so return sticky.
		    return START_STICKY;
		}

	class Discovery implements Runnable { 
		 long i = 0;
		 public void run() {			
			 // If we're already discovering, stop it
			 	
		        if (mBtAdapter.isDiscovering()) {
		            mBtAdapter.cancelDiscovery();
		        }
		        i++;
		      /*
		       *   if (mBtAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
		        	Log.i(TAG, "discoverability is not enabled anymore" );
		            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
		            discoverableIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		            Log.i(TAG, "enabling discoverability" );
		             startActivity(discoverableIntent);  		  	
		        }	*/	        
		        // Request discover from BluetoothAdapter
		        try {
		        	if (DisasterOperations.mBlueService.getState() != BluetoothComms.STATE_CONNECTED &&
		        		DisasterOperations.mBlueService.getState() != BluetoothComms.STATE_CONNECTING	) {		  
		        		mBtAdapter.startDiscovery(); 
		        		Log.i(TAG,"discovery run");		        
		        	}
		        	else 
		        		Log.i(TAG,"discovery not started since it's still connected");	
		        	long delay = Math.round(Math.random()*40000) + discoveryDelay;  		        	
		        	handler.postDelayed(this, delay);			        		
		        	
		        }
		        catch (Exception ex) {}
		       	        
		        
		 }
	 }
	
	
}
