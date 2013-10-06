package ch.ethz.twimight.net.OMF;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.twimight.activities.PrefsActivity;
import ch.ethz.twimight.activities.ShowTweetListActivity;
import ch.ethz.twimight.net.opportunistic.ScanningAlarm;
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.net.twitter.TwitterService.LocalBinder;

public class OmfService extends Service {
	
	public static final String TAG = "OMFService";


	static final int MSG_START_APP = 1;	
	static final int MSG_START_DIS_MODE = 2;	
	static final int MSG_STOP_DIS_MODE = 3;	
	static final int MSG_REGISTER_CLIENT = 4;   
	static final int MSG_UNREGISTER_CLIENT = 5;
	
	public static final int MESSAGE_COUNTER_UPDATE = 21;
	
	private Messenger clientMessenger;
	TwitterService service;
	/**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger localMessenger = new Messenger(new IncomingHandler());
    
	/** Flag indicating whether we have called bind on the service. */
	boolean mBound; 
	
	private int tweetCounter = 0;

	/**
	 * Handler of incoming messages from external clients.
	 */
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			
			case MSG_START_APP:
				createActivity(getApplicationContext());
				bindToTwitterService();
				break;
			case MSG_START_DIS_MODE:
				enableDisMode();				
				break;
			case MSG_STOP_DIS_MODE:
				disableDisMode();
				break;
			case MSG_REGISTER_CLIENT:								
				clientMessenger = msg.replyTo;
				break;
			case MSG_UNREGISTER_CLIENT:
				Log.i(TAG,"setting client messenger to null");
				clientMessenger = null;
				break;
			}
		}			
	}
	
	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder) { 			
			mBound = true;
			LocalBinder locBinder = (LocalBinder) binder;
			service = locBinder.getService();
			service.setOmfService(OmfService.this);			
		}

        public void onServiceDisconnected(ComponentName className) {              	
        	mBound = false;
        	service = null;
        }
    };
    
    public void updateTweetCounter(int value) {
    	tweetCounter += value;
    	sendMessageToTarget(MESSAGE_COUNTER_UPDATE, tweetCounter);    	
    }
    
    	
	private void bindToTwitterService() {
		Intent intent = new Intent(getApplicationContext(), TwitterService.class);
    	bindService(intent, mConnection, BIND_AUTO_CREATE);
		
	}

	private void createActivity(Context context) {
		Intent refreshIntent = new Intent(context,ShowTweetListActivity.class);
		refreshIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);		
		context.startActivity(refreshIntent);
		
	}

    public void disableDisMode() {
    	PrefsActivity.disableDisasterMode(getApplicationContext());
		setPreferences(getApplicationContext(),false);
		Toast.makeText(getApplicationContext(), "Disaster Mode disabled by OMF", Toast.LENGTH_SHORT).show();
		
	}
    
	private void setPreferences(Context context, boolean value) {
		SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
		editor.putBoolean("prefDisasterMode", value);
		editor.commit();
	}

	public void enableDisMode() {
		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mBluetoothAdapter.isEnabled())
			ScanningAlarm.setBluetoothInitialState(getApplicationContext(), true);
		else
			ScanningAlarm.setBluetoothInitialState(getApplicationContext(), false);
		
		mBluetoothAdapter.enable();
		setPreferences(getApplicationContext(),true);
		new ScanningAlarm(getApplicationContext(),true);
		Toast.makeText(getApplicationContext(), "Disaster Mode enabled by OMF", Toast.LENGTH_SHORT).show();	
		
	}
	
	   public void sendMessageToTarget(int message, int count) {
	    	if (clientMessenger == null) {	    		
	    		return;  
	    	}	    	
	    	Message msg = Message.obtain(null, message, count, 0);	    	
	    	try {
	    		clientMessenger.send(msg);	    		
	    	} catch (RemoteException e) {    
	    		Log.e(TAG,"remote exception",e);
	    	}
	    }
	
    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(getApplicationContext(), "binding", Toast.LENGTH_SHORT).show();       
        return localMessenger.getBinder();
    }

	
	public void unbindService() {
		if (mBound) {
			if (service != null)
				service.setOmfService(null);		
			unbindService(mConnection);
		}		
	}

	@Override
	public void onDestroy() {
		Log.i(TAG,"Destroying omf service");
		// TODO Auto-generated method stub
		super.onDestroy();			
		unbindService();
	}   
    
	

}
