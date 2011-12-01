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
package ch.ethz.twimight.net.tds;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.FriendsKeysDBHelper;
import ch.ethz.twimight.data.LocationDBHelper;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.data.RevocationDBHelper;
import ch.ethz.twimight.security.CertificateManager;
import ch.ethz.twimight.security.KeyManager;
import ch.ethz.twimight.security.RevocationListEntry;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.EasySSLSocketFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * TDSThread, together with TDSAlarm 
 * form the controller for the communication with the Twimight
 * Disaster Server.
 * This is the thread for communication with the Twimight Disaster Server.
 * @author theus
 *
 */
public class TDSThread implements Runnable{


	private static final String TDS_LAST_UPDATE = "TDSLastUpdate"; /** Name of the last update in shared preferences */
	private static final String TDS_UPDATE_INTERVAL = "TDSUpdateInterval"; /** Name of the update interval in shared preference */

	private static final String TAG = "TDSThread"; /** For Debugging */

	private Context context;
	
	private boolean successful;

	/**
	 * Constructor
	 */
	public TDSThread(Context context){
		this.context = context;
	}

	/**
	 * The run function of the thread. Triggers and reschedules communication with TDS.
	 */
	@Override
	public void run() {
		
		try{	
			
			Log.i(TAG, "running");
			
			successful = false;
	
			// we check if the last update is more than TDS_MIN_UPDATE_INTERVAL ago
			if(needUpdate()){
				// wait a bit until we have connectivity
				// TODO: This is a hack, we should wait for a connectivity change intent, or a timeout, to proceed.
				Thread.sleep(Constants.WAIT_FOR_CONNECTIVITY);
				
				// on successful update, we reschedule after TDS_UPDATE_INTERVAL
				if(update()){
					successful = true;
				} else {
					successful = false;
				}
			} 
			
		} catch (Exception e) {
			Log.e(TAG, "Exception while running the TDSThread");
		} finally {
			
			long next = 0;
			
			// reschedule
			if(successful){
				// remember the time of successful update
				setLastUpdate();
				
				// reset retry interval
				setUpdateInterval(Constants.TDS_UPDATE_RETRY_INTERVAL);
				
				// reschedule
				next = Constants.TDS_UPDATE_INTERVAL;
				TDSAlarm.scheduleCommunication(context, Constants.TDS_UPDATE_INTERVAL);
				
				Log.i(TAG,"update successful");
				
			} else {
				
				// get the last successful update
				Long lastUpdate = getLastUpdate();
				
				// get from shared preferences
				Long currentUpdateInterval = getUpdateInterval();
				
				// when should the next update be scheduled?
				Long nextUpdate = 0L;
				if(System.currentTimeMillis()-lastUpdate > Constants.TDS_UPDATE_INTERVAL){
					nextUpdate = currentUpdateInterval;
				} else {
					nextUpdate = Constants.TDS_UPDATE_INTERVAL - (System.currentTimeMillis()-lastUpdate);
				}
				
				// exponentially schedule again after TDS_UPDAT_RETRY_INTERVAL
				next = nextUpdate;
				TDSAlarm.scheduleCommunication(context, nextUpdate);
				
				currentUpdateInterval *= 2;
				// cap at TDS_UPDATE_INTERVAL
				if(currentUpdateInterval > Constants.TDS_UPDATE_INTERVAL){
					currentUpdateInterval = Constants.TDS_UPDATE_INTERVAL;
				}
				
				// write back to shared preferences
				setUpdateInterval(currentUpdateInterval);
				Log.i(TAG, "update not successful");
			}

			Log.i(TAG, "next update scheduled in " + next + " ms");
			
			// finally, release the lock
			TDSAlarm.releaseWakeLock();

		}
		

	}


	/**
	 * The communication with the TDS. Simple call containing all periodic updating tasks and parse return message
	 * TODO: This is a huuuuge controller, needs re-factoring!
	 * @return true if the connection with the TDS was successful, false otherwise.
	 */
	private boolean update(){

		TDSCommunication tds;
		
		try{
			tds = new TDSCommunication(context, Constants.CONSUMER_ID, LoginActivity.getAccessToken(context), LoginActivity.getAccessTokenSecret(context));
			
			// push locations to the server
			LocationDBHelper locationAdapter = new LocationDBHelper(context);
			locationAdapter.open();
	
			Date sinceWhen = new Date(getLastUpdate());
			ArrayList<Location> locationList = (ArrayList<Location>) locationAdapter.getLocationsSince(sinceWhen);
			if(!locationList.isEmpty()){
				tds.createLocationObject(locationList);
			}
			
			// request potential bluetooth peers
			String mac = PreferenceManager.getDefaultSharedPreferences(context).getString("mac", null);
			if(mac!=null){
				tds.createBluetoothObject(mac);
			}
	
			// revocation list
			RevocationDBHelper rm = new RevocationDBHelper(context);
			rm.open();
			tds.createRevocationObject(rm.getCurrentVersion());
			
			// do we need a new certificate?
			CertificateManager cm = new CertificateManager(context);
			if(cm.needNewCertificate()){
				KeyManager km = new KeyManager(context);
				tds.createCertificateObject(km.getKey(), null);
				Log.i(TAG, "we need a new certificate");
			} else {
				Log.i(TAG, "no need for a new certificate");
			}
			
			
			// follower key list
			FriendsKeysDBHelper fm = new FriendsKeysDBHelper(context);
			tds.createFollowerObject(fm.getLastUpdate());

		} catch(Exception e) {
			Log.e(TAG, "Exception while assembling request");
			return false;
		}

		boolean success = false;
		// Send the request
		try {
			success = tds.sendRequest(getClient());
		} catch (GeneralSecurityException e) {
			Log.e(TAG, "GeneralSecurityException while sending TDS request");
		}
		
		if(!success) {
			Log.e(TAG, "Error while sending");
			return false;
		}
		
		Log.i(TAG, "success");

		try {

			// authentication
			String twitterId = tds.parseAuthentication();
			if(!twitterId.equals(LoginActivity.getTwitterId(context))){
				Log.e(TAG, "Twitter ID mismatch!");
				return false;
			}
			Log.i(TAG, "authentication parsed");
			
			
			// bluetooth
			List<String> macsList = tds.parseBluetooth();
			if(!macsList.isEmpty()){
				
				MacsDBHelper dbHelper = new MacsDBHelper(context);
				
				// temporarily de-activate all local MACs
				dbHelper .updateMacsDeActive();
				
				// insert new macs in the DB
				Iterator<String> iterator = macsList.iterator(); 
				while(iterator.hasNext()) {

				    String mac = iterator.next();
				    if(dbHelper.createMac(dbHelper.mac2long(mac), 1) == -1){
						dbHelper.updateMacActive(dbHelper.mac2long(mac), 1);
						Log.i(TAG, "Already have MAC: " + mac);
					} else {
						Log.i(TAG,"New MAC: " + mac);
					}
				}
			} else {
				Log.i(TAG, "bluetooth mac list empty");
			}
			Log.i(TAG, "bluetooth parsed");
			
			// location, nothing to do here
			
			// certificate
			String certPem = tds.parseCertificate();
			if(certPem != null){
				CertificateManager cm = new CertificateManager(context);
				cm.setCertificate(certPem);
			}
			Log.i(TAG, "certificate parsed");
			
			// revocation
			RevocationDBHelper rm = new RevocationDBHelper(context);
			rm.open();
			rm.deleteExpired();
			// first, we check the version
			int revocationListVersion = tds.parseRevocationVersion();
			if(revocationListVersion!=0 && revocationListVersion > rm.getCurrentVersion()){
				// next, we get the update of the list
				List<RevocationListEntry> revocationList = tds.parseRevocation();
				if(!revocationList.isEmpty()){
					rm.processUpdate(revocationList);
				}
				rm.setCurrentVersion(revocationListVersion);
				// check if our certificate is on the new revocation list
				CertificateManager cm = new CertificateManager(context);
				if(rm.isRevoked(cm.getSerial())){
					Log.i(TAG, "Our certificate got revoked! Deleting key and certificate");
					cm.deleteCertificate();
					KeyManager km = new KeyManager(context);
					km.deleteKey();
				}
			} else {
				Log.i(TAG, "no new revocations");
			}
			Log.i(TAG, "revocation parsed");
			
			// Followers
			FriendsKeysDBHelper fm = new FriendsKeysDBHelper(context);
			fm.open();
			List<TDSPublicKey> keyList = tds.parseFollower();
			if(keyList != null){
				fm.insertKeys(keyList);
				long lastUpdate = tds.parseFollowerLastUpdate();
				if(lastUpdate != 0){
					fm.setLastUpdate(lastUpdate);
				}
			}
			Log.i(TAG, "followers parsed");
			
		} catch(Exception e) {
			Log.e(TAG, "Exception while parsing response");
		}
		
		return true;
	}

	/**
	 * Set up an HTTP client.
	 * @return
	 * @throws GeneralSecurityException
	 */
	private DefaultHttpClient getClient() throws GeneralSecurityException {
		
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));
		 
		HttpParams params = new BasicHttpParams();
		params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
		params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
		params.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);
		
		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpConnectionParams.setConnectionTimeout(params, Constants.HTTP_CONNECTION_TIMEOUT); // Connection timeout
		HttpConnectionParams.setSoTimeout(params, Constants.HTTP_SOCKET_TIMEOUT); // Socket timeout

		
		ClientConnectionManager cm = new SingleClientConnManager(params, schemeRegistry);
		return new DefaultHttpClient(cm, params);
		
	}
	
	/**
	 * Get the time (unix time stamp) of the last successful update
	 */
	private Long getLastUpdate(){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(TDS_LAST_UPDATE, 0);

	}

	/**
	 * Set the current time (unix time stamp) as the time of last successful update
	 */
	private void setLastUpdate() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putLong(TDS_LAST_UPDATE, System.currentTimeMillis());
		prefEditor.commit();
	}
	
	/**
	 * Get the current update interval from Shared preferences
	 */
	private Long getUpdateInterval() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getLong(TDS_UPDATE_INTERVAL, Constants.TDS_UPDATE_RETRY_INTERVAL);

	}
	
	/**
	 * Stores the current update interval in Shared preferences 
	 */
	private void setUpdateInterval(Long updateInterval) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putLong(TDS_UPDATE_INTERVAL, updateInterval);
		prefEditor.commit();
		
	}
	
	/**
	 * Do we need a periodic update?
	 */
	private boolean needUpdate(){
		
		// when was the last successful update?
		if(System.currentTimeMillis() - getLastUpdate() > Constants.TDS_UPDATE_INTERVAL){
			return true;
		} else {
			return false;
		}
	}
	
	public static void resetLastUpdate(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putLong(TDS_LAST_UPDATE, 0L);
		prefEditor.commit();
	}
	
	public static void resetUpdateInterval(Context context){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putLong(TDS_UPDATE_INTERVAL, Constants.TDS_UPDATE_INTERVAL);
		prefEditor.commit();		
	}
};
