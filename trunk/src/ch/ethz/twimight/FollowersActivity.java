package ch.ethz.twimight;

import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.FriendsAdapter;
import ch.ethz.twimight.data.TweetDbActions;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.net.twitter.OAUTH;
import ch.ethz.twimight.util.Constants;
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

/**
 * Shows the followers.
 * @author pcarta
 * @author thossmann
 */
public class FollowersActivity extends Activity{
	ListView listFriends;
	String[] stringArray;
	SharedPreferences mSettings ;
	ConnectivityManager connec ;
	ConnectionHelper connHelper;
	TweetDbActions dbActions = UpdaterService.getDbActions();

	static final int SEND_DIRECT_ID = Menu.FIRST+1;
	static final int REPLY_ID = Menu.FIRST+2;

	/**
	 * Set everything up.
	 */
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
				DbOpenHelper.C_IS_DISASTER_FRIEND + "=" + Constants.FALSE + " AND "
				+ DbOpenHelper.C_IS_MY_FOLLOWER + "=" + Constants.TRUE, DbOpenHelper.C_USER + " ASC" ,"1000");
		Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
		cursorPictures.moveToFirst();
		// Setup the adapter
		FriendsAdapter adapter = new FriendsAdapter(this, cursor, cursorPictures);		
		listFriends.setAdapter(adapter); 
		registerForContextMenu(listFriends);

	}

	/**
	 * Populates the Context Menu.
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {	
		super.onCreateContextMenu(menu, v, menuInfo);		

		menu.add(0, SEND_DIRECT_ID, 0, "Send Direct Message");
	}


	/**
	 * What did the user select from the context menu?
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {


		case SEND_DIRECT_ID:
			Intent intent = new Intent(this,SendDirectMessageActivity.class);
			String tweetUser = dbActions.userDbQuery(info,DbOpenHelper.TABLE_FRIENDS); 
			intent.putExtra("username", tweetUser);
			startActivity(intent);
			return true;
		} 

		return super.onContextItemSelected(item);
	}

	/**
	 * Start the UserViewActivity
	 * @author pcarta
	 *
	 */
	private class ClickListener implements OnItemClickListener {

		/**
		 * On Click
		 */
		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			if (connHelper.testInternetConnectivity() && ConnectionHelper.twitter != null ) {				
				LinearLayout row = (LinearLayout) arg1;	
				TextView user = (TextView)row.findViewById(R.id.friendsText);
				Intent intent = new Intent(FollowersActivity.this, UserInfoActivity.class);
				intent.putExtra("username", user.getText().toString());
				intent.putExtra("friends", stringArray);
				startActivity(intent);
			}
			else
				Toast.makeText(FollowersActivity.this, "No internet Connectivity", Toast.LENGTH_LONG).show(); 			
		}

	}




}