package ch.ethz.twimight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class Followers extends Activity{
	ListView listFriends;
	private static final String TAG = "Followers";
	String[] stringArray;
	SharedPreferences mSettings ;
	ConnectivityManager connec ;
	ConnectionHelper connHelper;
	TweetDbActions dbActions = UpdaterService.dbActions;
	
	 static final int SEND_DIRECT_ID = Menu.FIRST+1;
	 static final int REPLY_ID = Menu.FIRST+2;
	
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
		listFriends = (ListView) findViewById(R.id.itemList);
		setTitle("Followers");
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE); 
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		listFriends.setOnItemClickListener(new ClickListener());  	
		
		Cursor cursor = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS,
				DbOpenHelper.C_IS_DISASTER_FRIEND + "=" + Timeline.FALSE + " AND "
				+ DbOpenHelper.C_IS_MY_FOLLOWER + "=" + Timeline.TRUE, DbOpenHelper.C_USER + " ASC" ,"1000");
		Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
		cursorPictures.moveToFirst();
		    // Setup the adapter
		FriendsAdapter adapter = new FriendsAdapter(this, cursor, cursorPictures);		
		listFriends.setAdapter(adapter); 
		registerForContextMenu(listFriends);
		
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {	
		super.onCreateContextMenu(menu, v, menuInfo);		
		
		menu.add(0, SEND_DIRECT_ID, 0, "Send Direct Message");
	}

	
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		
				
			case SEND_DIRECT_ID:
				Intent intent = new Intent(this,SendDirectMessage.class);
				String tweetUser = dbActions.userDbQuery(info,DbOpenHelper.TABLE_FRIENDS); 
				intent.putExtra("username", tweetUser);
				startActivity(intent);
				return true;
		} 
		 
		return super.onContextItemSelected(item);
	}
	
	
	private class ClickListener implements OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			if (connHelper.testInternetConnectivity() && ConnectionHelper.twitter != null ) {				
				LinearLayout row = (LinearLayout) arg1;	
				TextView user = (TextView)row.findViewById(R.id.friendsText);
				Intent intent = new Intent(Followers.this, UserInfo.class);
				intent.putExtra("username", user.getText().toString());
				intent.putExtra("friends", stringArray);
				startActivity(intent);
			}
			else
				Toast.makeText(Followers.this, "No internet Connectivity", Toast.LENGTH_LONG).show(); 			
		}
		
	}
	

	

}