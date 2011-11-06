package ch.ethz.twimight;

import java.util.ArrayList;

import winterwell.jtwitter.Twitter;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;
import ch.ethz.twimight.net.twitter.FetchProfilePic;
import ch.ethz.twimight.net.twitter.OAUTH;
import ch.ethz.twimight.ui.Retweet;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.MySuggestionProvider;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.TimelineAdapter;
import ch.ethz.twimight.data.TweetDbActions;
import ch.ethz.twimight.net.twitter.ConnectionHelper;

/**
 * Shows search results.
 * @author pcarta
 * @author thossmann
 */
public class SearchActivity extends Activity {
	ListView listSearch ;
	SharedPreferences mSettings,prefs ;
	ConnectivityManager connec ;
	ConnectionHelper connHelper ;
	private static final String TAG = "Search";
	ArrayList<Twitter.Status> results;
	String query;
	AlertDialog.Builder alert;
	EditText input;
	TweetContextActions contextActions;
	TweetDbActions dbActions = UpdaterService.getDbActions();
	TimelineAdapter adapter;
	Bitmap theImage ;
	boolean isDisaster, disasterResults, normalResults;
	String tweetUser;

	/**
	 * Set everything up.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.i(TAG,"inside search");
		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.simplelist);
		setTitle("Search Results");
		prefs = PreferenceManager.getDefaultSharedPreferences(this);	
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE); 
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		input = new EditText(this); 
		contextActions = new TweetContextActions(connHelper,prefs, mSettings);

		registerReceiver(twitterStatusReceiver, new IntentFilter(UpdaterService.ACTION_NEW_TWITTER_STATUS));

		Intent intent = getIntent();		 
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			Log.i(TAG,"equals");
			query = intent.getStringExtra(SearchManager.QUERY);
			SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
					MySuggestionProvider.AUTHORITY, MySuggestionProvider.MODE);
			suggestions.saveRecentQuery(query, null);
			doMySearch(query);
		} 	

	}


	/**
	 * Populate context menu.
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {		  
		super.onCreateContextMenu(menu, v, menuInfo);  
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;

		TextView userTextView = (TextView)info.targetView.findViewById(R.id.textUser);	            	        
		tweetUser = userTextView.getText().toString() ;

		menu.add(0, TimelineActivity.REPLY_ID, 0, "Reply");
		menu.add(0, TimelineActivity.RETWEET_ID, 1, "Retweet");
		menu.add(0, TimelineActivity.FAVORITE_ID, 2, "Favorite");	
		menu.add(0, TimelineActivity.PROFILEINFO_ID, 3 , "About @" + tweetUser); 

	}

	/**
	 * Which context menu item did the user select?
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		switch(item.getItemId()) {
		case TimelineActivity.REPLY_ID:                                        
			try {
				TextView userTextView = (TextView)info.targetView.findViewById(R.id.textUser);	            	        
				input.setText("@" + userTextView.getText().toString() );
				showDialog(0); 

			} catch (Exception ex) {}

			return true;
		case TimelineActivity.RETWEET_ID: 
			if (connHelper.testInternetConnectivity() || 
					prefs.getBoolean("prefDisasterMode", false) == true ) 
				new Retweet(contextActions, DbOpenHelper.TABLE_SEARCH, this, true).execute(info.id);	        		  
			else 
				Toast.makeText(this, "No internet connectivity", Toast.LENGTH_LONG).show(); 

			return true;     
		case TimelineActivity.FAVORITE_ID:
			new SetFavorite().execute(info.id);
			return true;
		case TimelineActivity.PROFILEINFO_ID:
			if (ConnectionHelper.twitter != null && connHelper.testInternetConnectivity()) {

				Intent intent = new Intent(this,UserInfoActivity.class);
				intent.putExtra("username", tweetUser);
				startActivity(intent); 
			}	        		  
			else 
				Toast.makeText(this, "No internet connectivity", Toast.LENGTH_SHORT).show(); 
			return true;

		}
		return super.onContextItemSelected(item);
	}  

	/**
	 * Dialog
	 */
	@Override
	protected Dialog onCreateDialog(int id) {
		//dismissDialog(0);
		alert = new AlertDialog.Builder(SearchActivity.this);


		if (id == 0) {
			alert.setView(input); 
			alert.setTitle("Send a Tweet");  	
			// Set an EditText view to get user input     	

			alert.setPositiveButton("Send", new OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					String message = input.getText().toString();	  	
					TimelineActivity.getActivity().sendMessage(message);
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
		return alert.create();
	}

	/**
	 * on Stop.
	 */
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
		finish();
	}


	/**
	 * Clean up.
	 */
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		unregisterReceiver(twitterStatusReceiver);
	}


	/**
	 * Search in the disaster DB.
	 */
	private void searchDisasterTweets() {		  	 
		String sql = "SELECT tim._id,user,created_at,status FROM DisasterTable AS tim, FriendsTable AS f WHERE tim.userCode = f._id ORDER BY tim.created_at DESC";
		Cursor cursor = dbActions.rawQuery(sql);	 
		if (cursor != null) {				 
			if (cursor.getCount() > 0) {
				cursor.moveToFirst();
				for (int i=0; i < cursor.getCount(); i++) {
					try {
						String text = cursor.getString(cursor.getColumnIndexOrThrow(DbOpenHelper.C_TEXT));
						String username = cursor.getString(cursor.getColumnIndexOrThrow(DbOpenHelper.C_USER));
						if (text.contains(query) || username.contains(query) ) {

							disasterResults = true;								
							long id  = cursor.getLong(cursor.getColumnIndexOrThrow(DbOpenHelper.C_ID));
							long time = cursor.getLong(cursor.getColumnIndexOrThrow(DbOpenHelper.C_CREATED_AT));

							ContentValues ret = new ContentValues();
							ret.put(DbOpenHelper.C_ID, id); 
							ret.put(DbOpenHelper.C_CREATED_AT, time);
							ret.put(DbOpenHelper.C_TEXT, text);
							ret.put(DbOpenHelper.C_USER, username);
							ret.put(DbOpenHelper.C_IS_DISASTER, Constants.TRUE);
							dbActions.insertGeneric(DbOpenHelper.TABLE_SEARCH, ret);
						}							 
						cursor.moveToNext();
					}
					catch (IllegalArgumentException ex) {
						Log.e(TAG,"error",ex);
						break;	
					}
				}

			}
		}	 
	}

	/**
	 * Creates thread to perform the search.
	 * @param query Search query
	 */
	private void doMySearch(String query) {	
		isDisaster = prefs.getBoolean("prefDisasterMode", false);
		if (connHelper.testInternetConnectivity() || isDisaster ) {			
			try {
				Log.i(TAG,"performing search");
				new PerformSearch().execute();

			} catch (Exception e) {     }					

		}	else  {
			Toast.makeText(this, "No Internet connection", Toast.LENGTH_LONG).show(); 	
			finish();
		}		
	}

	/**
	 * Populate search table
	 */
	private void insertIntoDb() {

		for (Twitter.Status status : results) {
			ContentValues ret = new ContentValues();
			ret.put(DbOpenHelper.C_ID, status.id.longValue()); 
			ret.put(DbOpenHelper.C_CREATED_AT, status.getCreatedAt().getTime());
			ret.put(DbOpenHelper.C_TEXT, status.getText());
			ret.put(DbOpenHelper.C_USER, status.getUser().getScreenName());	          
			dbActions.insertGeneric(DbOpenHelper.TABLE_SEARCH, ret); 			          
		}		
	}

	/**
	 * Thread to perform search.
	 * @author pcarta
	 *
	 */
	class PerformSearch extends AsyncTask<Void, Void, Boolean> {		
		ProgressDialog postDialog; 

		/**
		 * Prepare.
		 */
		@Override
		protected void onPreExecute() {
			postDialog = ProgressDialog.show(SearchActivity.this, 
					"Searching", "Please wait while searching", 
					true,	// indeterminate duration
					false); // not cancel-able
		}

		/**
		 * Query twitter search API.
		 */
		@Override
		protected Boolean doInBackground(Void...nil ) {
			if (connHelper.testInternetConnectivity() && ConnectionHelper.twitter != null) {
				try {
					ConnectionHelper.twitter.setMaxResults(20);
					results = (ArrayList<Twitter.Status>)ConnectionHelper.twitter.search(query);
					if (results.size() > 0) {						
						new Thread(new FetchProfilePic(results, dbActions, SearchActivity.this)).start();												
						dbActions.delete(null, DbOpenHelper.TABLE_SEARCH);
						insertIntoDb();
						//return true;
						normalResults = true;						
					}					
				} catch (Exception e) {			    	  			    	         
					Log.e(TAG,"error searching", e);     
				}	
			}

			if (isDisaster) {
				if (normalResults == false)
					dbActions.delete(null, DbOpenHelper.TABLE_SEARCH);
				searchDisasterTweets();
				Log.i(TAG,"disaster search performed");
			}
			//some code here for return				
			return true;			

		}

		/**
		 * Done. remove dialog and show results (or message if nothing found).
		 */
		@Override
		protected void onPostExecute(Boolean result) {	
			visualizeResults();		
			if ( !normalResults && !disasterResults) {					
				Toast.makeText(SearchActivity.this, "No results", Toast.LENGTH_LONG).show();	 
			}			
			postDialog.dismiss();				
		}
	}

	/**
	 * Show search results.
	 */
	private void visualizeResults() {
		Cursor cursor = dbActions.queryGeneric(DbOpenHelper.TABLE_SEARCH, null, DbOpenHelper.C_CREATED_AT + " DESC" ,"100");
		Cursor cursorPictures = dbActions.queryGeneric(DbOpenHelper.TABLE_PICTURES,null, null, null);		 
		cursorPictures.moveToFirst();
		// Setup the adapter
		adapter = new TimelineAdapter(SearchActivity.this, cursor, cursorPictures);	
		listSearch = (ListView) findViewById(R.id.itemList);
		registerForContextMenu(listSearch);
		listSearch.setAdapter(adapter);	
	}

	/**
	 * Listens to new tweets and re-visualizes 
	 */
	private final BroadcastReceiver twitterStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {  	        	   	 
			visualizeResults();  
		}
	};

	/**
	 * Set favorite in the search results.
	 * @author pcarta
	 *
	 */
	class SetFavorite extends AsyncTask<Long, Void, Boolean> {		
		ProgressDialog postDialog; 	 

		/**
		 * Prepare
		 */
		@Override
		protected void onPreExecute() {
			postDialog = ProgressDialog.show(SearchActivity.this, 
					"Setting favorite", "Please wait while setting as favorite tweet", 
					true,	// indeterminate duration
					false); // not cancel-able
		}

		/**
		 * Main thread function to set favorite.
		 */
		@Override
		protected Boolean doInBackground(Long... id ) {
			return contextActions.favorite(id[0],TimelineActivity.FAVORITE_ID, DbOpenHelper.TABLE_SEARCH);	       	   		 
		}		

		/**
		 * Show result and notify about success (or not).
		 */
		@Override
		protected void onPostExecute(Boolean result) {
			postDialog.dismiss();				
			if (result)
				Toast.makeText(SearchActivity.this, "Favorite set succesfully", Toast.LENGTH_SHORT).show();
			else 
				Toast.makeText(SearchActivity.this, "Favorite not set", Toast.LENGTH_SHORT).show();

		}
	}

}