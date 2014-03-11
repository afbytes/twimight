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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import ch.ethz.twimight.R;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.net.Html.StartServiceHelper;
import ch.ethz.twimight.net.opportunistic.ScanningAlarm;
import ch.ethz.twimight.net.tds.TDSAlarm;
import ch.ethz.twimight.net.twitter.TwitterAlarm;

/**
 * Starts the updater service after the boot process.
 * 
 * @author pcarta
 * @author thossmann
 * @author Steven Meliopoulos
 * 
 */
public class BootReceiver extends BroadcastReceiver {
	private static final String TAG = BootReceiver.class.getName();

	/**
	 * Starts the twimight services upon receiving a boot Intent.
	 * 
	 * @param context
	 * @param intent
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		// protection against forged intents
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			Log.i(TAG, "BootReceiver called");

			// we only start the services if we are logged in (i.e., we have the
			// tokens from twitter)
			if (LoginActivity.hasAccessToken(context) && LoginActivity.hasAccessTokenSecret(context)) {

				StartServiceHelper.startService(context);

				// Start the alarm for communication with the TDS
				boolean tdsCommunicationEnabled = Preferences.getBoolean(context, R.string.pref_key_tds_communication,
						Constants.TDS_DEFAULT_ON);
				if (tdsCommunicationEnabled) {
					new TDSAlarm(context, Constants.TDS_UPDATE_INTERVAL);
				}

				// start the scanning alarm for disaster mode
				boolean disasterModeEnabled = Preferences.getBoolean(context, R.string.pref_key_disaster_mode,
						Constants.DISASTER_DEFAULT_ON);
				if (disasterModeEnabled) {
					new ScanningAlarm(context, false);
				}

				// start the twitter update alarm
				TwitterAlarm.initialize(context);
			}
		}
	}

}
