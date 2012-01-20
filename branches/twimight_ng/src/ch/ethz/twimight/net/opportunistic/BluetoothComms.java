/*******************************************************************************
 * Copyright (c) 2011 ETH Zurich.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Paolo Carta - Implementation
 *     Theus Hossmann - Implementation
 *     Dominik Schatzmann - Message specification
 ******************************************************************************/
/*
 /*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.ethz.twimight.net.opportunistic;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.UUID;

import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.LogFilesOperations;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class BluetoothComms {
	// Debugging
	private static final String TAG = "BluetoothComms";
	private static final boolean D = true;

	// Name for the SDP record when creating server socket  
	private static final String NAME_INSECURE = "BluetoothInsecure";

	// Unique UUID for this application   
	private static final UUID MY_UUID_INSECURE =
		UUID.fromString("54a0d176-f32b-4f86-81bc-657c0e9f45e4");    	

	// Member fields
	private static BluetoothComms instance = null;

	private final BluetoothAdapter mAdapter;
	private final Handler mHandler;
	private AcceptThread mInsecureAcceptThread;
	private ConnectThread mConnectThread;
	private ConnectedThread mConnectedThread;
	private int mState;

	ObjectOutputStream out;
	private BluetoothSocket mmSocket;   
	boolean isAccepted;
	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0;       // we're doing nothing
	public static final int STATE_LISTEN = 1;     // now listening for incoming connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
	public static final int STATE_CONNECTED = 3;  // now connected to a remote device

	LogFilesOperations logOps;
	FileWriter bluetoothLogWriter; 

	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 * @param context  The UI Activity Context
	 * @param handler  A Handler to send messages back to the UI Activity
	 */
	public BluetoothComms(Context context, Handler handler) {
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;

		instance = this;
		
		logOps = new LogFilesOperations();
		logOps.createLogsFolder();
		bluetoothLogWriter = logOps.createLogFile("ConnectTimes");


	}  

	public static BluetoothComms getInstance(){

		return instance;

	}
	
	public String getMac(){
		return mAdapter.getAddress();
	
	}
	/**
	 * Set the current state of the chat connection
	 * @param state  An integer defining the current connection state
	 */
	private synchronized void setState(int state) {
		if (D) Log.d(TAG, "setState() " + mState + " -> " + state);
		mState = state;        
	}

	/**
	 * Return the current connection state. */
	public synchronized int getState() {
		return mState;
	}


	/**
	 * Start the chat service. Specifically start AcceptThread to begin a
	 * session in listening (server) mode.  */
	public synchronized void start() {
		if (D) Log.d(TAG, "start");			        					        		    	

		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

		setState(STATE_LISTEN);   
		// Start the thread to listen on a BluetoothServerSocket

		if (mInsecureAcceptThread == null) {
			mInsecureAcceptThread = new AcceptThread();
			mInsecureAcceptThread.setPriority(Thread.MAX_PRIORITY);
			mInsecureAcceptThread.start();
		}
	}

	public void cancelConnectionAttempt() {
		try {
			mmSocket.close();
		} catch (Exception e) {
			Log.e(TAG, "close() of connect  socket failed", e);
		}
	}
	/**
	 * Start the ConnectThread to initiate a connection to a remote device.    
	 */
	public synchronized void connect(String mac) {

		// Which device??
		BluetoothDevice device = mAdapter.getRemoteDevice(mac);

		if (D) Log.d(TAG, "connect to: " + device);
		// Cancel any thread attempting to make a connection
		if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}       

		// Cancel any thread currently running a connection
		if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

		// Start the thread to connect with the given device
		if (mConnectedThread == null) {
			mConnectThread = new ConnectThread(device);        
			mConnectThread.start();         
			setState(STATE_CONNECTING); 
		}
	}

	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 * @param socket  The BluetoothSocket on which the connection was made
	 * @param device  The BluetoothDevice that has been connected
	 */
	public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
		Log.i(TAG, "connected");        
		// Cancel the thread that completed the connection
		if (mConnectThread != null) {
			mConnectThread.cancel();
			mConnectThread = null;
		}        
		// Cancel the accept thread         
		if (mInsecureAcceptThread != null) {
			mInsecureAcceptThread.cancel();
			mInsecureAcceptThread = null;
		} 

		// Start the thread to manage the connection and perform transmissions
		if (mState != STATE_CONNECTED ) {
			setState(STATE_CONNECTED); 

			mConnectedThread = new ConnectedThread(socket);       		
			mConnectedThread.start();
			
			// Send a success message back to the Activity
			Message msg = mHandler.obtainMessage(Constants.MESSAGE_CONNECTION_SUCCEEDED, -1, -1, device.getAddress());
			mHandler.sendMessage(msg);


		} else
			try {
				socket.close();
			} catch (IOException e) {
				Log.e(TAG,"closing socket inside connected since state == CONNECTED", e);
			}
	}

	/**
	 * Stop all threads
	 */
	public synchronized void stop() {
		if (D) Log.d(TAG, "stop");

		if (mConnectThread != null) {
			if (mState != STATE_CONNECTING) {
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}
		if (mConnectedThread != null) {
			mConnectedThread.cancel();
			mConnectedThread = null;
		}  
		if (mInsecureAcceptThread != null) {
			mInsecureAcceptThread.cancel();
			mInsecureAcceptThread = null;
		}
		setState(STATE_NONE);
	}

	/**
	 * Write to the ConnectedThread in an unsynchronized manner
	 * @param out The bytes to write
	 * @see ConnectedThread#write(byte[])
	 */
	public void write(String out) {
		// Create temporary object
		ConnectedThread r;
		// Synchronize a copy of the ConnectedThread
		synchronized (this) {
			if (mState != STATE_CONNECTED) return;
			r = mConnectedThread;
		}
		// Perform the write unsynchronized
		r.write(out);
	}

	private void connectionFailed(String mac) {
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(Constants.MESSAGE_CONNECTION_FAILED, -1 ,-1, mac);
		mHandler.sendMessage(msg);
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost() {            
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(Constants.MESSAGE_CONNECTION_LOST);
		mHandler.sendMessage(msg);

	}

	/**
	 * This thread runs while listening for incoming connections. It behaves
	 * like a server-side client. It runs until a connection is accepted
	 * (or until cancelled).
	 */
	private class AcceptThread extends Thread {
		// The local server socket
		private final BluetoothServerSocket mmServerSocket;
		volatile BluetoothSocket acceptSocket = null;

		public AcceptThread() {
			BluetoothServerSocket tmp = null;            

			// Create a new listening server socket
			
				    // from API level 10 we have an API call for insecure RFCOMM connections
					try {
						tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
					} catch (IOException e) {
						Log.e(TAG, "Unable to listen for Insecure RFCOMM connection");
					}				
				                
			mmServerSocket = tmp;
		}

		public void run() {
			if (D) Log.d(TAG, "BEGIN mAcceptThread" + this);
			setName("AcceptThread" );           
			if (mmServerSocket != null) {
				// Listen to the server socket if we're not connected
				while (mState != STATE_CONNECTED) {
					try {
						// This is a blocking call and will only return on a
						// successful connection or an exception                	
						acceptSocket = mmServerSocket.accept();                   
						cancel();                                   
						Log.i(TAG,"accepted incoming connection");


					} catch (IOException e) {
						// Log.e(TAG, "accept() failed", e);
						break;
					}
					/* catch (NullPointerException e) {
                	Log.e(TAG, "accept() failed: restarting", e);                    
                    synchronized (this) {
                    	stop();
                    	start();                    	
                    }
                    break; 
                }*/

					// If a connection was accepted
					if (acceptSocket != null) {
						synchronized (this) {                    	
							switch (mState) {
							case STATE_LISTEN:
							case STATE_CONNECTING:
								// Situation normal. Start the connected thread.
								connected(acceptSocket, acceptSocket.getRemoteDevice());
								break;
							case STATE_NONE:
							case STATE_CONNECTED:
								// Either not ready or already connected. Terminate new socket.
								try {
									acceptSocket.close();
								} catch (Exception e) {
									Log.e(TAG, "Could not close unwanted socket", e);
								}
								break;
							}
						}
					}
				}
			}
			if (D) Log.i(TAG, "END mAcceptThread " );
		}

		public void cancel() {
			if (D) Log.d(TAG, "cancel " + this);
			try {
				if (mmServerSocket !=  null)
					mmServerSocket.close();               
			} catch (IOException e) {
				Log.e(TAG, "close() of server failed", e);
			}
		}
	}


	/**
	 * This thread runs while attempting to make an outgoing connection
	 * with a device. It runs straight through; the connection either
	 * succeeds or fails.
	 */
	private class ConnectThread extends Thread {

		private final BluetoothDevice mmDevice;
		
		private Date start = null;
		


		public ConnectThread(BluetoothDevice device) {
			
			// Start timer
			start = new Date();

			mmDevice = device;
			BluetoothSocket tmp = null;
			// Get a BluetoothSocket for a connection with the
			// given BluetoothDevice			
			try { 					
				 // from API level 10 we have an API call for insecure RFCOMM connections
				tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
				                
			} catch (IOException e) {
				Log.e(TAG,  "create() failed", e);
			}
			mmSocket = tmp;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectThread ");
			setName("ConnectThread");

			// Always cancel discovery because it will slow down a connection
			mAdapter.cancelDiscovery();
			// Make a connection to the BluetoothSocket
			try {
				// This is a blocking call and will only return on a
				// successful connection or an exception 
				mmSocket.connect();
				
				Date end = new Date();
				try {
					bluetoothLogWriter.write(mmDevice.getAddress() + ": (succeeded) " + (long)(end.getTime()-start.getTime()) + "\n");
					bluetoothLogWriter.flush();
				} catch (IOException e1) {
					Log.e(TAG, "Log error");
				}

				

				Log.i(TAG,"connection attempt succeded");
			} catch (IOException e) {            	
				//Log.e(TAG, "connection attempt failed", e);
				
				Date end = new Date();
				try {
					bluetoothLogWriter.write(mmDevice.getAddress() + ": (failed) " + (long)(end.getTime()-start.getTime()) +"\n");
					bluetoothLogWriter.flush();
				} catch (IOException e1) {
					Log.e(TAG, "Log error");
				}
				try { 
					Thread.yield();
					mmSocket.close();                    
				} catch (Exception e2) {
					Log.e(TAG, "unable to close()  socket during connection failure", e2);
				}
				connectionFailed(mmDevice.getAddress());
				return;
			}

			// Reset the ConnectThread because we're done
			mConnectThread = null;
			
			// Start the connected thread
			connected(mmSocket, mmDevice);
		}

		public void cancel() {
			try {
				mmSocket.close();
			} catch (Exception e) {
				Log.e(TAG, "close() of connect  socket failed", e);
			}
		}
	}

	/**
	 * This thread runs during a connection with a remote device.
	 * It handles all incoming and outgoing transmissions.
	 */
	private class ConnectedThread extends Thread {
		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			Log.d(TAG, "create ConnectedThread");
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;


			// Get the BluetoothSocket input and output streams
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();                
			} catch (IOException e) {
				Log.e(TAG, "temp sockets not created", e);
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			Log.i(TAG, "BEGIN mConnectedThread");          

			// Keep listening to the InputStream while connected
			while (true) {
				try {
					// Read from the InputStream                   
					ObjectInputStream in = new ObjectInputStream(mmInStream);
					Object buffer;
					try {						
						buffer = in.readObject();
						// Send the obtained bytes to the UI Activity
						mHandler.obtainMessage(Constants.MESSAGE_READ, -1, -1, buffer)
						.sendToTarget();
					} catch (Exception e) {	}                   

				} catch (Exception e) {
					Log.e(TAG, "disconnected", e);                     
					connectionLost();                                  

					break;
				}
			}
		}

		/**
		 * Write to the connected OutStream.
		 * @param buffer  The bytes to write
		 */
		public synchronized void write(String buffer) {
			try {                  
				out = new ObjectOutputStream(mmOutStream);
				// Send it
				out.writeObject(buffer);                
				out.flush();     

			} catch (Exception e) {
				Log.e(TAG, "Exception during write", e);
				connectionLost();
			}
		}

		public void cancel() {
			try {

				mmSocket.close();                 
			} catch (Exception e) {
				Log.e(TAG, "close() of connect socket failed", e);
			}
		}
	}
}
