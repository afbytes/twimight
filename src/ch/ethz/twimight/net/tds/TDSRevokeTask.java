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
package ch.ethz.twimight.net.tds;

import java.security.GeneralSecurityException;

import org.apache.http.HttpVersion;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.security.CertificateManager;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.EasySSLSocketFactory;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

/**
 * This Thread revokes the current certificate.
 * @author theus
 *
 */
public class TDSRevokeTask extends AsyncTask<Void, Void, String>{

	private static final String TAG = "TDSRevokeThread"; /** For Debugging */
	private Context context;
	private ProgressDialog dialog;

	/**
	 * Constructor
	 */
	public TDSRevokeTask(Context context){
		this.context = context;
	}

	@Override
	protected void onPreExecute() {
	        dialog = new ProgressDialog(context);
	        dialog.setMessage("Please wait while revoking the certificate.");
	        dialog.setIndeterminate(true);
	        dialog.setCancelable(false);
	        dialog.show();
	    }
	
	/**
	 * The task
	 */
	@Override
	protected String doInBackground(Void... params) {
		try{	
			Log.i(TAG, "running");
			if(update()){
				return "Certificate revoked";
			} else {
				return "Error, please try again later!";
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception while running the TDSRevokeThread");
			return "Error, please try again later!";
		} 
	}
	
	@Override
	protected void onPostExecute(String result) {
		dialog.dismiss();
		Toast.makeText(context, result, Toast.LENGTH_LONG).show();
	}

	/**
	 * The communication with the TDS.
	 * @return true if the connection with the TDS was successful, false otherwise.
	 */
	private boolean update(){

		TDSCommunication tds;

		try{
			tds = new TDSCommunication(context, Constants.CONSUMER_ID, LoginActivity.getAccessToken(context), LoginActivity.getAccessTokenSecret(context));
			// TODO: request a certificate for a new key
			CertificateManager cm = new CertificateManager(context);			
			tds.createCertificateObject(null, cm.parsePem(cm.getCertificate()));
		} catch(Exception e) {
			Log.e(TAG, "Exception while assembling request");
			return false;
		}

		boolean success = false;
		// Send the request
		try {
			success = tds.sendRequest(getClient());
		} catch (GeneralSecurityException e) {
			Log.e(TAG, "GeneralSecurityException while sending TDS request");
		}

		if(!success) {
			Log.e(TAG, "Error while sending");
			return false;
		}

		Log.i(TAG, "success");

		try {

			// authentication
			String twitterId = tds.parseAuthentication();
			if(!twitterId.equals(LoginActivity.getTwitterId(context))){
				Log.e(TAG, "Twitter ID mismatch!");
				return false;
			}
			Log.i(TAG, "authentication parsed");


			// certificate
			int status = tds.parseCertificateStatus();
			if(status != 200) {
				return false;
			}
			Log.i(TAG, "certificate parsed");

		} catch(Exception e) {
			Log.e(TAG, "Exception while parsing response");
			return false;
		}

		return true;
	}

	/**
	 * Set up an HTTP client.
	 * @return
	 * @throws GeneralSecurityException
	 */
	private DefaultHttpClient getClient() throws GeneralSecurityException {

		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", new EasySSLSocketFactory(), 443));

		HttpParams params = new BasicHttpParams();
		params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
		params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
		params.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);

		HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
		HttpConnectionParams.setConnectionTimeout(params, Constants.HTTP_CONNECTION_TIMEOUT); // Connection timeout
		HttpConnectionParams.setSoTimeout(params, Constants.HTTP_SOCKET_TIMEOUT); // Socket timeout


		ClientConnectionManager cm = new SingleClientConnManager(params, schemeRegistry);
		return new DefaultHttpClient(cm, params);

	}

};
