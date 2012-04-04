package ch.ethz.twimight.net.Html;

import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.twitter.TwitterService;

public class HtmlService extends Service {
	
	ArrayList<String> htmlUrls =  new ArrayList<String>();
	
	

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		return super.onStartCommand(intent, flags, startId);
	}



	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	/**
	 * Loads the html pages from internet
	 * @author pcarta
	 *
	
	private class GetHtmlPagesTask extends AsyncTask<Void, Void, String[]> {

		Exception ex;
		

		@Override
		protected String[] doInBackground(Void... params) {
			String[] pages = new String[htmlUrls.size()];
			int i=0;
			/*
			synchronized(TwitterService.this) {
				for (String url: htmlUrls) {
					pages[i] = getHtmlPage(url);
					i++;
				}
			}
			
			return pages;

		}

		@Override
		protected void onPostExecute(String[] result) {
			if (result != null) {
				if (result.length > 0){
					new InsertHtmlPagesTask().execute(result);
				}
			}

		}

	}
	
	
	 * Loads the html pages from internet
	 * @author pcarta
	 *
	 
	private class InsertHtmlPagesTask extends AsyncTask<String[], Void, Void> {

		@Override
		protected Void doInBackground(String[]... params) {
			HtmlPagesDbHelper dbHelper = new HtmlPagesDbHelper();
			dbHelper.open();
			String[] pages = params[0];
			
			for (int i=0;i<pages.length;i++) {
				synchronized() {
					dbHelper.insertPage(htmlUrls.get(i),pages[i]);
				}
				
			}
			return null;
		
		}

		@Override
		protected void onPostExecute(Void params){
			
		}

	}
	
	
*/

}
