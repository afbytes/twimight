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
package ch.ethz.twimight.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.location.Location;

/**
 * Manages the Location table in the DB
 * @author thossmann
 *
 */
public class LocationDBHelper {
	
	// Database fields
	public static final String KEY_LOCATION_ID = "_id";
	public static final String KEY_LOCATION_LNG = "lng";
	public static final String KEY_LOCATION_LAT = "lat";
	public static final String KEY_LOCATION_ACCURACY = "accuracy";
	public static final String KEY_LOCATION_DATE = "loc_date";
	public static final String KEY_LOCATION_PROVIDER = "provider";
	
	private Context context;
	
	private SQLiteDatabase database;
	private DBOpenHelper dbHelper;
	
	
	/**
	 * Constructor.
	 * @param context
	 */
	public LocationDBHelper(Context context) {
		this.context = context;
	}

	/**
	 * Opens the DB.
	 * @return
	 * @throws SQLException
	 */
	public LocationDBHelper open() throws SQLException {
		dbHelper = DBOpenHelper.getInstance(context);
		database = dbHelper.getWritableDatabase();
		return this;
	}

	/**
	 * We don't close the DB since there is only one instance!
	 */
	public void close() {
		
	}
	
	/**
	 * Inserts a location update into the DB
	 * @param loc
	 * @return
	 */
	public boolean insertLocation(Location loc) {
		
		if(loc != null){

			ContentValues locUpdate = createContentValues(loc.getLatitude(), loc.getLongitude(), loc.getAccuracy(), loc.getTime(), loc.getProvider());
			
			try{
				database.insert(DBOpenHelper.TABLE_LOCATIONS, null, locUpdate);
				
			} catch (SQLiteException e) {
				return false;
			}
			return false;
			
		}
		return false;
	}
	
	/**
	 * Creates an ArrayList of locations since Date d
	 * @param d Date
	 * @return ArrayList of locations
	 */
	public List<Location> getLocationsSince(Date d){
		ArrayList<Location> locationList = new ArrayList<Location>();
		
		Cursor mCursor = database.query(true, DBOpenHelper.TABLE_LOCATIONS, new String[] {
				KEY_LOCATION_LNG, KEY_LOCATION_LAT, KEY_LOCATION_ACCURACY, KEY_LOCATION_PROVIDER, KEY_LOCATION_DATE},
				KEY_LOCATION_DATE + ">=" + Long.toString(d.getTime()), null, null, null, null, null);
		
		if(mCursor != null){
			mCursor.moveToFirst();
			
	
			while (mCursor.isAfterLast() == false) {
	        
				Location tmp = new Location(mCursor.getString(mCursor.getColumnIndex(LocationDBHelper.KEY_LOCATION_PROVIDER)));
				tmp.setLatitude(mCursor.getFloat(mCursor.getColumnIndex(LocationDBHelper.KEY_LOCATION_LAT)));
				tmp.setLongitude(mCursor.getFloat(mCursor.getColumnIndex(LocationDBHelper.KEY_LOCATION_LNG)));
				tmp.setAccuracy(mCursor.getFloat(mCursor.getColumnIndex(LocationDBHelper.KEY_LOCATION_ACCURACY)));
				tmp.setTime(mCursor.getLong(mCursor.getColumnIndex(LocationDBHelper.KEY_LOCATION_DATE)));
				
				locationList.add(tmp);
	       	    mCursor.moveToNext();
	        }
	        mCursor.close();
			
		}
		
		return locationList;
	}
	
	/**
	 * Delete old locations from DB
	 * TODO
	 */
	public void ageLocations(){
		
	}

	/**
	 * Creates a Location record to insert in the DB
	 * @return
	 */
	private ContentValues createContentValues(double lat, double lng, float accuracy, double locDate, String provider) {
		ContentValues values = new ContentValues();
		values.put(KEY_LOCATION_LAT, lat);
		values.put(KEY_LOCATION_LNG, lng);
		values.put(KEY_LOCATION_ACCURACY, (int) Math.round(accuracy));
		values.put(KEY_LOCATION_DATE, locDate);
		values.put(KEY_LOCATION_PROVIDER, provider);
		return values;
	}
}
