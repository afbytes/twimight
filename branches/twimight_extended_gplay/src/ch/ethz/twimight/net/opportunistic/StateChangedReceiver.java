package ch.ethz.twimight.net.opportunistic;

import ch.ethz.twimight.net.opportunistic.DevicesReceiver.ScanningFinished;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StateChangedReceiver extends BroadcastReceiver {

	public static interface BtSwitchingFinished {

		public void onSwitchingFinished();
	}

	private BtSwitchingFinished sf;

	@Override
	public void onReceive(Context context, Intent intent) {
		if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED))	{

			int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
			if (state == BluetoothAdapter.STATE_OFF){
				BluetoothAdapter.getDefaultAdapter().enable();				
				
			} else if (state == BluetoothAdapter.STATE_ON) {
				sf.onSwitchingFinished();
			}

		}

	}
	
	public void setListener(BtSwitchingFinished sf) {
		this.sf = sf;
	}

}
