package ch.ethz.twimight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Starts the updater service after the boot process.
 * @author pcarta
 *
 */
public class BootBroadReceiver extends BroadcastReceiver {
	private static final String TAG = "BootBroadReceiver";
	
	/**
	 * Starts the updater service upon receiving a boot Intent.
	 * @param context
	 * @param intent
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG,"starting app on boot");
		context.startService( new Intent(context, UpdaterService.class) );

	}
	
}
