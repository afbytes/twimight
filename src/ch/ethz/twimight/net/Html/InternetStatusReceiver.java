package ch.ethz.twimight.net.Html;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;

public class InternetStatusReceiver extends BroadcastReceiver {

	private static final String TAG = "InternetStatusReceiver";
	private ConnectivityManager cm;
	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		if(networkInfo == null){
			Log.d(TAG, "no active network connected");
			return;
		}
		int networkType = networkInfo.getType();
		Log.d(TAG, "network type:" + String.valueOf(networkType));
		
		if(networkType == ConnectivityManager.TYPE_WIFI){
			Log.d(TAG, "wifi connected");
			Intent i = new Intent(context, HtmlService.class);
			i.putExtra(HtmlService.DOWNLOAD_REQUEST, HtmlService.DOWNLOAD_ALL);
			context.startService(i);
			//start html service
		}
		else{
			Log.d(TAG, "wifi not connected");
			return;
		}
		
	}

}
