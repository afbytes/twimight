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
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Shows whom the user is following.
 * @author pcarta
 * @author thossmann
 */
public class FollowingActivity extends Activity{
	ListView listFriends;
	String[] stringArray;
	SharedPreferences mSettings ;
	ConnectivityManager connec ;
	ConnectionHelper connHelper;
	TweetDbActions dbActions = UpdaterService.getDbActions();

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
		setTitle("Following");
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE); 
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		listFriends.setOnItemClickListener(new ClickListener());  	

		Cursor cursor = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS,
				DbOpenHelper.C_IS_DISASTER_FRIEND + "=" + Constants.FALSE + " AND "
				+ DbOpenHelper.C_IS_FOLLOWED_BY_ME + "=" + Constants.TRUE, DbOpenHelper.C_USER + " ASC" ,"1000");
		Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
		cursorPictures.moveToFirst();
		// Setup the adapter
		FriendsAdapter adapter = new FriendsAdapter(this, cursor, cursorPictures);		
		listFriends.setAdapter(adapter); 
	}	

	/**
	 * Show user profile of a following user.
	 * @author pcarta
	 *
	 */
	private class ClickListener implements OnItemClickListener {

		/**
		 * on choosing a user to view the profile.
		 */
		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			if (connHelper.testInternetConnectivity() && ConnectionHelper.twitter != null ) {				
				LinearLayout row = (LinearLayout) arg1;	
				TextView user = (TextView)row.findViewById(R.id.friendsText);
				Intent intent = new Intent(FollowingActivity.this, UserInfoActivity.class);
				intent.putExtra("username", user.getText().toString());
				intent.putExtra("friends", stringArray);
				startActivity(intent);
			}
			else
				Toast.makeText(FollowingActivity.this, "No internet Connectivity", Toast.LENGTH_LONG).show(); 			
		}

	}




}
