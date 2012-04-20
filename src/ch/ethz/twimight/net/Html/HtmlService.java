package ch.ethz.twimight.net.Html;

import java.io.IOException;
import java.util.ArrayList;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import ch.ethz.twimight.activities.WebViewActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;

public class HtmlService extends Service {
	
	ArrayList<String> htmlUrls =  new ArrayList<String>();
	public static final String TAG = "HtmlService";
	

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		
		htmlUrls.add("http://www.google.com");
		new GetHtmlPagesTask().execute();
		return START_NOT_STICKY;
	}



	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	/**
	 * Loads the html pages from internet
	 * @author pcarta
	 */
	
	private class GetHtmlPagesTask extends AsyncTask<Void, Void, String[]> {

		Exception ex;		

		@Override
		protected String[] doInBackground(Void... params) {
			String[] pages = new String[htmlUrls.size()];
			int i=0;

			for (String url: htmlUrls) {
				pages[i] = getHtmlPage(url);				
				i++;
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
	
	
	 /* Loads the html pages from internet
	 * @author pcarta
	 */
	 
	private class InsertHtmlPagesTask extends AsyncTask<String[], Void, Void> {

		@Override
		protected Void doInBackground(String[]... params) {
			Log.i(TAG,"inserting page into the db");
			HtmlPagesDbHelper dbHelper = new HtmlPagesDbHelper(getBaseContext());
			dbHelper.open();
			String[] pages = params[0];

			for (int i=0;i<pages.length;i++) {	
				if (pages[i] != null)
					dbHelper.insertPage(htmlUrls.get(i),pages[i]);
			}
			return null;
		
		}

		@Override
		protected void onPostExecute(Void params){
			Intent intent = new Intent(HtmlService.this,WebViewActivity.class);
			HtmlPagesDbHelper dbHelper = new HtmlPagesDbHelper(getBaseContext());
			dbHelper.open();			
			intent.putExtra(WebViewActivity.HTML_PAGE, dbHelper.getPage("http://www.google.com"));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}

	}
	
	   private String getHtmlPage(String url) {
		   Connection conn = Jsoup.connect(url);
		   Document doc;
		try {
			doc = conn.get();
			//Element root = doc.select("html").first();			
		    //Log.i(TAG,"html: " + doc.outerHtml());
			
			Log.i(TAG,"html: \n"  + doc.outerHtml());
			return null;
		} catch (IOException e) {
			return null;
		}
		   
		   
	}
	
	


}
