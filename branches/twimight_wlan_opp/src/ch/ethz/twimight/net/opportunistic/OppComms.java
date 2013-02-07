package ch.ethz.twimight.net.opportunistic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import ch.ethz.twimight.data.MacsDBHelper;

public abstract class OppComms {

	static final String TAG = OppComms.class.getSimpleName();

	public static final long MIN_UPDATE_INTERVAL = 10*1000L; /** Interval FOR UPDATES */
	public static final long MAX_UPDATE_INTERVAL = 30*1000L; /** Interval FOR UPDATES */
	long lastUpdate;

	List<Neighbor> neighbors = new CopyOnWriteArrayList<Neighbor>();
	List<Neighbor> new_neighbors = new CopyOnWriteArrayList<Neighbor>();
	ContentObserver neighborObserver;
	Cursor neighborCursor;
	ContentResolver resolver;
	final String[] projection = { "ip", "device_id" };
	final int PORT = 29761;
	final Handler mHandler;	

	public static boolean isBinded = false;
	protected static final String NEW_PACKAGE = "ch.ethz.csg.wlanopp.START_WLANOPP";
	protected static final String NEW_PROVIDER_URI = "content://ch.ethz.csg.wlanopp.provider/neighbors/current";
	protected static final String PACKAGE = "ch.ethz.csg.burundi.BIND_SERVICE";
	protected static final String PROVIDER_URI = "content://ch.ethz.csg.burundi.NeighborProvider/dictionary";

	public class Neighbor{
		public String ipAddress;
		public String id;
		public long time;

		public Neighbor(String ipAddress, String id ){
			this.ipAddress = ipAddress;
			this.id = id;
		}
	}

	ServiceConnection connection = null ;
	Context context;

	public OppComms(Context context, Handler handler) {
		this.context = context;			
		resolver = context.getContentResolver();
		mHandler = handler;
		bindWifiOppService();
		
	}

	public void stop() {
		unbindWifiOppService();	
		
	}

	private void bindWifiOppService (){
		connection = new ServiceConnection(){
			@Override
			public void onServiceDisconnected (ComponentName arg0 ) {
				connection = null ;				
			}
			@Override
			public void onServiceConnected (ComponentName name , IBinder service ){


			}
		};
		
		Intent intent = new Intent (PACKAGE );
		PackageManager packageManager = context.getPackageManager();
		List<ResolveInfo> services = packageManager.queryIntentServices(intent, 0);
		
		if (services.size() > 0) {			
			if (context.bindService(intent, connection , Context.BIND_AUTO_CREATE )) {
				isBinded = true;
				startNeighborUpdates();
				startListeningSocket();
			}
			
		} else 
			Toast.makeText(context, "WlanOpp not installed, please install it first", Toast.LENGTH_LONG).show();
	}

	private void unbindWifiOppService (){
		if ( isBinded ){
			context.unbindService(connection);
			connection = null ;
			stopListeningSocket();
			stopNeighborUpdates();		

		}
	}

	private void startNeighborUpdates(){
		neighborObserver = new ContentObserver(new Handler()){
			@Override
			public void onChange(boolean selfChange){
				super.onChange(selfChange);
				if (System.currentTimeMillis() > (lastUpdate + MIN_UPDATE_INTERVAL ) ) {					
					updateNeighbors();
				}

			}
		};
		updateNeighbors();
		context.getContentResolver().registerContentObserver(Uri.parse(PROVIDER_URI), false, neighborObserver);
	}

	private void stopNeighborUpdates(){
		if (isBinded)
			context.getContentResolver().unregisterContentObserver(neighborObserver);
	}


	public void forceNeighborUpdate() {
		if (isBinded)
			updateNeighbors();
	}

	protected abstract void updateNeighbors();

	protected abstract void startListeningSocket();

	protected abstract void stopListeningSocket();

	protected abstract void write(String data, String ip);

	protected boolean isApEnabled() {
		WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		Method[] wmMethods = wifi.getClass().getDeclaredMethods();
		for(Method method: wmMethods){
			if(method.getName().equals("isWifiApEnabled")) {

				try {
					return (Boolean)method.invoke(wifi);
				} catch (Exception e) {
					Log.e(TAG,"error during reflection",e);

				} 
			}

		}
		return false;
	}
}
