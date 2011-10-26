package ch.ethz.twimight;

import java.util.ArrayList;

import winterwell.jtwitter.Twitter.Status;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import ch.ethz.twimight.AsyncTasks.DirectMsgTask;
import ch.ethz.twimight.AsyncTasks.Retweet;

public class Mentions extends Activity {
	ListView listMentions;
	SharedPreferences mSettings,prefs ;	
	ConnectionHelper connHelper ;
	private static final String TAG = "Favorites";
	boolean isThereConn = false;
	ArrayList<Status> results = null;
	TimelineAdapter adapter;
	Cursor cursor;
	private SQLiteDatabase db;
	private DbOpenHelper dbHelper;
	AlertDialog.Builder alert;
	private EditText input, inputDirect;
	TweetDbActions dbActions = UpdaterService.dbActions;
	TweetContextActions contextActions;
	String destinationUsername;
	NotificationManager notificationManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.simplelist);
		setTitle("Mentions");
		listMentions = (ListView) findViewById(R.id.itemList);	 		
		input = new EditText(this);
		inputDirect = new EditText(this); 	
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		dbHelper = new DbOpenHelper(this);
		db = dbHelper.getWritableDatabase(); 
		//dbActions = new TweetDbActions();
		connHelper = new ConnectionHelper(mSettings,Timeline.connec);
		contextActions = new TweetContextActions(connHelper,prefs, mSettings);
		String query = "SELECT tim._id,user,created_at,status,isDisaster FROM MentionsTable AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";
 	    cursor = dbActions.rawQuery(query);
 	    
		if (cursor != null)
			if ( cursor.getCount() == 0) {
				finish();
				Toast.makeText(this, "No mentions to be shown", Toast.LENGTH_SHORT).show();     	
			}
		Cursor cursorPictures = db.query(DbOpenHelper.TABLE_PICTURES, null,null, null, null, null, null);
			    // Setup the adapter
		cursorPictures.moveToFirst();
		adapter = new TimelineAdapter(this, cursor, cursorPictures);
		listMentions.setAdapter(adapter);
		registerForContextMenu(listMentions);
	}

	@Override
	protected void onDestroy() {
		db.close();
		cursor.close();
		super.onDestroy();		
	}
	
	
	/**
	 * onResume mainly cancels the pending notification.
	 */
	 @Override
	protected void onResume() {
		super.onResume();
		 notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		 notificationManager.cancel(Constants.MENTION_NOTIFICATION_ID);
	}

	@Override
	  public void onCreateContextMenu(ContextMenu menu, View v,
	          ContextMenuInfo menuInfo) {
	      super.onCreateContextMenu(menu, v, menuInfo);
	      menu.add(0, Timeline.REPLY_ID, 0, "Reply");
	      menu.add(0, Timeline.RETWEET_ID, 1, "Retweet");	
	      menu.add(0,Timeline.DIRECT_ID,2, "Direct Message");
	  }  
	 
	 @Override
	  public boolean onContextItemSelected(MenuItem item) {
		 AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

	      switch(item.getItemId()) {
	          case Timeline.REPLY_ID:                                        
	              try {
	                String user = dbActions.userDbQuery(info, DbOpenHelper.TABLE_MENTIONS);              
	                input.setText("@" + user);
	                showDialog(0); 
	                
	              } catch (Exception ex) {}
	             
	              return true;
	          case Timeline.RETWEET_ID: 
	        	  if (connHelper.testInternetConnectivity() || 
	        			  prefs.getBoolean("prefDisasterMode", false) == true ) 
	        		  new Retweet(contextActions, DbOpenHelper.TABLE_MENTIONS, this, true).execute(info.id);     
	        	  
	        		  
	        	  else 
	        		  Toast.makeText(this, "No internet connectivity", Toast.LENGTH_LONG).show(); 
	         	  return true;
	          case Timeline.DIRECT_ID:
	        	  if (connHelper.testInternetConnectivity() || prefs.getBoolean("prefDisasterMode", false) == true) {
	        	    destinationUsername = dbActions.userDbQuery(info, DbOpenHelper.TABLE);
	        	    
	        	  	showDialog(2);
	        	  } 
	        	  else
	        		Toast.makeText(this, "No internet connectivity", Toast.LENGTH_SHORT).show();   
	        	  return true;
	              	  
	      }
	      return super.onContextItemSelected(item);
	  }  
		 
	 @Override
	 protected Dialog onCreateDialog(int id) {	
	 	
	 	alert = new AlertDialog.Builder(this);
	 	
	 	if (id == 0) {
	 		alert.setTitle("Send a Tweet");	   	
	  	 	// Set an EditText view to get user input     	
	  	 	alert.setView(input);  	
	  	 	alert.setPositiveButton("Send", new OnClickListener() {
	   			public void onClick(DialogInterface dialog, int whichButton) {
	   			  String message = input.getText().toString();	  	
	   			  Timeline.activity.sendMessage(message);
	   			  dialog.dismiss();	  		  		
	   			}
	  	 		});

	  	 	alert.setNegativeButton("Cancel", new OnClickListener() {
	  	 		public void onClick(DialogInterface dialog, int whichButton) {
	  	 			input.setText(""); 
	   				 dialog.dismiss();	  		    
	  	 		}
	  	 	}); 
	 	}
	 // Dialog to send a direct message
		else {
			alert.setTitle("Insert message");  	
		  	// Set an EditText view to get user input     	
			alert.setView(inputDirect); 	
		  	alert.setPositiveButton("Send", new OnClickListener() {
		  		public void onClick(DialogInterface dialog, int whichButton) {
		  		 String message = inputDirect.getText().toString();
		  		 String user = mSettings.getString("user", "not found");
		  		 new DirectMsgTask(message,destinationUsername,Mentions.this, connHelper,
		  				 mSettings,prefs.getBoolean("prefDisasterMode", false)).execute();	  		
		  		    		  		
		  		  }
		  		});

		  	alert.setNegativeButton("Cancel", new OnClickListener() {
		  		  public void onClick(DialogInterface dialog, int whichButton) {
		  			 inputDirect.setText(""); 
		  			 dialog.dismiss();	  		    
		  		  }
		  		}); 
		}
	   	
	   	return alert.create();	
	 }
		
	
	 
	
	
}
