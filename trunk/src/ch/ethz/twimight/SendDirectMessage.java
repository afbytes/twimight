package ch.ethz.twimight;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import ch.ethz.twimight.AsyncTasks.DirectMsgTask;

public class SendDirectMessage extends Activity {
	EditText msgEditText;
	String username;
	SharedPreferences mSettings,prefs;
	ConnectivityManager connec;
	ConnectionHelper connHelper;
	EditText userEditText;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Are we in disaster mode?
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", false) == true) {
			setTheme(R.style.twimightDisasterTheme);
		} else {
			setTheme(R.style.twimightTheme);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.senddirectmsg);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);	   
		Button send = (Button) findViewById(R.id.buttonSendDirect);
		Button cancel = (Button) findViewById(R.id.buttonCancelDirect);
		userEditText = (EditText)  findViewById(R.id.userEditText);
		msgEditText = (EditText)  findViewById(R.id.msgEditText);
		Intent intent = getIntent();
		username =  intent.getStringExtra("username");
		if (username != null)
			userEditText.setText( username);
		send.setOnClickListener(sendClickListener);
		cancel.setOnClickListener(canceClickListener);
		mSettings = getSharedPreferences(OAUTH.PREFS, Context.MODE_PRIVATE);    
		connec =  (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		connHelper = new ConnectionHelper(mSettings,connec);
		registerReceiver(directMsgSentReceiver,new IntentFilter("DirectMsg"));
	}
	
	
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		unregisterReceiver(directMsgSentReceiver);
	}



	final OnClickListener sendClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			 String message = msgEditText.getText().toString();
			 if (username == null)
				 username = userEditText.getText().toString();
			 if (username.length() > 0) {
				// String sender = mSettings.getString("user", "not found");
				 new DirectMsgTask(message, username, SendDirectMessage.this, connHelper,
						 mSettings,prefs.getBoolean("prefDisasterMode", false)).execute();
			 }
			 else 
				 Toast.makeText(SendDirectMessage.this, "Insert Username", Toast.LENGTH_SHORT).show();   
			 
		}
		
	};
	
	final OnClickListener canceClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			finish();			
		}		
	};
	
	private final BroadcastReceiver directMsgSentReceiver = new BroadcastReceiver() {
	    @Override
	    public void onReceive(Context context, Intent intent) {   
	    	int result = intent.getIntExtra("result", DirectMsgTask.NOT_SENT );
	    	if (result == DirectMsgTask.SENT) {
	    		msgEditText.setText("");
	    		userEditText.setText("");
	    		finish();
	    	}	   	   
	    }
	};

}
