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
package ch.ethz.twimight.location;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import ch.ethz.twimight.data.LocationDBHelper;
import ch.ethz.twimight.util.Constants;

/**
 * This is the thread for periodic localization in Twimight.
 * @author thossmann
 *
 */
public class LocationThread extends HandlerThread{


	private static final String TAG = "LocationThread"; /** For Debugging */

	private LocationDBHelper dbHelper;

	private Context context;

	private LocationManager lm;
	private static Location loc;

	private LocationListener locationListener;

	private Handler handler=new Handler();

	private Runnable timeOutRunnable;

	/**
	 * Constructor
	 */
	public LocationThread(Context context){

		super(TAG);

		this.context = context;
		
		
		// open DB connection
		dbHelper = new LocationDBHelper(this.context);
		dbHelper.open();

		// get a location manager
		lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
		
		locationListener = new LocationListener() {
			public void onLocationChanged(Location location) {

				// is the fix good enough to stop listening?
				if(location.hasAccuracy() && location.getAccuracy() <= Constants.LOCATION_ACCURACY){

					// stop the timeout thread
					handler.removeCallbacks(timeOutRunnable);

					loc = location;
					
					Log.d(TAG,"received a satisfying fix");
					quit();
				}
			}

			public void onProviderDisabled(String provider) {}
			public void onProviderEnabled(String provider) {}
			public void onStatusChanged(String provider, int status, Bundle extras) {}
		};
		
		//release the wake lock
		LocationAlarm.releaseWakeLock();

	}

	/**
	 * The run function of the thread.
	 */
	@Override
	public void run() {

		Log.d(TAG, "running");

		try {
			super.run();
		} finally {
			onPostExecute();
		}

	}

	/**
	 * Is triggered once the looper is set up.
	 */
	@Override
	protected void onLooperPrepared() {
		try {
			onPreExecute();
		}
		catch (RuntimeException e) {
			onPostExecute();
			throw(e);
		}
	}

	/**
	 * Here we prepare everything
	 */
	private void onPreExecute() {

		Log.d(TAG, "starting timeout");
		timeOutRunnable = new Runnable() {
			public void run() {
				Log.d(TAG, "timeout expired");
				quit();
			}
		};

		handler.postDelayed(timeOutRunnable, Constants.LOCATION_WAIT);

		// request location updates from both GPS and Wifi. In the callback we will decide when we are happy with a location and remove the update listener
		try{
			lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 0, locationListener);
			lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 0, locationListener);
		} catch(Exception e) {
			Log.d(TAG,"Can't request location Updates: " + e.toString());
			return;
		}

	}

	/**
	 * Clean up after execution
	 */
	private void onPostExecute(){

		Log.d(TAG, "cleaning up");

		// stop listening
		if ((lm != null) && (locationListener != null)) {
	        lm.removeUpdates(locationListener);
	    }
		locationListener = null;
		lm = null;

		// insert into DB
		if(loc != null){
			dbHelper.insertLocation(loc);
		}

		//randomize interval until next location update
		LocationAlarm.scheduleLocationUpdate(context, Math.round(Math.random()*2*Constants.LOCATION_UPDATE_TIME));

		// release everything
		context = null;
		loc = null;
		timeOutRunnable = null;
		dbHelper = null;
		handler = null;	

		Log.d(TAG, "done");
	}

};
