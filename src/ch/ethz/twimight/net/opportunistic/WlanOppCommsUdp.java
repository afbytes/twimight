package ch.ethz.twimight.net.opportunistic;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import ch.ethz.twimight.data.MacsDBHelper;
import ch.ethz.twimight.util.Constants;

public class WlanOppCommsUdp {
		
	    private static final String TAG = WlanOppComms.class.getSimpleName();
	
        public static final long MIN_UPDATE_INTERVAL = 30*1000L; /** Interval FOR UPDATES */
	    public static final long MAX_UPDATE_INTERVAL = 60*1000L + MIN_UPDATE_INTERVAL; /** Interval FOR UPDATES */
	    private long lastUpdate;
	    
	    private List<Neighbor> neighbors = new CopyOnWriteArrayList<Neighbor>();
		private List<Neighbor> new_neighbors = new CopyOnWriteArrayList<Neighbor>();
		private ContentObserver neighborObserver;
		private Cursor neighborCursor;
		private ContentResolver resolver;
		private final String[] projection = { "ip", "device_id" };
		private final int PORT = 19761;
		private final Handler mHandler;
		
		ListeningTask lTask = null;
	    
		private MacsDBHelper dbHelper;
		
		public class Neighbor{
			public String ipAddress;
			public String id;
			public long time;
		}

		private ServiceConnection connection = null ;
		Context context;
		
		public WlanOppCommsUdp(Context context, Handler handler) {
			this.context = context;
			bindWifiOppService();
			dbHelper = MacsDBHelper.getInstance(context);
			dbHelper.open();		
			resolver = context.getContentResolver();
			mHandler = handler;
			startNeighborUpdates();
			startListeningSocket();			
			
		}
		
		public void stop() {
			Log.i(TAG,"inside stop");
			stopListeningSocket();
			stopNeighborUpdates();
			unbindWifiOppService();			
			Log.i(TAG,"exiting stop");
		}
		
		private void bindWifiOppService (){
			connection = new ServiceConnection(){
				@Override
				public void onServiceDisconnected (ComponentName arg0 ) {
					connection = null ;
				}
				@Override
				public void onServiceConnected (ComponentName arg0 , IBinder arg1 ){
					// do nothing
				}
			};
			Intent intent = new Intent ("ch.ethz.csg.burundi.BIND_SERVICE" );
			context.bindService(intent, connection , Context.BIND_AUTO_CREATE );
		}

		private void unbindWifiOppService (){
			if ( connection != null ){
				context.unbindService ( connection );
				connection = null ;
			}
		}
		
		public void write(String data, String ip) {		
			Log.i(TAG,"inside write");
			new SendingTask(ip,data).start();
			
			
		}
		
		public void broadcast(String data) {
			
			for (Neighbor n : neighbors) {
				new SendingTask(n.ipAddress,data).start();
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
			context.getContentResolver().registerContentObserver(Uri.parse("content://ch.ethz.csg.burundi.NeighborProvider/dictionary"), false, neighborObserver);
		}

		private void stopNeighborUpdates(){
			context.getContentResolver().unregisterContentObserver(neighborObserver);
		}
		
		public void forceNeighborUpdate() {
			Log.i(TAG,"forcing neighbor update");
			updateNeighbors();
		}

		/** Updates the local list of available neighbors **/
		private void updateNeighbors(){
			Log.i(TAG, "Update Neighbors...");
			lastUpdate = System.currentTimeMillis();
			//List<Neighbor> old = neighbors;
			neighbors = new CopyOnWriteArrayList<Neighbor>();
			if (neighborCursor == null || neighborCursor.isClosed()){
				neighborCursor = resolver.query(Uri.parse("content://ch.ethz.csg.burundi.NeighborProvider/dictionary"), projection, null, null, null);
			}

			neighborCursor.moveToFirst();
			while (!neighborCursor.isAfterLast()){
				Neighbor temp = new Neighbor();
				temp.ipAddress = neighborCursor.getString(neighborCursor.getColumnIndex("ip"));
				temp.id = neighborCursor.getString(neighborCursor.getColumnIndex("device_id"));
				temp.time = System.currentTimeMillis();
				neighbors.add(temp);							
				neighborCursor.moveToNext();
			}
			
			neighborCursor.close();
			
			// Check added
			/*
			new_neighbors = new CopyOnWriteArrayList<Neighbor>();
			for(Neighbor n : neighbors){
				boolean found = false;
				for(Neighbor o : old){
					if(o.ipAddress.equals(n.ipAddress) && o.id.equals(n.id)){
						found = true;
						break;
					}
				}
				if(!found){
					// new neighbor n
					Log.d(TAG, "Found Neighbor: "+n.ipAddress);
					new_neighbors.add(n);
					
				}
			}
			*/
			mHandler.obtainMessage(Constants.MESSAGE_NEW_NEIGHBORS, -1, -1, neighbors)
	        .sendToTarget();	
			//old = null;
			
		}
		
		private void startListeningSocket(){
			lTask = new ListeningTask();
			lTask.start();
		}
		
		private void stopListeningSocket(){
			lTask.cancel();
			lTask = null;
		}
		
		
		private class ListeningTask extends Thread{
			
			DatagramSocket ds = null;
			
			public synchronized void cancel() {
				if(ds != null) {
					ds.close();
					ds = null;
				}					
			}			
			
			@Override
			public synchronized void run(){
				
				byte[] msg = new byte[65535];
			    DatagramPacket dp = new DatagramPacket(msg, msg.length);   
			    
			    try {
			    	
			    	ds = new DatagramSocket(PORT);
			    	while(ds != null) {			    		    
					        //disable timeout for testing
					        //ds.setSoTimeout(100000);
					        ds.receive(dp);					        
					        String data = new String(dp.getData(), 0, dp.getLength());
					        mHandler.obtainMessage(Constants.MESSAGE_READ, -1, -1, data).sendToTarget();	
					        
					        Log.i(TAG,"UDP packet received");
			    	}		      
			        
			    } catch (SocketException e) {
			    	 Log.e(TAG,"SocketException",e);

			    } catch (IOException e) {
			    	 Log.e(TAG,"IOException",e);

			    } finally {
			        if (ds != null) {
			            ds.close();
			        }
			    }				
			}
		};	
		
	private class SendingTask extends Thread {
		
		InetAddress serverAddr;
		String data;
		
		public SendingTask(String ip, String data) {
			try {
				serverAddr = InetAddress.getByName(ip);
				this.data = data;
			} catch (UnknownHostException e) {
				Log.e(TAG,"UnknownHostException",e);
			}
			
		}

		@Override
		public void run() {		
			
		    DatagramSocket ds = null;
		    
		    if(serverAddr != null ) {		    	
		    	try {
			        ds = new DatagramSocket();		        
			        DatagramPacket dp;
			        dp = new DatagramPacket(data.getBytes(), data.length(), serverAddr, PORT);
			        Log.i(TAG, "# bytes: " + data.length());
			        ds.send(dp);

			    } catch (SocketException e) {
			        Log.e(TAG,"SocketException",e);
			    } catch (IOException e) {
			    	Log.e(TAG,"IOException",e);
			    }  finally {
			        if (ds != null) 
			            ds.close();
			        
			    }
		    }	    	
			
		}
		
		
		
	}	
	    
		

}
