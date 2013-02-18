package ch.ethz.twimight.net.opportunistic;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StateChangedReceiver extends BroadcastReceiver {
	
	BluetoothComms comms;
	
	public StateChangedReceiver(BluetoothComms comms) {
		this.comms = comms;
	}
	
	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))	{
			
			int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
			if (state == BluetoothAdapter.STATE_OFF){
				BluetoothAdapter.getDefaultAdapter().enable();
				context.unregisterReceiver(this);
				comms.start();
			}
			
		}

	}

}
