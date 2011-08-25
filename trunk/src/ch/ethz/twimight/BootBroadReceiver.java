package ch.ethz.twimight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootBroadReceiver extends BroadcastReceiver {
	private static final String TAG = "BootBroadReceiver";
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG,"starting app on boot");
		context.startService( new Intent(context, UpdaterService.class) );
		//Intent activityIntent = new Intent(context, MyTwitter.class);		
		//activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);	
		//context.startActivity(activityIntent); 

	}
	
}
