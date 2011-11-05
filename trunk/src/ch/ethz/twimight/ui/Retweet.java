package ch.ethz.twimight.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;
import ch.ethz.twimight.TweetContextActions;

public class Retweet extends AsyncTask<Long, Void, Boolean> {	
	 TweetContextActions contextActions;
	 String table;
	 Context cont;
	 boolean show;
	
	 	public Retweet (TweetContextActions contextActions, String table, Context cont, boolean show) {
	 		this.contextActions = contextActions;
	 		this.table = table;
	 		this.cont = cont;
	 		this.show = show;
	 	} 	
		
		
		@Override
		protected Boolean doInBackground(Long... id ) {
			return (contextActions.retweet(id[0], table,true));					 
		}		

		// This is in the UI thread, so we can mess with the UI
		@Override
		protected void onPostExecute(Boolean result) {			
			if (result)
				Toast.makeText(cont, "Retweet posted succesfully", Toast.LENGTH_SHORT).show();
			else {
				if (show )
					Toast.makeText(cont, "Retweet not posted ", Toast.LENGTH_SHORT).show(); 
			}
			
		}
	}


						 
		
	
