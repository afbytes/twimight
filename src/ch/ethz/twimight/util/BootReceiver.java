/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/
package ch.ethz.twimight.util;

import ch.ethz.twimight.location.LocationAlarm;
import ch.ethz.twimight.net.opportunistic.ScanningService;
import ch.ethz.twimight.net.tds.TDSAlarm;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Starts the updater service after the boot process.
 * @author pcarta
 *
 */
public class BootReceiver extends BroadcastReceiver {
	private static final String TAG = "BootBroadcastReceiver";
	
	/**
	 * Starts the updater service upon receiving a boot Intent.
	 * @param context
	 * @param intent
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		Log.i(TAG,"starting app at boot time");
		
		// Start the service for communication with the TDS
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefTDSCommunication", Constants.TDS_DEFAULT_ON)==true){
			new TDSAlarm(context, Constants.TDS_UPDATE_INTERVAL);
		}
		
		
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON)==true){
			context.startService(new Intent(context, ScanningService.class));
		}
		
		
		// Start the location update service
		if(PreferenceManager.getDefaultSharedPreferences(context).getBoolean("prefLocationUpdates", Constants.LOCATION_DEFAULT_ON)==true){
			new LocationAlarm(context, Constants.LOCATION_UPDATE_TIME);
		}

	}
	
}
