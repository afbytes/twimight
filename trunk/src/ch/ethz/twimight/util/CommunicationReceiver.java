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
import android.net.ConnectivityManager;
import android.util.Log;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.net.Html.StartServiceHelper;
import ch.ethz.twimight.net.tds.TDSAlarm;
import ch.ethz.twimight.net.twitter.TwitterSyncService;

/**
 * Listends for changes in connectivity and starts the TDSThread if a new
 * connection is detected.
 * 
 * @author thossmann
 * 
 */
public class CommunicationReceiver extends BroadcastReceiver {

	private static final String TAG = CommunicationReceiver.class.getName();

	@Override
	public void onReceive(Context context, Intent intent) {
		// protection against forged intents
		if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {

			Log.i(TAG, "CommunicationReceiver called");
			// connectivity changed!
			StartServiceHelper.startService(context);

			// TDS communication
			if (TDSAlarm.isTdsEnabled(context)) {
				// remove currently scheduled updates and schedule an immediate
				// one
				new TDSAlarm();
			}

			if (!LoginActivity.hasTwitterId(context)) {
				Intent loginIntent = new Intent(context, TwitterSyncService.class);
				loginIntent.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_LOGIN);
				context.startService(loginIntent);
			} else {
				Intent syncTransactionalIntent = new Intent(context, TwitterSyncService.class);
				syncTransactionalIntent.putExtra(TwitterSyncService.EXTRA_KEY_ACTION, TwitterSyncService.EXTRA_ACTION_SYNC_ALL_TRANSACTIONAL);
				context.startService(syncTransactionalIntent);
			}
		}

	}
}
