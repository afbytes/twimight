package ch.ethz.twimight;



import java.util.Date;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;


public class MyTwitter extends Activity implements OnClickListener{

    static final String TAG = "MyTwitter";    
      
    SharedPreferences mSettings;
    Button buttonOAuth; 
    ConnectionHelper connHelper;
	private DbOpenHelper dbHelper;
	public String status;
	static MyTwitter activity;
	static PendingIntent restartIntent;	
	
	@Override
    public void onClick(View src) {
	switch (src.getId()) {		
		case R.id.buttonOAuth:
			//finish();
			startActivity(new Intent(this,OAUTH.class));	
			break;						
		}    
    }      
	
	/** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
    	
		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}

    	Log.i(TAG,"inside on create");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);          
        
		if (Timeline.isRunning) {
			finish();
			startActivity(new Intent(this, Timeline.class));
		}
		else {
			mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);          
	        // find views by id
	        buttonOAuth = (Button) findViewById(R.id.buttonOAuth);       
	        activity = this;
			restartIntent = PendingIntent.getActivity(this.getBaseContext(), 0, 
					new Intent(getIntent()), getIntent().getFlags());
			new Thread(new GenerateKeys()).start();
			ifTokensTryLogin(); 
	        // Add listeners       
	        buttonOAuth.setOnClickListener(this);      
		}
            
    }    
   
  
	 @Override
	protected void onRestart() {
		// TODO Auto-generated method stub
		super.onRestart();
		ifTokensTryLogin();
	}
	 

	 class Login extends AsyncTask<Long, Void, String> {
		 
			@Override
			protected String doInBackground(Long... id ) {
				if (connHelper.testInternetConnectivity()) {
					boolean result = connHelper.doLogin();
					Log.i(TAG,"" + result);
    				if (result) {    				
    					return "Login Successful";							   			    
    				} else 
    					return "Incorrect Login, showing old tweets"; 				
    			}
    			else 
    				return "No internet connectivity";	
    			
			}		

			// This is in the UI thread, so we can mess with the UI
			@Override
			protected void onPostExecute(String message) {				
				Toast.makeText(MyTwitter.this, message, Toast.LENGTH_SHORT).show();
				finish();
			}
	 }
	 
	 private void ifTokensTryLogin() {
		  	
   	  if(mSettings.contains(OAUTH.USER_TOKEN) && mSettings.contains(OAUTH.USER_SECRET)) {    			
   			ConnectivityManager connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
   			connHelper = new ConnectionHelper(mSettings,connec); 
   			if (UpdaterService.isRunning == false) {
   				startService(new Intent(this, UpdaterService.class));
   				Log.i(TAG,"Updater service started");
   			}
   			new Login().execute();
   			startActivity(new Intent(this, Timeline.class));
   			
				
			} 
   	  	//else {				
		//		Toast.makeText(this,"Press the button above to authorize the client" , Toast.LENGTH_SHORT).show();
		//	}
			  	
   }
	
	class GenerateKeys implements Runnable {

		@Override
		public void run() {
			long time = mSettings.getLong("generated_at", 0);
			if (time < (new Date().getTime() - (3600000 *24 * 2))) {
				RSACrypto crypto = new RSACrypto(mSettings);
				crypto.createKeys();
				SharedPreferences.Editor editor = mSettings.edit();
				editor.remove("PublicKeyPosted");
				editor.commit();
			}
			
		}
		
	}
	
	
}