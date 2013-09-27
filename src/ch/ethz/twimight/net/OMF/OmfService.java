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
import android.preference.PreferenceManager;
import android.widget.Toast;
import ch.ethz.twimight.activities.PrefsActivity;
import ch.ethz.twimight.activities.ShowTweetListActivity;
import ch.ethz.twimight.net.opportunistic.ScanningAlarm;

public class OmfService extends Service {
	
    static final int MSG_START_APP = 1;	
    static final int MSG_START_DIS_MODE = 2;	
    static final int MSG_STOP_DIS_MODE = 3;	
    
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
                    break;
                case MSG_STOP_DIS_MODE:
                    disableDisMode();
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

	/**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(getApplicationContext(), "binding", Toast.LENGTH_SHORT).show();
        return mMessenger.getBinder();
    }
	

}
