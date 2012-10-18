package ch.ethz.twimight.net.opportunistic;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONArray;
import org.json.JSONException;

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

public class WlanOppComms {

	private static final String TAG = WlanOppComms.class.getSimpleName();
	
	private List<Neighbor> neighbors = new CopyOnWriteArrayList<Neighbor>();
	private List<Neighbor> new_neighbors = new CopyOnWriteArrayList<Neighbor>();
	private ContentObserver neighborObserver;
	private Cursor neighborCursor;
	private ContentResolver resolver;
	private final String[] projection = { "ip", "device_id" };
	private final int PORT = 19761;
	ServerSocket sSocket;
	private Map<String, SendingTask> sendingTasks = new HashMap<String, SendingTask>();
	private Map<String, ReceivingTask> receivingTasks = new HashMap<String, ReceivingTask>();
	
    private final Handler mHandler;
    
    public static final long MIN_UPDATE_INTERVAL = 30*1000L; /** Interval FOR UPDATES */
    public static final long MAX_UPDATE_INTERVAL = 60*1000L + MIN_UPDATE_INTERVAL; /** Interval FOR UPDATES */
    private long lastUpdate;
    
	private MacsDBHelper dbHelper;
	
	public class Neighbor{
		public String ipAddress;
		public String id;
		public long time;
	}

	private ServiceConnection connection = null ;
	Context context;
	
	public WlanOppComms(Context context, Handler handler) {
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
		endOpenConnections();
		Log.i(TAG,"exiting stop");
	}
	
	public boolean write(String data, String ip) {		
		
		SendingTask task = null;
		synchronized(sendingTasks) {
			task =  sendingTasks.get(ip);
		}
			if (task != null)
				return task.write(data);
			else
				return false;
		
	}
	
	public void broadcast(String data) {
		synchronized(sendingTasks){
			for(SendingTask sTask : sendingTasks.values()){
				sTask.write(data);
			}
		}
		
		
	}
	
	public int numberOfNeighbors() {
		return sendingTasks.size();
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
	

		
	private void endOpenConnections() {
		synchronized(sendingTasks){
			for(SendingTask sTask : sendingTasks.values()){
				sTask.cancel();
			}
		}
		synchronized(receivingTasks){
			for(ReceivingTask rTask : receivingTasks.values()){
				rTask.cancel();
			}
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
			
			synchronized(sendingTasks){
				if(!sendingTasks.containsKey(temp.ipAddress)){
					Log.d(TAG, "Create Sending Task");
					// start connection with new neighbor
					SendingTask sTask = new SendingTask(temp);
					sendingTasks.put(temp.ipAddress, sTask);
					sTask.start();
					
				}
			}			
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
	
	private class SendingTask extends Thread{
		
		private Neighbor neighbor;
		Socket sock;
		ObjectOutputStream out;
		SocketAddress remoteAddr;
		
		public SendingTask(Neighbor n){			
			neighbor = n;
			sock = new Socket();
			
		}
		
		   /**
         * Write to the connected OutStream.
         * @param buffer  The bytes to write
         */
        public boolean write(String buffer) {
            try {            	
            	
            	synchronized(sock) {
            		Log.i(TAG,"inside synch writing block ");
            		if (sock.isConnected()) {
            			out = new ObjectOutputStream(sock.getOutputStream());
                		// Send it
                        out.writeObject(buffer);                
                        out.flush();
                        //dbHelper.setLastSuccessful(neighbor.ipAddress, new Date());
						// Insert successful connection into DB
					    //dbHelper.updateMacSuccessful(neighbor.ipAddress, 1);
                        // sock.close(); 
                        try {
							JSONArray jarray = new JSONArray(buffer);
							Log.i(TAG, "sent " + jarray.length() + " to neighbor" + neighbor.ipAddress);
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
            		}
            		
            	}                
                synchronized(sendingTasks){
    				sendingTasks.remove(neighbor.ipAddress);
    			}
                return true;
                
            } catch (UnknownHostException e) {
				// TODO Auto-generated catch block
            	Log.e(TAG, "Exception during write", e);     			
				return false;
				
			} catch (IOException e) {
                Log.e(TAG, "Exception during write", e);               
                return false;
            }            
            
        }

		
		public void cancel() {
			synchronized(sock) {
				if(sock !=null && !sock.isClosed()){
					try {
						sock.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} 
			
		}

		@Override
		public void run(){
			
				try {
					synchronized(sock) {
						remoteAddr = new InetSocketAddress(InetAddress.getByName(neighbor.ipAddress),PORT);	
						Log.i(TAG,"connection attempt to remote ip: " + neighbor.ipAddress);
						sock.connect(remoteAddr);						
						Log.i(TAG,"connection attempt to remote ip: " + neighbor.ipAddress + " succeded");
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG,"connection attempt to remote ip: " + neighbor.ipAddress + " failed");
				}
			
			
			
		}

	};
	
	
	
	private void startListeningSocket(){
		new ListeningTask().start();
	}
	
	private void stopListeningSocket(){
		if(sSocket !=null){
			try {
				sSocket.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	private class ListeningTask extends Thread{
		
		@Override
		public void run(){
			try {
				sSocket = new ServerSocket(PORT);
				while (true){
					Log.d(TAG, "Listening...");
					Socket sock = sSocket.accept();
					String ipAddr = sock.getInetAddress().getHostAddress();
					Log.d(TAG, "Got socket: "+ipAddr);
					synchronized(receivingTasks){
						if(!receivingTasks.containsKey(ipAddr)){
							Log.d(TAG, "Create Receiving Task");
							ReceivingTask rTask = new ReceivingTask(sock);
							receivingTasks.put(ipAddr, rTask);
							rTask.start();
						}
					}
				}
			} catch (IOException e) {
				Log.d(TAG, "Socket was closed or some other error...");
			}
		}
	};
	
	private class ReceivingTask extends Thread{
		
		private Socket sock;
		public ReceivingTask(Socket s){
			super();
			sock = s;
		}
		
		public void cancel() {
			if(sock !=null && !sock.isClosed()){
				try {
					sock.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		@Override
		public void run(){
					
			String ipAddr = sock.getInetAddress().getHostAddress();
			try {
				
				Log.i(TAG, "Receiving...");
				ObjectInputStream in = new ObjectInputStream(sock.getInputStream());
                Object buffer;                
                buffer = in.readObject();
                // Send the obtained bytes to the UI Activity
                mHandler.obtainMessage(Constants.MESSAGE_READ, -1, -1, buffer)
                .sendToTarget();				
				in.close();
				sock.close();
				Log.i(TAG, "Done Receiving");			
				
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Log.e(TAG,"exception receiving: ",e);
				e.printStackTrace();
			}
			synchronized(receivingTasks){
				receivingTasks.remove(ipAddr);
			}
			
		}

	};

}
