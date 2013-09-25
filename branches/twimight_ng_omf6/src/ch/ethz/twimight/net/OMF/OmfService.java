package ch.ethz.twimight.net.OMF;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.view.View;

public class OmfService extends Service {
	
    static final int MSG_SAY_HELLO = 1;	
	
	Messenger mService = null;

    /** Flag indicating whether we have called bind on the service. */
    boolean mBound;
    
    @Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
    
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {          
            mService = new Messenger(service);
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {            
            mService = null;
            mBound = false;
        }
    };

    public void sayHello(View v) {
        if (!mBound) return;        
        Message msg = Message.obtain(null, MSG_SAY_HELLO, 0, 0);
        try {
            mService.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

	

}
