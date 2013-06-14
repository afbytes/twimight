package ch.ethz.twimight.net.Html;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources.NotFoundException;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.util.SDCardHelper;

public class HtmlService extends Service {
	
	ArrayList<String> htmlUrls =  new ArrayList<String>();
	public static final String TAG = "HtmlService";
	
	public static final int DOWNLOAD_ALL = 1;	
	public static final int DOWNLOAD_ONLY_FORCED = 2;
	public static final String DOWNLOAD_REQUEST = "download_request";
	private SDCardHelper sdCardHelper;
	private HtmlPagesDbHelper htmlDbHelper;
	private int limit = 0;
	
	Cursor c = null;
	


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {		
		
		ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		if(cm.getActiveNetworkInfo()==null || !cm.getActiveNetworkInfo().isConnected())			
			return START_NOT_STICKY;		
		
		if(intent != null){
			
			
			sdCardHelper = new SDCardHelper();
			htmlDbHelper = new HtmlPagesDbHelper(getApplicationContext());
			htmlDbHelper.open();			

			int serviceCommand = intent.getIntExtra(DOWNLOAD_REQUEST,DOWNLOAD_ALL);
			switch(serviceCommand){			

			case DOWNLOAD_ALL:
				bulkDownloadHtmlPages(false);
				break;			

			case DOWNLOAD_ONLY_FORCED:
				Log.d(TAG, "forced download");
				bulkDownloadHtmlPages(true);
				break;

			default:
				throw new IllegalArgumentException("Exception: Unknown download request");
			}
			return START_STICKY;
		}
		else return START_NOT_STICKY;

	}



	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	


	/**
	 * download and insert html pages with bulk tweets
	 * @author fshi
	 *
	 */
	private void bulkDownloadHtmlPages(boolean forced){
		
		long lastTime = getLastDownloadedTime(getBaseContext());
		if((System.currentTimeMillis() - lastTime) < 1000*30){
			return;
		}
		else{			
			new CleanCacheDownload(forced).execute();					
			
		}
		

	}
		
	//if downloaded pages > 100, clear those created 1 days ago
	private void checkCacheSize(){
		
		Log.i(TAG, "check cache size");
		Cursor c = htmlDbHelper.getDownloadedHtmls();
		if(c.getCount() > 100){
			htmlDbHelper.clearHtmlPages(1*24*3600*1000);
		}
	}
	
	
	private class CleanCacheDownload extends AsyncTask<Void,Void,Void>{
		boolean forced;
		
		public CleanCacheDownload(boolean forced) {
			this.forced = forced;
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			// TODO Auto-generated method stub
			
			checkCacheSize();				
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			// TODO Auto-generated method stub
			downloadPages(forced);	
		}
		
		
		
	}
	
	
	
	private class fileDownload extends AsyncTask<ContentValues, Void, Boolean>{

		@Override
		protected Boolean doInBackground(ContentValues... params) {
			// TODO Auto-generated method stub
			
			ContentValues fileCV = params[0];
			
			Long tweetId = fileCV.getAsLong(HtmlPage.COL_TID);
		
			String url = fileCV.getAsString(HtmlPage.COL_URL);
			String filename = fileCV.getAsString(HtmlPage.COL_FILENAME);
			int forced = fileCV.getAsInteger(HtmlPage.COL_FORCED);
			int tries = fileCV.getAsInteger(HtmlPage.COL_ATTEMPTS);
			String[] filePath = {HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(getApplicationContext())};
			if(sdCardHelper.checkSDState(filePath)){
				File targetFile = sdCardHelper.getFileFromSDCard(filePath[0], filename);
				try {
			        URL fileUrl = new URL(url);
			        
		            URLConnection connection = fileUrl.openConnection();
		            connection.connect();

		            // download the file
		            InputStream input = new BufferedInputStream(fileUrl.openStream());
		            OutputStream output = new FileOutputStream(targetFile);

		            byte data[] = new byte[1024];
		            int count;
		            while ((count = input.read(data)) != -1) {
		                
		                output.write(data, 0, count);
		            }

		            output.flush();
		            output.close();
		            input.close();
					htmlDbHelper.updatePage(url, filename, tweetId, 1, forced, tries + 1);
					Log.d(TAG, "file download finished");
				} catch (NotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			return null;
		}
		
	}
	
	
	private class GetPagesTask extends AsyncTask<Void,Void,Void>{
		
        boolean forced;
		
		public GetPagesTask(boolean forced) {
			this.forced = forced;
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			// TODO Auto-generated method stub			
			//download unsuccessfully downloaded pages			
			cleanupMess();
			c = htmlDbHelper.getUndownloadedHtmls(forced);	
			if (c.getCount() > 0) {
				c.moveToFirst();				
			}
			
			return null;
		}

		@Override
		protected void onPostExecute(Void param) {		
			storePage();			

		}



	}


	private void downloadPages(boolean forced){
		
		setRecentDownloadedTime(System.currentTimeMillis(), getBaseContext());	
		new GetPagesTask(forced).execute();	
		
	}
	
	public void storePage() {
		
		if (c != null && c.getCount() > 0 && (!c.isAfterLast())) {
			
			limit++;
			if (limit == 10) {limit = 0; return;}
			
			String htmlUrl = c.getString(c.getColumnIndex(HtmlPage.COL_URL));
			String filename = c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME));	
			String[] filePath = {HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(getApplicationContext())};

			if(sdCardHelper.checkSDState(filePath)){
				
				ContentValues htmlCV = htmlDbHelper.getPageInfo(htmlUrl);

				switch(sdCardHelper.checkFileType(htmlUrl)){
				case SDCardHelper.TYPE_XML:						
					
					Uri webUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(filePath[0], filename));
					webDownload(htmlCV, webUri.getPath());
					break;
				case SDCardHelper.TYPE_PDF:

					processFiles(htmlCV, "pdf");
					break;

				case SDCardHelper.TYPE_JPG:
					processFiles(htmlCV, "jpg");
					break;

				case SDCardHelper.TYPE_PNG:
					processFiles(htmlCV, "png");
					break;	

				case SDCardHelper.TYPE_GIF:
					processFiles(htmlCV, "gif");
					break;

				case SDCardHelper.TYPE_MP3:
					processFiles(htmlCV, "mp3");
					break;

				case SDCardHelper.TYPE_FLV:
					processFiles(htmlCV, "flv");
					break;		

				case SDCardHelper.TYPE_RMVB:
					processFiles(htmlCV, "rmvb");
					break;

				case SDCardHelper.TYPE_MP4:
					processFiles(htmlCV, "mp4");
					break;

				default:
					break;

				}

			}
			c.moveToNext();
			
		} else if (c != null)
			c.close();
		
		
	}



	private void processFiles(ContentValues fileCV, String fileSuffix){
		Log.i(TAG, "file type: " + fileSuffix);
		int len = fileSuffix.length();
		String filename = fileCV.getAsString(HtmlPage.COL_FILENAME).substring(0, fileCV.getAsString(HtmlPage.COL_FILENAME).length()-len-1) + "." + fileSuffix;
		fileCV.put(HtmlPage.COL_FILENAME, filename);
		htmlDbHelper.updatePage(fileCV.getAsString(HtmlPage.COL_URL), filename, fileCV.getAsLong(HtmlPage.COL_TID), 
				 fileCV.getAsInteger(HtmlPage.COL_DOWNLOADED), fileCV.getAsInteger(HtmlPage.COL_FORCED), fileCV.getAsInteger(HtmlPage.COL_ATTEMPTS));
		new fileDownload().execute(fileCV);
	}
	
	/**
	 * correct download errors caused by network interrupt
	 */
	private void cleanupMess(){
		
		Cursor c = htmlDbHelper.getDownloadedHtmls();
		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext())
		{
			String htmlUrl = c.getString(c.getColumnIndex(HtmlPage.COL_URL));
			Long tweetId = c.getLong(c.getColumnIndex(HtmlPage.COL_TID));
			String filename = c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME));
			int forced = c.getInt(c.getColumnIndex(HtmlPage.COL_FORCED));
			int tries = c.getInt(c.getColumnIndex(HtmlPage.COL_ATTEMPTS));	
			
			String[] filePath = {HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(getApplicationContext())};
			switch(sdCardHelper.checkFileType(htmlUrl)){
				case SDCardHelper.TYPE_XML:
					if(sdCardHelper.checkSDState(filePath)){
						
	
						File htmlPage = sdCardHelper.getFileFromSDCard(filePath[0], filename);
	
						if(!htmlPage.exists() || (sdCardHelper.getFileFromSDCard(filePath[0], filename).length() < 10) ){
							Log.i(TAG, "update ###" + filename);
							htmlDbHelper.updatePage(htmlUrl, filename, tweetId, 0, forced, tries);
						}	
					}
					break;	
				default:
					break;
			}
		}
	}


	private boolean webDownload(ContentValues htmlCV, String filePath){
		boolean result = true;
		
		WebView web = new WebView(getBaseContext());
		// TODO Auto-generated method stub				
		web.setWebViewClient(new WebClientDownload(filePath, htmlCV));			
		web.getSettings().setJavaScriptEnabled(true);
		web.getSettings().setDomStorageEnabled(true);		
		web.loadUrl(htmlCV.getAsString(HtmlPage.COL_URL));
		
		return result;
	}
	

	

	/**
	 * store the id for the tweets of which html pages have been downloaded
	 * @param sinceId
	 * @param context
	 */
	public static void setRecentDownloadedTime(long sinceTime, Context context) {
		
		Log.i(TAG,"set preference to:" + String.valueOf(sinceTime));
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putLong("downloadSinceTime" , sinceTime);
		prefEditor.commit();
	}	
	
	/**
	 * get the last timetamp for performing bulkdownload action
	 * @param context
	 * @return
	 */
	public static long getLastDownloadedTime(Context context) {
		
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		Long lastTime = prefs.getLong("downloadSinceTime",Long.valueOf(0));
		Log.i(TAG, "get preference:" + String.valueOf(lastTime));
		return lastTime;
		
	}
	
	
	

	//webview only to download webarchive
	@SuppressLint("NewApi")
	private class WebClientDownload extends WebViewClient {
		private String filePath;
		private String baseUrl;
		private Long tweetId;
		private String filename;	
		private int forced;
		private int tries;
		private boolean loadingFailed;
		
		private class WebArchiveCallback implements ValueCallback<String> {

			@Override
			public void onReceiveValue(String str) {				
				if (str == null)
					htmlDbHelper.updatePage(baseUrl, filename, tweetId, 0, forced, tries);
				else {			
					
					String basePath = HtmlPage.HTML_PATH + "/" + LoginActivity.getTwitterId(HtmlService.this);
					Log.i(TAG, "length: " + sdCardHelper.getFileFromSDCard(basePath, filename).length() + " bytes");
					if (sdCardHelper.getFileFromSDCard(basePath, filename).length() > 0) {
						
						htmlDbHelper.updatePage(baseUrl, filename, tweetId, 1, forced, tries);
				        getContentResolver().notifyChange(Tweets.TABLE_TIMELINE_URI, null);			
					} 			    	
					
				}				
				storePage();
				
			}
			
		}

		public WebClientDownload(String filePath, ContentValues htmlCV){
			super();
			this.filePath = filePath;
			Log.i(TAG,filePath);
			this.baseUrl = htmlCV.getAsString(HtmlPage.COL_URL);
			this.filename = htmlCV.getAsString(HtmlPage.COL_FILENAME);
			this.tweetId = htmlCV.getAsLong(HtmlPage.COL_TID);			
			this.forced = htmlCV.getAsInteger(HtmlPage.COL_FORCED);
			this.tries = htmlCV.getAsInteger(HtmlPage.COL_ATTEMPTS) + 1;
			this.loadingFailed = false;			
		}
		
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			// TODO Auto-generated method stub
			htmlDbHelper.updatePage(baseUrl, filename, tweetId, 0, forced, tries);
			Log.d(TAG, "on page started");			
			super.onPageStarted(view, url, favicon);
		}


		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			// TODO Auto-generated method stub
			Log.d(TAG, "on received error" + failingUrl);
			htmlDbHelper.updatePage(baseUrl, filename, tweetId, 0, forced, tries);
			loadingFailed = true;
			super.onReceivedError(view, errorCode, description, failingUrl);
		}


		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler,
				SslError error) {
			// TODO Auto-generated method stub
			Log.d(TAG, "on received ssl error");
			htmlDbHelper.updatePage(baseUrl, filename, tweetId, 0, forced, tries);
			loadingFailed = true;
			super.onReceivedSslError(view, handler, error);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			// TODO Auto-generated method stub
			if(!loadingFailed){								
				view.saveWebArchive(filePath, false , new WebArchiveCallback() );				
				
			}
			else{
				Log.d(TAG, "download failed" + url);
			}
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			// TODO Auto-generated method stub
			view.loadUrl(url);
			Log.d(TAG, baseUrl + " redirect to:" + url);
			return true;
		}	

	}

}
