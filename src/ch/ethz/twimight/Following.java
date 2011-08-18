package ch.ethz.twimight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;


public class Following extends Activity{
	ListView listFriends;
	private static final String TAG = "Following";
	String[] stringArray;
	SharedPreferences mSettings ;
	ConnectivityManager connec ;
	ConnectionHelper connHelper;
	TweetDbActions dbActions = UpdaterService.dbActions;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.simplelist);
		listFriends = (ListView) findViewById(R.id.itemList);
		setTitle("Following");
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE); 
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		listFriends.setOnItemClickListener(new ClickListener());  	
		
		Cursor cursor = dbActions.queryGeneric(DbOpenHelper.TABLE_FRIENDS,
				DbOpenHelper.C_IS_DISASTER_FRIEND + "=" + Timeline.FALSE + " AND "
				+ DbOpenHelper.C_IS_FOLLOWED_BY_ME + "=" + Timeline.TRUE, DbOpenHelper.C_USER + " ASC" ,"1000");
		Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
		cursorPictures.moveToFirst();
		    // Setup the adapter
		FriendsAdapter adapter = new FriendsAdapter(this, cursor, cursorPictures);		
		listFriends.setAdapter(adapter); 
	}	
	private class ClickListener implements OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			if (connHelper.testInternetConnectivity() && ConnectionHelper.twitter != null ) {				
				LinearLayout row = (LinearLayout) arg1;	
				TextView user = (TextView)row.findViewById(R.id.friendsText);
				Intent intent = new Intent(Following.this, UserInfo.class);
				intent.putExtra("username", user.getText().toString());
				intent.putExtra("friends", stringArray);
				startActivity(intent);
			}
			else
				Toast.makeText(Following.this, "No internet Connectivity", Toast.LENGTH_LONG).show(); 			
		}
		
	}
	

	

}
