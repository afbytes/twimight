package ch.ethz.twimight.net.twitter;

import java.util.Date;

import winterwell.jtwitter.Twitter;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.net.RSACrypto;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.TimelineActivity;
import ch.ethz.twimight.data.TweetDbActions;

public class DirectMsgTask extends AsyncTask<Void, Void, Boolean> {
	String message,username,senderUser;
	ProgressDialog postDialog;
	Context cont;
	ConnectionHelper connHelper;	
	TweetDbActions dbActions;
	Twitter.Message messageResult;
	int isDisaster, hasBeenSent = Constants.FALSE;
	SharedPreferences mSettings;
		
	public static final int SENT = 1;
	public static final int NOT_SENT = 2;
	private static final String TAG = "DirectMsgTask";

	public DirectMsgTask( String message, String username, Context cont, 
			ConnectionHelper connHelper, SharedPreferences mSettings, boolean isDisaster) {			
		this.message =  message;			
		this.username = username;
		this.cont = cont;
		this.connHelper = connHelper;
		this.mSettings = mSettings;
		if (isDisaster)
			this.isDisaster = Constants.TRUE;
		else
			this.isDisaster = Constants.FALSE;		
		this.senderUser = mSettings.getString("user", "");
		dbActions = new TweetDbActions();
		
	 }	

	@Override
	protected void onPreExecute() {
		postDialog = ProgressDialog.show(cont, "Sending message",
				"Please wait while your message is being sent", true, // indeterminate
																		// duration
				false); // not cancel-able
	}

	@Override
	protected Boolean doInBackground(Void... mess) {
		try {
			if (connHelper.testInternetConnectivity()) {
			messageResult = ConnectionHelper.twitter.sendMessage(username, message);
			hasBeenSent = Constants.TRUE;
			return true;
			}
		} catch (Exception ex) {			
		}
		return false;	
	}

	// This is in the UI thread, so we can mess with the UI
	@Override
	protected void onPostExecute(Boolean result) {
		long resultId;
		
		postDialog.dismiss();
		 // if it has been sent correctly
			Intent intent = new Intent("DirectMsg");											
			
			if (result) {
				Toast.makeText(cont, "Message has been sent",Toast.LENGTH_SHORT).show();
				intent.putExtra("result", SENT);
			}
			else { // if it hasnt been sent
				Toast.makeText(cont, "Message not sent",Toast.LENGTH_SHORT).show();
				intent.putExtra("result", NOT_SENT);
			}
			cont.sendBroadcast(intent);	
			
		if (isDisaster == Constants.TRUE ) {	//if we are in disaster mode	
			
		 	if (message.length() > 0  ) {  //and length is ok	
		 		
		 		RSACrypto crypto = new RSACrypto(mSettings); 			
		 				 		
		 		String concatenate = message.concat(" " + senderUser) ; 
		 		long time = new Date().getTime();
		 		
		 		if (hasBeenSent == Constants.TRUE)
		 			resultId = messageResult.getId().longValue(); //id from twitter servers
		 		else
		 			resultId = concatenate.hashCode(); //local id
		 		
		 		// get public key of recipient
		 		Cursor cursorKey = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS, 
		 				DbOpenHelper.C_USER + "='" + username + "'", null, null);
		 		if (cursorKey != null)
		 			if (cursorKey.getCount() == 1) {
		 				
		 				cursorKey.moveToFirst();
		 				//Log.i(TAG,"key found");
		 				String modulus = cursorKey.getString(cursorKey.getColumnIndex(DbOpenHelper.C_MODULUS));
		 				String exponent = cursorKey.getString(cursorKey.getColumnIndex(DbOpenHelper.C_EXPONENT));
		 				if (modulus != null && exponent != null) {	 				
		 					// encrypt 
				 			byte[] encrypted = crypto.encypt(message, modulus, exponent);
				 			ContentValues values = new ContentValues();
				 			values.put(DbOpenHelper.C_ID, resultId);
				 			values.put(DbOpenHelper.C_CREATED_AT, time);
				 			values.put(DbOpenHelper.C_USER_RECIPIENT, username);
				 			
				 			values.put(DbOpenHelper.C_USER, crypto.encypt(senderUser, modulus, exponent));
				 			values.put(DbOpenHelper.C_IS_DISASTER, isDisaster);
				 			values.put(DbOpenHelper.C_HASBEENSENT, hasBeenSent);
				 			values.put(DbOpenHelper.C_TEXT, encrypted);	 	
				 			
				 			dbActions.insertGeneric(DbOpenHelper.TABLE_DIRECT_OUTGOING, values);
				 			
				 			Toast.makeText(cont, "Message saved into disaster Db",Toast.LENGTH_SHORT).show();
		 				}
		 			}
		 			else
		 				Log.i(TAG,"no key has been found");
		 		
		 		hasBeenSent = Constants.FALSE;								   
		 	}		 	
		}
		

	}
}
