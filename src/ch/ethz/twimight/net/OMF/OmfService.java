package ch.ethz.twimight.net.OMF;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
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

public class OmfService extends Service {
	
	public static final String TAG = "OMFService";


	static final int MSG_START_APP = 1;	
	static final int MSG_START_DIS_MODE = 2;	
	static final int MSG_STOP_DIS_MODE = 3;	
	static final int MSG_REGISTER_CLIENT = 4;   
	static final int MSG_UNREGISTER_CLIENT = 5;
	
	public static final int MSG_TEST_BIDIRECTIONAL = 21;
	
	private Messenger clientMessenger;
	/**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger localMessenger = new Messenger(new IncomingHandler());

	/**
	 * Handler of incoming messages from external clients.
	 */
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			
			case MSG_START_APP:
				createActivity(getApplicationContext());
				break;
			case MSG_START_DIS_MODE:
				enableDisMode();				
				sendMessageToTarget(MSG_TEST_BIDIRECTIONAL);
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
	
	   public void sendMessageToTarget(int message) {
	    	if (clientMessenger == null) {	    		
	    		return;  
	    	}	    	
	    	Message msg = Message.obtain(null, message, 0, 0);
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

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		return START_STICKY;
	}
    
    
	

}
