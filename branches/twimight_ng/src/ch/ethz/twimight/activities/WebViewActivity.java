package ch.ethz.twimight.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import ch.ethz.twimight.R;

public class WebViewActivity extends Activity {
	
	public static final String HTML_PAGE = "html_page";	
	public static final String TAG = "WebViewActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.webview);
		Log.i(TAG,"inside on create");
		Intent intent = getIntent();
		WebView web = (WebView) findViewById(R.id.webview);
		web.getSettings().setJavaScriptEnabled(true);
		web.getSettings().setBuiltInZoomControls(true); //Enable Multitouch if supported
		//web.loadData(intent.getStringExtra(HTML_PAGE), "text/html", null);
		web.loadUrl("http://techcrunch.com/2012/04/17/twitpolls/");
		web.setWebViewClient(new HelloWebViewClient());
	}
	
	private class HelloWebViewClient extends WebViewClient {
	    @Override
	    public boolean shouldOverrideUrlLoading(WebView view, String url) {
	        view.loadUrl(url);
	        return true;
	    }
	}
	
	

}
