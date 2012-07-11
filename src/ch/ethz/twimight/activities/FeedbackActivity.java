package ch.ethz.twimight.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import ch.ethz.twimight.R;

public class FeedbackActivity extends Activity {
	
	private EditText description;		
	private Button cancelButton;
	private Button sendButton;
	
	public static final String COL_TWITTER_ID = "twitter_id";
	public static final String COL_TEXT = "description";
	public static final String COL_TYPE = "type";
	
	public static final int  TYPE_BUG = 1;
	public static final int  TYPE_FEAT_REQUEST = 2;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.feedback);
		
		sendButton = (Button) findViewById(R.id.feedback_send);
		cancelButton = (Button) findViewById(R.id.feedback_cancel);
		
		sendButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				
			}
			
		});
		cancelButton.setOnClickListener(new OnClickListener(){

			@Override
			public void onClick(View v) {
				finish();
			}
			
		});
	}
	
	
	

}
