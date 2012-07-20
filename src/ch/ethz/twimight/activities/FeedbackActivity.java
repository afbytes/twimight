package ch.ethz.twimight.activities;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.net.tds.TDSService;

public class FeedbackActivity extends Activity {
	
	private static final String TAG = "FeedbackActivity";
	
	private EditText description;		
	private Button cancelButton;
	private Button sendButton;
	private RadioButton radioBug;
	//private RadioButton radioFeature;
	
	public static final String COL_TWITTER_ID = "twitter_id";
	public static final String COL_TEXT = "description";
	public static final String COL_TYPE = "type";
	
	public static final int  TYPE_BUG = 1;
	public static final int  TYPE_FEAT_REQUEST = 2;
	
	public static final String SEND_RESULT_ACTION = "send_result_action";
	public static final String SEND_RESULT = "send_result";
	public static final int SEND_SUCCESS = 1;
	public static final int SEND_FAILURE = 2;
	
	ProgressDialog progressDialog;
	SendingReceiver sendReceiver;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.feedback);
		
		description = (EditText) findViewById(R.id.feedbackEditText);
		sendButton = (Button) findViewById(R.id.feedback_send);
		cancelButton = (Button) findViewById(R.id.feedback_cancel);
		radioBug = (RadioButton) findViewById(R.id.radioBug);
		//radioFeature = (RadioButton) findViewById(R.id.radioFeature);
		IntentFilter intentFilter = new IntentFilter(SEND_RESULT_ACTION	);
		sendReceiver = new SendingReceiver();
		registerReceiver(sendReceiver,intentFilter );
		
		sendButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if ((description.getText().length()) > 0) {
					int type;
					if(radioBug.isChecked())
						type = TYPE_BUG;
					else
						type = TYPE_FEAT_REQUEST;
					Intent synchIntent = new Intent(FeedbackActivity.this, TDSService.class);
					synchIntent.putExtra("synch_request", TDSService.SYNCH_BUG);
					synchIntent.putExtra(TDSService.DESCRIPTION_FIELD, description.getText().toString());
					synchIntent.putExtra(TDSService.TYPE_FIELD, type);
					startService(synchIntent);
					progressDialog=ProgressDialog.show(FeedbackActivity.this, "In progress", "Sending feedback to the Twimight Server");
					progressDialog.setCancelable(true);
					
				} else
					Toast.makeText(FeedbackActivity.this, "Cannot send an empty feedback report", Toast.LENGTH_SHORT).show();
				
				
			}
			
		});
		cancelButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				finish();
			}
			
		});
	}
	
	
	
	@Override
	protected void onDestroy() {
		
		super.onDestroy();
		unregisterReceiver(sendReceiver);
	}



	private class SendingReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			
	        	Log.i(TAG,"onReceive");
	        	if(intent.hasExtra(SEND_RESULT)){
	        		if (progressDialog != null)
	        			progressDialog.dismiss(); 
	        		    if (intent.getIntExtra(SEND_RESULT, SEND_FAILURE) == SEND_SUCCESS) {
	        		    	Toast.makeText(FeedbackActivity.this, "Feedback sent correctly", Toast.LENGTH_SHORT).show();
	        		        finish();
	        		    }
	        		    else
	        		    	Toast.makeText(FeedbackActivity.this, "Error sending feedback, please try again later", Toast.LENGTH_SHORT).show();
	        			
	        		}	        	
	        
			
		}
		
	}
	
	
	

}
