package ch.ethz.twimight.net.Html;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

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
		
		htmlUrls.add("http://techcrunch.com/2012/04/17/twitpolls/");
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
				Log.i(TAG,"html page: " + pages[i]);
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
				dbHelper.insertPage(htmlUrls.get(i),pages[i]);
			}
			return null;
		
		}

		@Override
		protected void onPostExecute(Void params){
			Intent intent = new Intent(HtmlService.this,WebViewActivity.class);
			HtmlPagesDbHelper dbHelper = new HtmlPagesDbHelper(getBaseContext());
			dbHelper.open();			
			intent.putExtra(WebViewActivity.HTML_PAGE, dbHelper.getPage("http://techcrunch.com/2012/04/17/twitpolls/"));
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}

	}
	
	   private String getHtmlPage(String url) {
		   
		   HttpClient client = new DefaultHttpClient();
		   HttpGet request = new HttpGet(url);
		   HttpResponse response;
		   try {
			   response = client.execute(request);
			   String html = "";
			   InputStream in = response.getEntity().getContent();
			   BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			   StringBuilder str = new StringBuilder();
			   String line = null;
			   
			   while((line = reader.readLine()) != null)			   
				   str.append(line);
			   
			   in.close();
			   html = str.toString();
			   return html;

		   } catch (ClientProtocolException e) {			  
			   return null;
		   } catch (IOException e) {			  
			   return null;
		   }		
	}
	
	


}
