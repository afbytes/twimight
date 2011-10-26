package ch.ethz.twimight;

import java.math.BigInteger;
import java.util.Date;

import winterwell.jtwitter.Twitter.Status;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.util.Log;

public class TweetDbActions {
	DbOpenHelper dbHelper;
	private SQLiteDatabase db = UpdaterService.db;	
	private Cursor cursorNewPeer,cursorDisaster,cursorSelected, cursorPeers;
	private static final String TAG	= "TweetDbActions";
	

	 void createTables(Context cont, SQLiteDatabase db) {
		    String sql = cont.getResources().getString(R.string.sql);	    
		    String sqlFavorites = cont.getResources().getString(R.string.sqlFavorites);
		    String sqlMentions = cont.getResources().getString(R.string.sqlMentions);
		    String sqlDirect = cont.getResources().getString(R.string.sqlDirect);
		    String sqlPictures = cont.getResources().getString(R.string.sqlProfilePictures);
		    String sqlFriends = cont.getResources().getString(R.string.sqlFriends);
		    String sqlDirectOutgoing = cont.getResources().getString(R.string.sqlDirectOutgoing);
		    db.execSQL(sql); // execute the sql	   
		    db.execSQL(sqlFavorites); 
		    db.execSQL(sqlMentions);   
		    db.execSQL(sqlDirect);  
		    db.execSQL(sqlPictures);
		    db.execSQL(sqlFriends);
		    db.execSQL(sqlDirectOutgoing);
	}
	  
	
	public long getTimestampLastTweet(String mConnectedDeviceName ){
	  long recentTweetTime = 0;
	  
	  String query = "SELECT tim.created_at FROM DisasterTable AS tim, FriendsTable AS f " +
	  		"WHERE tim.userCode = f._id AND f.user = '" + mConnectedDeviceName + "' ORDER BY tim.created_at DESC";
		cursorNewPeer = rawQuery(query);		
	  
	  if (cursorNewPeer.getCount() > 0) {
		  cursorNewPeer.moveToFirst();
		  recentTweetTime = cursorNewPeer.getLong(cursorNewPeer.getColumnIndex(DbOpenHelper.C_CREATED_AT)); 		  
	  }	
	  return recentTweetTime;
	}
	
	Cursor disasterDbQuery(String where, String order) {
		  if (db != null) {
		    	if (db.isOpen()) {
		    		try{
		    			synchronized (this) {
		    				cursorDisaster = db.query(DbOpenHelper.TABLE_DISASTER, null, where, null, null, null,
		    						DbOpenHelper.C_ADDED_AT + order);		    			
		    				return cursorDisaster;
		    			}
		    		} 
		    		catch (Exception ex) { return null; }
		    	}		    	
		   } 
		   return null;
		  	  
	  }
	
	synchronized void updateTables(int isFavorite, int action, Status status,
			long userId, boolean isInTimeline){
		 ContentValues values = new ContentValues();
		 

		 values.put(DbOpenHelper.C_IS_FAVORITE,isFavorite );
		 db.update(DbOpenHelper.TABLE, values, DbOpenHelper.C_ID + "=" + status.id.longValue() , null);
		 
		 if (action == Timeline.R_FAVORITE_ID) 
			 db.delete(DbOpenHelper.TABLE_FAVORITES, DbOpenHelper.C_ID + "=" + status.id.longValue() , null);
		 else {
			 values = DbOpenHelper.statusToContentValues(status,userId);
			 long row = db.insert(DbOpenHelper.TABLE_FAVORITES, null, values);			 
		 }
		 
	}
	
    void savePairedPeer(String address, long tweetsNumber) {		  			 
		  if (address != null) {			 
			  ContentValues values = new ContentValues();
			  values.put(DbOpenHelper.C_MAC, address );
			  values.put(DbOpenHelper.C_MET_AT, new Date().getTime() );
			  if (tweetsNumber != -1) {
				  values.put(DbOpenHelper.C_TWEETS_NUMBER, tweetsNumber );
				  
			  }
		  	  try {
		  		 if (db != null)
		  			 db.insertOrThrow(DbOpenHelper.TABLE_ADDRESSES, null, values);  
		    	
		  	  } catch (SQLException ex) {
		  		 if (db != null)
		  			 db.update(DbOpenHelper.TABLE_ADDRESSES, values,
		  					 DbOpenHelper.C_MAC + "='" + address + "'", null);
		    	}
		  }
		  
	  }
    
    void updateDisasterTable(long id, long oldId, int hasBeenSent, int isValid) {
    	ContentValues values = new ContentValues();		  
  	    values.put(DbOpenHelper.C_HASBEENSENT, hasBeenSent ); 
  	    values.put(DbOpenHelper.C_IS_VALID, isValid);
  	    if (id != oldId)
  	    	values.put(DbOpenHelper.C_ID, id);
    	db.update(DbOpenHelper.TABLE_DISASTER, values, DbOpenHelper.C_ID + "=" + oldId, null);
    }
    
   boolean saveIntoDisasterDb(long id,long date,long added_at, String status, long userId,
  		  String sentBy, int isFromServer, int hasBeenSent, int isValid, int hopCount, byte[] signature) {
  	
  	    ContentValues values = new ContentValues();		  
  	    values.put(DbOpenHelper.C_ID, id ); 	
  	    values.put(DbOpenHelper.C_CREATED_AT, date);	
  	    values.put(DbOpenHelper.C_ADDED_AT, added_at);
  	    values.put(DbOpenHelper.C_TEXT, status);
  	    values.put(DbOpenHelper.C_USER_ID, userId);	 
  	    values.put(DbOpenHelper.C_ISFROMSERVER, isFromServer );  	   
  	    values.put(DbOpenHelper.C_HASBEENSENT, hasBeenSent );	  	   
  	    values.put(DbOpenHelper.C_SENT_BY, sentBy );	
  	    values.put(DbOpenHelper.C_IS_VALID, isValid );
  	    values.put(DbOpenHelper.C_HOPCOUNT, hopCount );
  	    values.put(DbOpenHelper.C_SIGNATURE, signature );
  	    Log.i(TAG, "trying to insert into the Db");	 
  	    try {
  	    	synchronized (this) {
  	    		db.insertOrThrow(DbOpenHelper.TABLE_DISASTER, null, values);
  	    		Log.i(TAG, "saved into the Db");
  	    		return true;
  	    	}
  	    } 
  	    catch (SQLException ex) {   	    	
  	    	return false;
  	    }
    }
    
    synchronized boolean copyIntoTimelineTable(long id,long created, String status, long userId, int isFromServer) {
	    ContentValues values = new ContentValues();
	    
	    values.put(DbOpenHelper.C_ID, id);
	    values.put(DbOpenHelper.C_CREATED_AT, created);
	    values.put(DbOpenHelper.C_TEXT, status);
	    values.put(DbOpenHelper.C_USER_ID, userId);
	    values.put(DbOpenHelper.C_IS_DISASTER, Timeline.TRUE);
	    
	    try {
	    	db.insertOrThrow(DbOpenHelper.TABLE, null, values);	    	
	    	return true;
	    } catch (Exception ex) { 	    	
	    		return false;
	    	   	
	    	}
  }
    
    synchronized String userDbQuery(AdapterContextMenuInfo info, String table) {
    	String query;
    	
    	if (table.equals(DbOpenHelper.TABLE_DIRECT) || table.equals(DbOpenHelper.TABLE_FRIENDS) )
    		query = "SELECT user FROM " + table + " AS tim WHERE tim._id =" + info.id;
    	else
    		query = "SELECT f.user FROM " + table + " AS tim, FriendsTable AS f WHERE tim.userCode = f._id AND tim._id =" + info.id;
		cursorSelected = rawQuery(query);		
    	    	
        if (cursorSelected != null) {        	
            cursorSelected.moveToFirst();
            try {
            	String user = cursorSelected.getString(cursorSelected
          	       .getColumnIndexOrThrow(DbOpenHelper.C_USER));
            	return user;
            }
            catch (Exception ex) {
            	return ""; 
            }            	
        }        
        return "";    
        
    }
    
    synchronized Cursor contextMenuQuery(long id, String table) {
    	cursorSelected = null;
    	String query = "SELECT DISTINCT f.user,status,created_at,tim.userCode FROM " + table + " AS tim, FriendsTable AS f WHERE tim.userCode = f._id AND tim._id =" + id;
		cursorSelected = rawQuery(query);		 
		
		 return cursorSelected;
    }
    
    Cursor peersDbQuery(String deviceMac) {	
		 if (db != null) {
		    if (db.isOpen()) {
		    	try{
		    		cursorPeers = db.query(DbOpenHelper.TABLE_ADDRESSES,new String[] {DbOpenHelper.C_MAC,
		    				DbOpenHelper.C_MET_AT,DbOpenHelper.C_TWEETS_NUMBER},
		    				DbOpenHelper.C_MAC + "='" + deviceMac + "'", null, null, null, null, null); 		    		  
		    		return cursorPeers; 
		    			
		    		} catch (Exception ex) {  		    			
		    		    return null;
		    		 }		    		
		    	} 
		    }
		 return null;
 } 
    
    
     void delete(String where,String table){
    	 if (db != null) {
    		 synchronized(this) {
    			 if (db != null) {
    				 db.delete(table, where, null);
    					 
    			 }
    		 }	
    	 }
    }
  
    public void insertPicture(ContentValues dataToInsert) {
    	 try {
 				synchronized(this) {
 					db.insertOrThrow(DbOpenHelper.TABLE_PICTURES, null, dataToInsert);
 					Timeline.activity.sendBroadcast(new Intent(UpdaterService.ACTION_NEW_TWITTER_STATUS));
 				}
 			} catch (Exception ex) {}
     }
    
     synchronized boolean insertIntoTimelineTable(Status status) {
    	 
    	 // prepare tweets to enter into DB
     	ContentValues values = DbOpenHelper.statusToContentValues(status, null);  
     	
     	if (status.isFavorite())        		
           	values.put(DbOpenHelper.C_IS_FAVORITE,1);       	
        else 
           	values.put(DbOpenHelper.C_IS_FAVORITE,0);
       	
       	try {
       		db.insertOrThrow(DbOpenHelper.TABLE, null, values);        			   
			    
       	} 
       	catch (SQLException ex) {
       		//Since we get an exception, this was not a new tweet -> return false
       		return false;
       	}
       	
       	return true;
     }
      
      synchronized public boolean insertGeneric(String table, ContentValues values) {
    	  try {
			  if (db != null)
				  if (db.isOpen()) {
					  if (table.equals(DbOpenHelper.TABLE_MENTIONS)) {
						 long id = values.getAsLong(DbOpenHelper.C_ID);
					 	 Cursor cursor  = db.query(table, null, DbOpenHelper.C_ID + "=" + id , null, null, null, null);
					 	
					  }
					  db.insertOrThrow(table, null, values);					 
					  return true;
					  
				  }				  
			  }
			  catch (Exception ex){				  
				  if (table.equals(DbOpenHelper.TABLE_FRIENDS)) {
					  long id = values.getAsLong(DbOpenHelper.C_ID);					  
					  db.update(table, values, DbOpenHelper.C_ID + "=" + id , null);					  
					  
				  }
			  }
			  return false;
      }
      
      
      synchronized public Cursor queryGeneric(String table ,String where, String order, String limit) {
    	  if (db != null) {
			  if (db.isOpen()) {
				  return db.query(table, null, where, null, null, null,
			   		       order, limit); 
			  }	
			  else 
				  return null;
    	  }
    	  else
    		  return null;
    	 
      }
      
      synchronized public Cursor rawQuery(String query ) {
    	  if (db != null) {
			  if (db.isOpen()) {
				  return db.rawQuery(query , null); 
			  }	
			  else 
				  return null;
    	  }
    	  else
    		  return null;
    	 
      }
      
      /**
       * Set the Favorite flag in the timeline Table to 1
       * @param tweetId ID of Tweet
       * @return number of rows affected
       */
      synchronized int setFavorite(BigInteger tweetId){
      	ContentValues values = new ContentValues();
      	values.put(DbOpenHelper.C_IS_FAVORITE, 1);
      	
      	return db.update(DbOpenHelper.TABLE, values, DbOpenHelper.C_ID + "=" + tweetId.toString(), null);
      }
      
      /**
       * Set the Favorite flag in the timeline Table to 0
       * @param tweetId ID of Tweet
       * @return number of rows affected
       */
      synchronized int unsetFavorite(BigInteger tweetId){
      	ContentValues values = new ContentValues();
      	values.put(DbOpenHelper.C_IS_FAVORITE, 0);
      	
      	return db.update(DbOpenHelper.TABLE, values, DbOpenHelper.C_ID + "=" + tweetId.toString(), null);
      }
   
}
