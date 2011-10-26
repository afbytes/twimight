package ch.ethz.twimight;

import java.util.Date;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class DirectMessages extends Activity {
	ListView listDirect;
	Cursor cursor, cursorPictures;
	TimelineAdapter adapter;
	TweetDbActions dbActions = UpdaterService.dbActions;
	SharedPreferences mSettings;
	ConnectivityManager connec;
	ConnectionHelper connHelper;	
	
	private static final String TAG = "DirectMessages";
	 static final int DELETE_ID = Menu.FIRST ;	
	 static final int SEND_DIRECT_ID = Menu.FIRST+2;
	 static final int DELETE_ALL_ID = Menu.FIRST+3;
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		
			case DELETE_ID:
				new DeleteMsg().execute(info.id);			
				return true;
				
			case SEND_DIRECT_ID:
				Intent intent = new Intent(this,SendDirectMessage.class);
				String tweetUser = dbActions.userDbQuery(info,DbOpenHelper.TABLE_DIRECT);
				Log.i("direct messages", "tweetUser = " + tweetUser);
				intent.putExtra("username", tweetUser);
				startActivity(intent);
				return true;
		} 
		 
		return super.onContextItemSelected(item);
	}

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

		listDirect = (ListView) findViewById(R.id.itemList);	
		registerForContextMenu(listDirect);		
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);	    
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		String where = DbOpenHelper.C_CREATED_AT + "<" + (new Date().getTime() - (3600000*24*2))  ;
		dbActions.delete(where, DbOpenHelper.TABLE_DIRECT);	
		visualize();
	}
	
	/**
	 * on Resume mainly cancels pending notifiactions
	 */
	@Override
	  public void onResume() {
	    super.onResume();
	    // Cancel notification
		 NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		 notificationManager.cancel(Constants.DIRECT_NOTIFICATION_ID);
	       
	  }
	
	@Override
	  public boolean onCreateOptionsMenu(Menu menu){
		  super.onCreateOptionsMenu(menu);
		  menu.add(0, SEND_DIRECT_ID, 0, "Send").setIcon(R.drawable.ic_menu_send);
		  menu.add(0, DELETE_ALL_ID, 0, "Delete All").setIcon(R.drawable.ic_menu_delete);
		  return true;
		  
	}
	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {		
		switch(item.getItemId()){ 
			case SEND_DIRECT_ID:
				Intent intent = new Intent(this,SendDirectMessage.class);				
				startActivity(intent);
				return true;
			case DELETE_ALL_ID:
				cursor = dbActions.queryGeneric(DbOpenHelper.TABLE_DIRECT,null, DbOpenHelper.C_CREATED_AT + " DESC" ,"20");
				cursor.moveToFirst();
				for (int i = 0; i < cursor.getCount(); i++) {
					long id = cursor.getLong(cursor.getColumnIndex(DbOpenHelper.C_ID));
					new DeleteMsg().execute(id);
					cursor.moveToNext();					
				}
				return true;
		}
		return false;
	}
	
	private void visualize() {
		// Get the data from the DB
		cursor = dbActions.queryGeneric(DbOpenHelper.TABLE_DIRECT,null, DbOpenHelper.C_CREATED_AT + " DESC" ,"20");
		if (cursor.getCount() > 0) {
			cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
			cursorPictures.moveToFirst();
			    // Setup the adapter
			adapter = new TimelineAdapter(this, cursor, cursorPictures);
			listDirect.setAdapter(adapter); 
		}
		else {
			 Toast.makeText(this, "No Direct Messages", Toast.LENGTH_SHORT).show();
			 //finish();
		}		
	}

	class DeleteMsg extends AsyncTask<Long, Void, Boolean> {		
		 ProgressDialog postDialog; 
		
			@Override
			protected void onPreExecute() {
				postDialog = ProgressDialog.show(DirectMessages.this, 
						"Deleting message", "Please wait while deleting tweet", 
						true,	// indeterminate duration
						false); // not cancel-able
			}
			
			@Override
			protected Boolean doInBackground(Long... id ) {
				if (connHelper.testInternetConnectivity() && ConnectionHelper.twitter != null) {
					try {
						ConnectionHelper.twitter.destroyMessage(id[0]);	
						dbActions.delete(DbOpenHelper.C_ID + "=" + id[0], DbOpenHelper.TABLE_DIRECT);
					} catch (Exception ex) {
						Log.e(TAG,"error", ex);					
					}
					
					return true;
				}
				else {
					Toast.makeText(DirectMessages.this, "No internet connectivity", Toast.LENGTH_SHORT).show();
					return false;
				}		
				
			}		

			// This is in the UI thread, so we can mess with the UI
			@Override
			protected void onPostExecute(Boolean result) {
				postDialog.dismiss();
				if (result)
					visualize();
				else
					Toast.makeText(DirectMessages.this, "No internet connectivity", Toast.LENGTH_LONG).show();  
				
			}
		}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {	
		super.onCreateContextMenu(menu, v, menuInfo);		
		menu.add(0, DELETE_ID, 0, "Delete Message");
		menu.add(0, SEND_DIRECT_ID, 0, "Reply");
	}

	

	
	

}
