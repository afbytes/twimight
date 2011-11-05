package ch.ethz.twimight;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.util.ByteArrayBuffer;

import winterwell.jtwitter.Twitter.User;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import ch.ethz.twimight.net.twitter.ConnectionHelper;
import ch.ethz.twimight.net.twitter.DirectMsgTask;
import ch.ethz.twimight.net.twitter.OAUTH;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.TweetDbActions;

public class UserInfo extends Activity {	
	
	private static final int UNFOLLOW_ID = Menu.FIRST;
	private static final int FOLLOW_ID = Menu.FIRST +1;
	private static final int SEND_ID = Menu.FIRST +2;
	private static final int DIRECT_ID = Menu.FIRST +3;
	String username;
	ArrayList<String> screenNamesList;
	ArrayList<User> userList;
	Bitmap theImage ;
	boolean isFollowing;	
	Intent intent;
	AlertDialog.Builder alert;
	private EditText input, inputDirect;
	TweetDbActions dbActions;
	SharedPreferences mSettings,prefs;
	ConnectivityManager connec;
	ConnectionHelper connHelper;
	
	private static final String TAG = "UserInfo";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.userprofile);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);	   
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);
		setTitle("User Information");
		input = new EditText(this);
		inputDirect = new EditText(this);
		intent = getIntent();
		username = intent.getStringExtra("username");		
		screenNamesList = new ArrayList<String>();
		screenNamesList.add(username);
		dbActions = new TweetDbActions();
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		registerReceiver(directMsgSentReceiver,new IntentFilter("DirectMsgSent"));
		new RetrieveProfile().execute();
			
	}
	
	
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		unregisterReceiver(directMsgSentReceiver);
	}



	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// TODO Auto-generated method stub
		if (!username.equals(mSettings.getString("user", ""))) {
			if (isFollowing)
				menu.add(0, UNFOLLOW_ID, 1, "Unfollow").setIcon(R.drawable.ic_menu_delete);
			else
				menu.add(0, FOLLOW_ID, 2, "Follow").setIcon(R.drawable.ic_menu_cc);
		}
		
		menu.add(0, SEND_ID, 2, "Reply").setIcon(android.R.drawable.ic_menu_revert)	;
		menu.add(0, DIRECT_ID, 2, "Direct Message").setIcon(R.drawable.ic_menu_send);
		return super.onCreateOptionsMenu(menu);
	}
	
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		
		switch (item.getItemId()) {
		
		case UNFOLLOW_ID:
			 if (ConnectionHelper.twitter != null)
				 ConnectionHelper.twitter.stopFollowing(username);
			 return true;
			
		case FOLLOW_ID:
			 if (ConnectionHelper.twitter != null)
				 ConnectionHelper.twitter.follow(username);
			return true;
		case SEND_ID:			      
            input.setText("@" + username);
			showDialog(0);
			return true;
		case DIRECT_ID:			
			showDialog(1);
            return true;
		}
		return false;
		
	}

	class RetrieveProfile extends AsyncTask<Void, Void, Boolean> {		
	    ProgressDialog postDialog;
	    byte[] imageByteArray = null;
	
		@Override
		protected void onPreExecute() {
			postDialog = ProgressDialog.show(UserInfo.this, 
					"Retrieving Profile", "Please wait while retrieving data", 
					true,	// indeterminate duration
					false); // not cancel-able
		}
		
		@Override
		protected Boolean doInBackground(Void...nil ) {
			if (ConnectionHelper.twitter != null) {
				try {
					userList = (ArrayList<User>) ConnectionHelper.twitter.bulkShow(screenNamesList);
					//new RetrieveFigure().execute();
					if (intent.getStringArrayExtra("friends") != null)
						 isFollowing = true;
					 else {
						 List<User> friendsList= ConnectionHelper.twitter.getFriends();
						 for(User friend : friendsList) {
							 if (friend.getScreenName().toString().equals(username))
								 isFollowing = true;
						 }
					 }
					
				} catch (Exception ex) {
					return false;
				}
				
				Cursor cPic = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,
						DbOpenHelper.C_USER + "='" + username + "'",null, null);
				if (cPic.getCount() > 0) {
					cPic.moveToFirst();
					imageByteArray = cPic.getBlob(cPic.getColumnIndex(DbOpenHelper.C_IMAGE));  
					if (imageByteArray != null) {    			
		    			ByteArrayInputStream imageStream = new ByteArrayInputStream(imageByteArray);    	
		        		theImage = BitmapFactory.decodeStream(imageStream);
		        		
		    		}
				}
				else
					new RetrieveFigure().execute();
				
				return true;				
			}
			return true;
		}		

		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(Boolean result) {
			postDialog.dismiss();
			if (result) {
				ImageView picture = (ImageView) findViewById(R.id.imageUser);
				picture.setImageBitmap(theImage);
        		imageByteArray = null;
				User user = userList.get(0);				
				
				TextView textView = (TextView) findViewById(R.id.tweetsTextView);
				textView.append(" " + user.getStatusesCount());				
				textView = (TextView) findViewById(R.id.descrTextView);
				String description = user.getDescription();
				String location = user.getLocation();
				if (description != null && location != null)
					textView.setText("" + description + "\n" + location);				
				textView = (TextView) findViewById(R.id.nameTextView);
				textView.setText("" + user.getName() + "\n" + user.getScreenName() );				
				textView = (TextView) findViewById(R.id.followersTextView);
				textView.append(" " + user.getFollowersCount());				
				textView = (TextView) findViewById(R.id.friendsTextView);
				textView.append(" " +user.getFriendsCount());				
				textView = (TextView) findViewById(R.id.listsTextView);
				textView.append(" " + user.listedCount);				
				textView = (TextView) findViewById(R.id.favouritesTextView);
				textView.append(" " + user.getFavoritesCount()); 			
			}	
			else {
				Toast.makeText(UserInfo.this	, "No user has been found", Toast.LENGTH_SHORT).show();
				finish();
			}
				
			
		}
	}
	
	class RetrieveFigure extends AsyncTask<Void, Void, Boolean> {    		
		
		@Override
		protected Boolean doInBackground(Void...nil ) {
			try {
			    User user = userList.get(0);
			    URL url = new URL(user.getProfileImageUrl().toURL().toString());
	  			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	  			connection.setDoInput(true);
	  			connection.connect();
	  			InputStream input = connection.getInputStream();			
	  			
	  			BufferedInputStream bis = new BufferedInputStream(input,128);			
	  			ByteArrayBuffer baf = new ByteArrayBuffer(128);			
	  			//get the bytes one by one			
	  			int current = 0;			
	  			while ((current = bis.read()) != -1) {			
	  			        baf.append((byte) current);			
	  			}
	  			ByteArrayInputStream imageStream = new ByteArrayInputStream(baf.toByteArray());    	
        	    theImage = BitmapFactory.decodeStream(imageStream);
        		
			} catch (Exception ex) {
				 Log.e(TAG,"Exception",ex);
				return false;
			}
	  			return true;
		}		

		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(Boolean result) {
			
			if (result) {
				 Log.i(TAG,"setting image");
				ImageView image = (ImageView) findViewById(R.id.imageUser);
        		image.setImageBitmap(theImage);		
			}	
			
		}
	}
	
		
	@Override
	protected Dialog onCreateDialog(int id) {
		//dismissDialog(0);
		alert = new AlertDialog.Builder(this);		
		
		if (id == 0) {
			alert.setView(input); 
			alert.setTitle("Send a Tweet");  	
		  	// Set an EditText view to get user input     	
		  	 	
		  	alert.setPositiveButton("Send", new OnClickListener() {
		  		public void onClick(DialogInterface dialog, int whichButton) {
		  		 String message = input.getText().toString();	  	
		  		 Timeline.getActivity().sendMessage(message);
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
		else {
			alert.setTitle("Insert Message");  	
		  	// Set an EditText view to get user input     	
			alert.setView(inputDirect); 	
		  	alert.setPositiveButton("Send", new OnClickListener() {
		  		public void onClick(DialogInterface dialog, int whichButton) {
		  		 String message = inputDirect.getText().toString();
		  		 if (connHelper.testInternetConnectivity()) {
		  			 String sender = mSettings.getString("user", "not found");
		  			 new DirectMsgTask(message, username, UserInfo.this, connHelper,
		  					mSettings,prefs.getBoolean("prefDisasterMode", false)).execute();
		  		 }
		  		 else
		  			Toast.makeText(UserInfo.this, "No Internet connectivity", Toast.LENGTH_SHORT).show();
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
	
	private final BroadcastReceiver directMsgSentReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) { 	  
	   	 inputDirect.setText("");	   	   
	    }
	};
	
	

}
