package ch.ethz.twimight;

import java.util.Date;

import winterwell.jtwitter.Twitter.Status;
import winterwell.jtwitter.Twitter.User;
import android.bluetooth.BluetoothAdapter;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.TweetDbActions;
import ch.ethz.twimight.net.RSACrypto;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.net.opportunistic.packets.SignedTweet;

public class TweetContextActions {
	private Cursor cursorSelected;
	ConnectionHelper connHelper;	
	  SharedPreferences prefs, mSettings;
	  TweetDbActions dbActions = UpdaterService.getDbActions();
	  BluetoothAdapter mBtAdapter;
	
	 private static final int FALSE = 0;
	 private static final int TRUE = 1; 
	  private String username = "";
	  private String text = "";	
	  long userId;
	  private static final String TAG = "ContextActions";
	
	 	  
	public TweetContextActions(ConnectionHelper connHelper,SharedPreferences prefs,
			SharedPreferences mSettings) {	
		this.connHelper = connHelper;
		this.prefs = prefs;
		this.mSettings = mSettings;		
		mBtAdapter = BluetoothAdapter.getDefaultAdapter();
	}
	
	boolean deleteTweet(long id, boolean isDisaster) {
		try {
			if (ConnectionHelper.twitter != null && connHelper.testInternetConnectivity()) {
				ConnectionHelper.twitter.destroyStatus(id);
				dbActions.delete(DbOpenHelper.C_ID + "=" + id,DbOpenHelper.TABLE);
				if (isDisaster)					
					dbActions.updateDisasterTable(id,id, Timeline.TRUE, Timeline.FALSE);				
				return true;
			}
			else {
				if (isDisaster) {
					dbActions.delete(DbOpenHelper.C_ID + "=" + id,DbOpenHelper.TABLE);
					dbActions.updateDisasterTable(id,id, Timeline.TRUE, Timeline.FALSE);				
				}
				return false;
			} 
				
		} catch (Exception ex) {
			if (isDisaster) {
				dbActions.delete(DbOpenHelper.C_ID + "=" + id,DbOpenHelper.TABLE);
				dbActions.updateDisasterTable(id,id, Timeline.TRUE, Timeline.FALSE);				
			}
			return false;
			}
	}
	
	 @SuppressWarnings("deprecation")
	boolean favorite(long id, int action, String table){
		boolean isInTimeline= false  ;
		Log.i(TAG,"id = " + id);
		if (connHelper.testInternetConnectivity()) {
			Log.i(TAG,"there is connect.");
			if (table.equals(DbOpenHelper.TABLE))
				isInTimeline = true;
			extractFromCursor(id , table); 	       
	        Date date = new Date();
	        User user = new User(username);	        
	        Status status = new Status(user,text,id,date);	        
				try {
					//if i am in the timeline view
			        if (action == Timeline.FAVORITE_ID) {
			        	Log.i(TAG,"setting as a favorite");
			        	ConnectionHelper.twitter.setFavorite(status, true);	
			        	if (table.equals(DbOpenHelper.TABLE))
			        		dbActions.updateTables(TRUE,action,status,userId,true);
			        	else {			        		
			        		dbActions.updateTables(TRUE,action,status,userId,false);
			        	}
			        	return true;		        	
			        }
			        //if I am in the favorite view I can just remove from favorites
			       else {
			        	ConnectionHelper.twitter.setFavorite(status, false); 
			        	dbActions.updateTables(FALSE,action,status,0,isInTimeline);
			        	return true;
			       }
			        
			        } catch (Exception ex) {
			        	Log.e(TAG,"error setting the favorite",ex);
			        	return false;
			        }        
			        
		  }  else
			  return false;
	}

	@SuppressWarnings("deprecation")
	public boolean retweet(long id, String table, boolean saveDisaster) {	
		boolean returnValue= false;
		int hasBeenSent = FALSE;  	
	   
		   extractFromCursor(id,table); 
		   Date date = new Date();
	       User user = new User(username);
	       Status status = new Status(user,text,id,date);
		try {       
	        ConnectionHelper.twitter.retweet(status);                  
	        hasBeenSent = TRUE;	        
	        returnValue = true;
	        } catch (Exception ex) {	        	
	        }
	        //if disaster mode is enabled...
	   if ( (prefs.getBoolean("prefDisasterMode", false) == true ) && saveDisaster) { 
		
	  	   long created  = cursorSelected.getLong(cursorSelected
	   	        .getColumnIndex(DbOpenHelper.C_CREATED_AT));
	  	 
	  	    SignedTweet tweet = new SignedTweet(id, created, text, username,userId, TRUE,
				hasBeenSent, 0, null, null);			 			
			RSACrypto crypto = new RSACrypto(mSettings);			
			byte[] signature = crypto.sign(tweet);
			
		   if (Long.toString(id).length() > 12)	 {
			  
			 //I must save the retweet with the centralized twitter id
			   dbActions.saveIntoDisasterDb(id,created,new Date().getTime(),text,userId,"",
					   TRUE,hasBeenSent, TRUE, 0, signature);		   
			   //here i dont need to copy into the timeline table since I am retweeting from there
			   hasBeenSent = FALSE;
		   }
		   else {
			   
			   String newText = "RT @" + username + " "+ text;
			   //I must save the retweet with the LOCAL twitter id
			   dbActions.saveIntoDisasterDb(newText.hashCode(),created,new Date().getTime(), newText, userId,"",
					   FALSE, FALSE, FALSE , 0, signature);		   
			   //here i dont need to copy into the timeline table since I am retweeting from there
			   hasBeenSent = FALSE;
		   }
	  	   
		   
		  // String mac = mBtAdapter.getAddress();
			//long time = new Date().getTime();
			
			/*
			 * try {
			 
				if (RandomTweetGenerator.generatorWriter != null)
					RandomTweetGenerator.generatorWriter.write(mac + ":" + username + ":" 
							+ id + ":manual:" + time
							+ ":" + new Date().toString() + "\n");
			} catch (IOException e) {	}
			*/		
	  }
	   return returnValue;	   	
	}


	private void extractFromCursor(long id, String table) {
			if (!table.equals(DbOpenHelper.TABLE_SEARCH))
		      cursorSelected = dbActions.contextMenuQuery(id,table);
			else {
				cursorSelected = dbActions.queryGeneric(DbOpenHelper.TABLE_SEARCH,
						DbOpenHelper.C_ID + "=" + id , null, null);
			}
		     
		     if (cursorSelected != null) {
		    	 cursorSelected.moveToFirst();
		    	 Log.i(TAG, "cursorSelected count = " + cursorSelected.getCount());
		     	try {
		    		 text = cursorSelected.getString(cursorSelected
				   	        .getColumnIndexOrThrow(DbOpenHelper.C_TEXT));
		    		 username = cursorSelected.getString(cursorSelected
				 	        .getColumnIndexOrThrow(DbOpenHelper.C_USER));
		    		 userId = cursorSelected.getLong(cursorSelected.getColumnIndex(DbOpenHelper.C_USER_ID));
		    		 
		    		 if (table.equals(DbOpenHelper.TABLE_SEARCH)) {
		    			 ContentValues values = new ContentValues();
		    		    	values.put(DbOpenHelper.C_USER, username );
		    				values.put(DbOpenHelper.C_ID, userId);
		    				values.put(DbOpenHelper.C_IS_DISASTER_FRIEND, Timeline.TRUE);		    							
		    				dbActions.insertGeneric(DbOpenHelper.TABLE_FRIENDS, values);				
		    		 }
		    			 
		    		
		   	  } catch (IllegalArgumentException ex) {		   		 
		   	  }
		       
		     }
		     
	}
	
}
