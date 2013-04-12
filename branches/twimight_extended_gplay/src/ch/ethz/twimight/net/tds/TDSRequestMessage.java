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

import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.location.Location;
import android.util.Log;
import ch.ethz.twimight.security.KeyManager;
import ch.ethz.twimight.util.Constants;

/**
 * A collection of JSONObjects to send to the Twimight Disaster Server
 * @author thossmann
 *
 */
public class TDSRequestMessage {

	private int version;
	Context context;
	
	private JSONObject authenticationObject;
	private JSONObject locationObject;
	private JSONObject bluetoothObject;
	private JSONObject certificateObject;
	private JSONObject revocationObject;
	private JSONObject followerObject;
	private JSONObject statisticObject;
	
	
	/**
	 * Constructor
	 */
	public TDSRequestMessage(Context context){
		version = Constants.TDS_MESSAGE_VERSION;
		this.context=context;
	}
	
	/**
	 * creates JSONObject to authenticate with the TDS
	 * @param client
	 * @return JSON Object
	 * @throws JSONException 
	 */
	public void createAuthenticationObject(int consumerId, String twitterAccessToken, String twitterAccessTokenSecret) throws JSONException {
		authenticationObject = new JSONObject();
		authenticationObject.put("consumer_id", consumerId);
		authenticationObject.put("access_token", twitterAccessToken);
		authenticationObject.put("access_token_secret", twitterAccessTokenSecret);
	}
	

	/**
	 * creates JSONObject to push the Bluetooth MAC to the TDS
	 * @param client
	 * @return JSON Object
	 * @throws JSONException 
	 */
	public void createBluetoothObject(String mac) throws JSONException{

		// the JSON Object will contain our name values
		bluetoothObject = new JSONObject();
		bluetoothObject.put("mac", mac);
	}
	

	

	/**
	 * creates JSONObject to push Statistics to the TDS
	 * @param 
	 * @return JSON Object
	 * @throws JSONException 
	 */
	public void createStatisticObject(Cursor stats, long follCount) throws JSONException{

				
		if(stats != null) {			
			statisticObject = new JSONObject();
			JSONArray statisticArray = new JSONArray();
			
			while(!stats.isAfterLast()) {
				
				JSONObject row = new JSONObject();
				row.put("latitude", Double.toString(stats.getDouble(stats.getColumnIndex("lat"))));
				row.put("longitude", Double.toString(stats.getDouble(stats.getColumnIndex("lng"))));
				row.put("accuracy", Integer.toString(stats.getInt(stats.getColumnIndex("accuracy"))) );
				row.put("provider", stats.getString(stats.getColumnIndex("provider")));
				Log.i("TDSRequestMessage","timestamp from db: " + Long.toString(stats.getLong(stats.getColumnIndex("timestamp"))));
				row.put("timestamp", Long.toString(stats.getLong(stats.getColumnIndex("timestamp"))));
				row.put("network", stats.getString(stats.getColumnIndex("network")));
				row.put("event", stats.getString(stats.getColumnIndex("event")));
				row.put("link", stats.getString(stats.getColumnIndex("link")));
				row.put("followers_count", follCount);
				
				statisticArray.put(row);
				stats.moveToNext();
			}
			stats.close();
			
			statisticObject.put("content", statisticArray);
			
		}
	}
	

	/**
	 * Sends all the locations since the last successful update to the TDS
	 * @param client
	 * @return true if operation was successful, false otherwise
	 * @throws JSONException 
	 */
	public int createLocationObject(ArrayList<Location> locationList) throws JSONException{
		
		locationObject = new JSONObject();
		
		if(!locationList.isEmpty()){
			
			JSONArray locationArray = new JSONArray();
			// iterate through all location
			Iterator<Location> iterator = locationList.iterator();
		    while (iterator.hasNext()) {
		    	Location loc = iterator.next();
		    	JSONObject wayPoint = new JSONObject();
		    	
		    	Integer providerInteger = Constants.locationProvidersMap.get(loc.getProvider());
		    	
		    	// TODO: reduce precision of lat, lng
	    		wayPoint.put("latitude", Double.toString(loc.getLatitude()));
	    		wayPoint.put("longitude", Double.toString(loc.getLongitude()));
	    		wayPoint.put("accuracy", Integer.toString((int)Math.round(loc.getAccuracy())));
	    		wayPoint.put("provider", providerInteger==null? Integer.toString(0):providerInteger.toString());
	    		wayPoint.put("date", Long.toString(loc.getTime()));
	    		
	    		locationArray.put(wayPoint);
	    	
		    }
		    
		    locationObject.put("waypoints", locationArray);
		}
		
		return 0;
	}
	
	/**
	 * Creates a JSON object for the revocation list update request
	 * @param lastUpdate
	 */
	public void createRevocationObject(int currentVersion) throws JSONException {
		revocationObject = new JSONObject();
		revocationObject.put("version", currentVersion);
	}

	
	/**
	 * Creates the JSON object containing the public key to send to the TDS
	 * @return
	 * @throws JSONException 
	 */
	public void createCertificateObject(KeyPair toSign, KeyPair toRevoke) throws JSONException {
		
		certificateObject = new JSONObject();
		if(toSign!=null){
			certificateObject.put("public_key", KeyManager.getPemPublicKey(toSign));
		}
		
		if(toRevoke!=null){
			certificateObject.put("revoke", KeyManager.getPemPublicKey(toRevoke));
		}
	}
	
	/**
	 * Creates the JSON object for a request for follower keys
	 * @param lastUpdate
	 * @throws JSONException
	 */
	public void createFollowerObject(long lastUpdate) throws JSONException{
		followerObject = new JSONObject();
		followerObject.put("last_update", lastUpdate);
	}
	
	
	/**
	 * is the authentication object set?
	 * @return
	 */
	public boolean hasAuthenticationObject(){
		return authenticationObject != null;
	}
	
	/**
	 * is the Bluetooth object set?
	 * @return
	 */
	public boolean hasBluetoothObject(){
		return bluetoothObject != null;
	}
	

	
	/**
	 * is the Bluetooth object set?
	 * @return
	 */
	public boolean hasStatisticObject(){
		return statisticObject != null;
	}
	
	
	/**
	 * Is the version field set?
	 * @return
	 */
	public boolean hasVersion(){ 
		return version != 0;
	}
	
	/**
	 * Is the Certificate object set?
	 */
	public boolean hasCertificatObject(){
		return certificateObject != null;
	}
	
	/**
	 * Is the Location object set?
	 */
	public boolean hasLocationObject(){
		return locationObject != null;
	}
	
	/**
	 * Is the Revocation object set?
	 * @return
	 */
	public boolean hasRevocationObject() {
		return revocationObject != null;
	}

	/**
	 * Is the Follower object set?
	 * @return
	 */
	public boolean hasFollowerObject() {
		return followerObject != null;
	}

	
	/**
	 * Getter
	 * @return
	 */
	public int getVersion(){
		return version;
	}
	
	/**
	 * Getter
	 * @return
	 */
	public JSONObject getAuthenticationObject(){
		return authenticationObject;
	}
	
	/**
	 * Getter
	 * @return
	 */
	public JSONObject getBluetoothObject(){
		return bluetoothObject;
	}
	
	

	
	/**
	 * Getter
	 * @return
	 */
	public JSONObject getStatisticObject(){
		return statisticObject;
	}
	
	/**
	 * Getter
	 * @return
	 */
	public JSONObject getLocationObject(){
		return locationObject;
	}
	
	/**
	 * Getter
	 * @return
	 */
	public JSONObject getCertificateObject(){
		return certificateObject;
	}

	/**
	 * Getter
	 * @return
	 */
	public JSONObject getRevocationObject(){
		return revocationObject;
	}

	/**
	 * Getter
	 * @return
	 */
	public JSONObject getFollowerObject(){
		return followerObject;
	}
}
