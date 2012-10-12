package ch.ethz.twimight.net.opportunistic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class WlanOppService extends Service {

	private static final String TAG = WlanOppService.class.getSimpleName();
	
	private List<Neighbor> neighbors = new ArrayList<Neighbor>();
	private List<Neighbor> new_neighbors = new ArrayList<Neighbor>();
	private ContentObserver neighborObserver;
	private Cursor neighborCursor;
	private ContentResolver resolver;
	private final String[] projection = { "ip", "device_id" };
	private final int PORT = 19761;
	ServerSocket sSocket;
	private Map<String, SendingTask> sendingTasks = new HashMap<String, SendingTask>();
	private Map<String, ReceivingTask> receivingTasks = new HashMap<String, ReceivingTask>();
	
	public class Neighbor{
		public String ipAddress;
		public String id;
	}

	private ServiceConnection connection = null ;

	public void bindWifiOppService (){
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
		bindService(intent, connection , Context.BIND_AUTO_CREATE );
	}

	public void unbindWifiOppService (){
		if ( connection != null ){
			unbindService ( connection );
			connection = null ;
		}
	}
	
	@Override
    public void onCreate() {
		resolver = getContentResolver();
    }
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		bindWifiOppService();
		startNeighborUpdates();
		startListeningSocket();
		
        return START_STICKY;
    }
	
	@Override
    public void onDestroy() {
		
		stopListeningSocket();
		stopNeighborUpdates();
		unbindWifiOppService();
		endOpenConnections();
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

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private void startNeighborUpdates(){
		neighborObserver = new ContentObserver(new Handler()){
			@Override
			public void onChange(boolean selfChange){
				super.onChange(selfChange);
				updateNeighbors();
			}
		};
		updateNeighbors();
		getContentResolver().registerContentObserver(Uri.parse("content://ch.ethz.csg.burundi.NeighborProvider/dictionary"), false, neighborObserver);
	}

	private void stopNeighborUpdates(){
		getContentResolver().unregisterContentObserver(neighborObserver);
	}

	/** Updates the local list of available neighbors **/
	private void updateNeighbors(){
		Log.d(TAG, "Update Neighbors...");
		List<Neighbor> old = neighbors;
		neighbors = new ArrayList<Neighbor>();
		if (neighborCursor == null || neighborCursor.isClosed()){
			neighborCursor = resolver.query(Uri.parse("content://ch.ethz.csg.burundi.NeighborProvider/dictionary"), projection, null, null, null);
		}

		neighborCursor.moveToFirst();
		while (!neighborCursor.isAfterLast()){
			Neighbor temp = new Neighbor();
			temp.ipAddress = neighborCursor.getString(neighborCursor.getColumnIndex("ip"));
			temp.id = neighborCursor.getString(neighborCursor.getColumnIndex("device_id"));
			neighbors.add(temp);
			neighborCursor.moveToNext();
		}
		neighborCursor.close();
		// Check added
		new_neighbors = new ArrayList<Neighbor>();
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
				synchronized(sendingTasks){
					if(!sendingTasks.containsKey(n.ipAddress)){
						Log.d(TAG, "Create Sending Task");
						// start connection with new neighbor
						SendingTask sTask = new SendingTask(n);
						sendingTasks.put(n.ipAddress, sTask);
						sTask.start();
					}
				}
			}
		}
		old = null;
	}
	
	private class SendingTask extends Thread{
		private Neighbor neighbor;
		Socket sock;
		public SendingTask(Neighbor n){
			super();
			neighbor = n;
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
			Log.d(TAG, "Start Sending Task");
			sock = new Socket();
			SocketAddress remoteAddr;
			try {
				Log.d(TAG, "Sending: "+neighbor.ipAddress);
				remoteAddr = new InetSocketAddress(InetAddress.getByName(neighbor.ipAddress),PORT);
				sock.connect(remoteAddr);
				if(sock.isConnected()){
					Log.d(TAG, "is connected");
				}
				PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
				//out.println("START");
				out.println("HELLO");
				//out.println("END");
				sock.close();
				Log.d(TAG, "Done Sending: "+neighbor.ipAddress);
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			synchronized(sendingTasks){
				sendingTasks.remove(neighbor.ipAddress);
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
			Log.d(TAG, "Start Receiving Task...");
			BufferedReader in;
			String ipAddr = sock.getInetAddress().getHostAddress();
			try {
				Log.d(TAG, "Receiving...");
				in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
				String receivedLine;
				while((receivedLine = in.readLine()) != null){
					// Do work here...
					Log.d(TAG, "Received Line: "+receivedLine);
					/*if(receivedLine.equals("END")){
						break;
					}/**/
				}
				in.close();
				sock.close();
				Log.d(TAG, "Done Receiving");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			synchronized(receivingTasks){
				receivingTasks.remove(ipAddr);
			}
		}

	};

}
