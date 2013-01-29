package ch.ethz.twimight.activities;

import java.io.FileInputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.Html.WebArchiveReader;
import ch.ethz.twimight.util.SDCardHelper;

public class WebViewActivity extends Activity {
	
	public static final String HTML_PAGE = "html_page";	
	public static final String TAG = "WebViewActivity";
	private SDCardHelper sdCardHelper;
	private HtmlPagesDbHelper htmlDbHelper;
	private ProgressDialog progressBar; 

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.webview);
		Log.i(TAG,"inside on create");
		Intent intent = getIntent();
		String tweetId = intent.getStringExtra("tweet_id");
		Log.d("test1", tweetId);
		String url = intent.getStringExtra("url");
		String userId = intent.getStringExtra("user_id");
		String[] filePath = {HtmlPage.HTML_PATH + "/" + userId};
		htmlDbHelper = new HtmlPagesDbHelper(this);
		sdCardHelper = new SDCardHelper(this);
		WebView web = (WebView) findViewById(R.id.webview);
		web.getSettings().setJavaScriptEnabled(true);
		web.getSettings().setDomStorageEnabled(true); //twitter api and youtube api hack
		 
        web.getSettings().setBuiltInZoomControls(true); //Enable Multitouch if supported
        
		if(sdCardHelper.checkSDStuff(filePath)){

			htmlDbHelper.open();
			String filename = htmlDbHelper.getPageInfo(url, tweetId).getAsString(HtmlPage.COL_FILENAME);
			Log.d(TAG, filename);
			if(sdCardHelper.getFileFromSDCard(filePath[0], filename).exists() && sdCardHelper.getFileFromSDCard(filePath[0], filename).length() > 500){
				progressBar = ProgressDialog.show(this, "LOADING...", url);
				Uri webUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(filePath[0], filename));
				Log.d(TAG, webUri.getPath());
				try {
		            FileInputStream is = new FileInputStream(webUri.getPath());
		            WebArchiveReader wr = new WebArchiveReader() {
		                protected void onFinished(WebView v) {
		                    // we are notified here when the page is fully loaded.
		                    Log.d(TAG, "load finished");
		                    continueWhenLoaded(v);
		                }
		            };
		            // To read from a file instead of an asset, use:
		            // FileInputStream is = new FileInputStream(fileName);
		            if (wr.readWebArchive(is)) {
		                wr.loadToWebView(web);
		            }
		        } catch (IOException e) {
		            e.printStackTrace();
		        }
			}
			else{
				Log.d(TAG, "file not exist:" + filename);
				Toast.makeText(this, "file not exist", Toast.LENGTH_LONG).show();
			}

			
		}
		
	}
	
	private void continueWhenLoaded(WebView webView) {
        Log.d(TAG, "Page from WebArchive fully loaded.");
        // If you need to set your own WebViewClient, do it here,
        // after the WebArchive was fully loaded:
       
        webView.setWebViewClient(new WebClientView());
        

        // Any other code we need to execute after loading a page from a WebArchive...
        if(progressBar.isShowing()){
			progressBar.dismiss();
			Toast.makeText(getBaseContext(), "Loading finished.", Toast.LENGTH_LONG).show();
		}
    }
	

    
	private class WebClientView extends WebViewClient {

		@Override
		public void onLoadResource(WebView view, String url) {
			// TODO Auto-generated method stub
			super.onLoadResource(view, url);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onPageFinished without download");
			super.onPageFinished(view, url);
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onPageStarted");
			super.onPageStarted(view, url, favicon);
		}

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			// TODO Auto-generated method stubsuper.shouldOverrideUrlLoading(view, url);
			return true;
		}
		
		
	}
	

}
