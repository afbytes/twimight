package ch.ethz.twimight.net.opportunistic;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import ch.ethz.twimight.util.Constants;

public class WlanOppCommsUdp extends OppComms {    
		
		ListeningTask lTask = null;		
		
		public WlanOppCommsUdp(Context context, Handler handler) {
			super(context,handler);
		}
		
		public void write(String data, String ip) {		
			if (isBinded )	
				new SendingTask(ip,data).start();			
			
		}

		public void broadcast(String data) {
			if (isBinded )
				for (Neighbor n : neighbors) {
					new SendingTask(n.ipAddress,data).start();
				}
		}		


		/** Updates the local list of available neighbors **/
		protected void updateNeighbors(){
			
			lastUpdate = System.currentTimeMillis();
			//List<Neighbor> old = neighbors;
			neighbors = new CopyOnWriteArrayList<Neighbor>();
			if (neighborCursor == null || neighborCursor.isClosed()){
				neighborCursor = resolver.query(Uri.parse(PROVIDER_URI), projection, null, null, null);
			}
			
			if (neighborCursor == null || neighborCursor.isClosed()){
				neighborCursor.moveToFirst();
				while (!neighborCursor.isAfterLast()){				
					String ipAddress = neighborCursor.getString(neighborCursor.getColumnIndex("ip"));
					String id = neighborCursor.getString(neighborCursor.getColumnIndex("device_id"));
					Neighbor temp = new Neighbor(ipAddress,id);
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
			
			
		}
		
		protected void startListeningSocket(){
			lTask = new ListeningTask();
			lTask.start();
		}
		
		protected void stopListeningSocket(){
			if (lTask != null && isBinded ) {
				lTask.cancel();
				lTask = null;
			}
			
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
						if (isApEnabled()) {
							Log.i(TAG,"wifi AP enabled");
							ArrayList<Neighbor> temp = new ArrayList<Neighbor>();
							Neighbor neigh = new Neighbor(dp.getAddress().getHostAddress(),null);
							temp.add(neigh);
							mHandler.obtainMessage(Constants.MESSAGE_NEW_NEIGHBORS, -1, -1,temp )
							.sendToTarget();
						}
						mHandler.obtainMessage(Constants.MESSAGE_READ, -1, -1, data).sendToTarget();	

						Log.i(TAG,"UDP packet received");
					}		      

				} catch (SocketException e) {
					startListeningSocket();

				} catch (IOException e) {
					Log.e(TAG,"IOException",e);
					startListeningSocket();

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
