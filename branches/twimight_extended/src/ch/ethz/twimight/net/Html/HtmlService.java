package ch.ethz.twimight.net.Html;

import java.io.File;
import java.util.ArrayList;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.SpannableString;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.util.TweetTagHandler;

public class HtmlService extends Service {
	
	ArrayList<String> htmlUrls =  new ArrayList<String>();
	public static final String TAG = "HtmlService";
	public static final int DOWNLOAD_SINGLE = 0;
	public static final int DOWNLOAD_ALL = 1;
	public static final int CLEAR_ALL = 2;
	public static final String DOWNLOAD_REQUEST = "download_request";
	private SDCardHelper sdCardHelper;
	private HtmlPagesDbHelper htmlDbHelper;
	private Handler webHandler;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		boolean noConn = false;
		ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		if(cm.getActiveNetworkInfo()==null || !cm.getActiveNetworkInfo().isConnected()){
			
			noConn = true;
			
		}
		
		if(intent != null){
				
			sdCardHelper = new SDCardHelper(this);
			htmlDbHelper = new HtmlPagesDbHelper(this);
			htmlDbHelper.open();
			webHandler = new Handler();
			Bundle extras = intent.getExtras();
			
			
			int serviceCommand = extras.getInt(DOWNLOAD_REQUEST);
			switch(serviceCommand){
					
				case DOWNLOAD_SINGLE:
					if(noConn){
						Log.w(TAG, "Error synching: no connectivity");
						return START_NOT_STICKY;
					}
					if(extras.getString("tweetId")==null){
						return START_NOT_STICKY;
					}
					Log.d(TAG, "download single request");
					downloadTweetHtmlPages(extras);
					break;
					
				case DOWNLOAD_ALL:
					if(noConn){
						Log.w(TAG, "Error synching: no connectivity");
						return START_NOT_STICKY;
					}
					Log.d(TAG, "bulk download request");
					bulkDownloadTweetHtmlPages();
					break;
					
				case CLEAR_ALL:
					Log.d(TAG, "clear cache request");
					clearAllHtmlPages();
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
	 * download html pages attached to a single tweet
	 * @author fshi
	 *
	 */
	private void downloadTweetHtmlPages(Bundle mBundle) {

		Log.d(TAG, "downloadTweetHtmlPages");
			
		Uri webUri;	
		String tweetId = mBundle.getString("tweetId");
		String userId = mBundle.getString("user_id");
		ArrayList<String> htmlUrls = mBundle.getStringArrayList("urls");
		String[] filePath = {HtmlPage.HTML_PATH + "/" + userId};
		
			
		Log.d(TAG, htmlUrls.toString());
		if(sdCardHelper.checkSDStuff(filePath)){	
			
			for(int i=0;i<htmlUrls.size();i++){
				//web view declaration

				String htmlUrl = htmlUrls.get(i);
				ContentValues htmlCV = htmlDbHelper.getPageInfo(htmlUrl, tweetId);
				webUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(filePath[0], htmlCV.getAsString(HtmlPage.COL_FILENAME)));
				webDownload(htmlCV, webUri.getPath());


			}
			
		}
					
		Log.d(TAG, "download finished");
			
	}

	/**
	 * download and insert html pages with bulk tweets
	 * @author fshi
	 *
	 */
	private void bulkDownloadTweetHtmlPages(){
		
		long lastTime = getLastDownloadedTime(getBaseContext());
		if((System.currentTimeMillis() - lastTime) < 1000*30){
			return;
		}
		else{
			Log.d(TAG, "downloadBulkHtmlPages since:" + String.valueOf(lastTime));
			//get the timestamp of the last tweet we download
			
			//find if new tweets received time is later then previous one have been received
			Cursor c = htmlDbHelper.getNewTweet(lastTime);
			
			Log.d(TAG, "new tweets:" + String.valueOf(c.getCount()));
			
			//download new tweets
			for (c.moveToLast(); !c.isBeforeFirst(); c.moveToPrevious())
			{
				
				String text = c.getString(c.getColumnIndex(Tweets.COL_TEXT));
				String tweetId = String.valueOf(c.getLong(c.getColumnIndex(Tweets.COL_TID)));
				String userId = String.valueOf(c.getLong(c.getColumnIndex(Tweets.COL_USER)));
				String[] filePath = {HtmlPage.HTML_PATH + "/" + userId};
				
				SpannableString str = new SpannableString(Html.fromHtml(text, null, new TweetTagHandler(this)));
				String substr = str.toString().substring(str.toString().indexOf("http"));
				String[] strarr = substr.split(" ");
				ArrayList<String> htmlUrls = new ArrayList<String>();
				//save the urls of the tweet in a list
				for(String subStrarr : strarr)
					if(subStrarr.indexOf("http://") == 0)
						htmlUrls.add(subStrarr);


				for(String htmlUrl : htmlUrls){

					//find if it's already being downloaded
					ContentValues isExist = htmlDbHelper.getPageInfo(htmlUrl, tweetId);
					if(isExist==null){
						//if not

						String filename = "twimight" + String.valueOf(System.currentTimeMillis()) + ".xml";
						htmlDbHelper.insertPage(htmlUrl, filename, tweetId, userId, 0);

					}

				}

			}
			
			c.close();
			
			downloadPages();
			
		}
		

	}

	
	private void downloadPages(){
		setRecentDownloadedTime(System.currentTimeMillis(), getBaseContext());
		
		int downloadCount = 1;
		
		//download unsuccessfully downloaded pages
		Cursor c = htmlDbHelper.getUndownloadedHtmls();

		for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext())
		{
			if(downloadCount > 10)return;
			String htmlUrl = c.getString(c.getColumnIndex(HtmlPage.COL_URL));
			String tweetId = c.getString(c.getColumnIndex(HtmlPage.COL_TID));
			String filename = c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME));
			
			String userId = c.getString(c.getColumnIndex(HtmlPage.COL_USER));
			
			String[] filePath = {HtmlPage.HTML_PATH + "/" + userId};
				
			if(sdCardHelper.checkSDStuff(filePath)){
					
				Uri webUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(filePath[0], filename));
					
				ContentValues htmlCV = htmlDbHelper.getPageInfo(htmlUrl, tweetId);

				webDownload(htmlCV, webUri.getPath());
					
				downloadCount++;

			}

		}
		c.close();
		Log.d(TAG, "download finished");
	}
	
	private boolean webDownload(ContentValues htmlCV, String filePath){
		boolean result = true;
		
		webHandler.post(new webRunnable(filePath, htmlCV));
		
		return result;
	}
	
	private class webRunnable implements Runnable{
		
		private String filePath;
		private ContentValues htmlCV;
		
		webRunnable(String filePath, ContentValues htmlCV){
			this.filePath = filePath;
			this.htmlCV = htmlCV;
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			WebView web = new WebView(getBaseContext());

			web.setWebViewClient(new WebClientDownload(filePath, htmlCV));
			web.getSettings().setJavaScriptEnabled(true);
			web.getSettings().setDomStorageEnabled(true);
			web.loadUrl(htmlCV.getAsString(HtmlPage.COL_URL));
		}
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
	
	//clear all downloaded html pages and set the database to status undownloaded (int downloaded =0) for all rows
	private void clearAllHtmlPages(){
		
		Cursor c = htmlDbHelper.getAll();
		
		for(c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
			
			String filename = c.getString(c.getColumnIndex(HtmlPage.COL_FILENAME));
			String userId = c.getString(c.getColumnIndex(HtmlPage.COL_USER));
			String[] filePath = {HtmlPage.HTML_PATH + "/" + userId};
			if(sdCardHelper.checkSDStuff(filePath)){
				File deleteFile = sdCardHelper.getFileFromSDCard(filePath[0], filename);
				if(deleteFile.exists()){
					if(deleteFile.delete()){

						htmlDbHelper.updatePage(c.getString(c.getColumnIndex(HtmlPage.COL_URL)), filename,
								c.getString(c.getColumnIndex(HtmlPage.COL_TID)), userId, 0);

					}
				}
			}
		}
	}
	
	
	//webview only to download webarchive
	@SuppressLint("NewApi")
	private class WebClientDownload extends WebViewClient {
		private String filePath;
		private String baseUrl;
		private String tweetId;
		private String filename;
		private String userId;
		private boolean loadingFailed;

		public WebClientDownload(String filePath, ContentValues htmlCV){
			super();
			this.filePath = filePath;
			this.baseUrl = htmlCV.getAsString(HtmlPage.COL_URL);
			this.filename = htmlCV.getAsString(HtmlPage.COL_FILENAME);
			this.tweetId = htmlCV.getAsString(HtmlPage.COL_TID);
			this.userId = htmlCV.getAsString(HtmlPage.COL_USER);
			this.loadingFailed = false;
		}
		
		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			// TODO Auto-generated method stub
			if(htmlDbHelper.updatePage(this.baseUrl, this.filename, this.tweetId, this.userId, 0)){
				Log.d(TAG, "on page started");	
			}
			super.onPageStarted(view, url, favicon);
		}


		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			// TODO Auto-generated method stub
			Log.d(TAG, "on received error" + failingUrl);
			htmlDbHelper.updatePage(this.baseUrl, this.filename, this.tweetId, this.userId, 0);
			loadingFailed = true;
			super.onReceivedError(view, errorCode, description, failingUrl);
		}


		@Override
		public void onReceivedSslError(WebView view, SslErrorHandler handler,
				SslError error) {
			// TODO Auto-generated method stub
			Log.d(TAG, "on received ssl error");
			htmlDbHelper.updatePage(this.baseUrl, this.filename, this.tweetId, this.userId, 0);
			loadingFailed = true;
			super.onReceivedSslError(view, handler, error);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			// TODO Auto-generated method stub
			if(!loadingFailed){
				view.saveWebArchive(filePath);
				if(htmlDbHelper.updatePage(this.baseUrl, this.filename, this.tweetId, this.userId, 1)){
					Log.d(TAG, "onPageFinished and downloaded:" + url + " in " + filePath);
				}
			}
			else{
				Log.d(TAG, "download failed" + url);
			}
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			// TODO Auto-generated method stub
			view.loadUrl(url);
			Log.d(TAG, baseUrl + "redirect to:" + url);
			return true;
		}		
		

	}

}
