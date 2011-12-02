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

package ch.ethz.twimight.security;


import java.io.IOException;
import java.io.StringReader;
import java.security.Principal;
import java.security.Security;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.util.Date;

import org.spongycastle.jce.provider.X509CertificateObject;
import org.spongycastle.openssl.PEMReader;

import ch.ethz.twimight.activities.LoginActivity;
import ch.ethz.twimight.util.Constants;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * Manages the TDS issued certificates.
 * @author thossmann
 *
 */
public class CertificateManager {

	private static final String X509_CERTIFICATE_PEM = "X509CertificatePEM"; /** The name of the shared preference */

	private static final String TAG = "CertificateManager"; /** For logging */
	
	private Context context;
	
	static {
	    Security.addProvider(new org.spongycastle.jce.provider.BouncyCastleProvider());
	}
	
	/**
	 * Constructor
	 */
	public CertificateManager(Context context){
		this.context = context;
	}
	
	/**
	 * Parse certificate in PEM format
	 */
	public X509CertificateObject parsePem(String pemString){
		
		X509CertificateObject cert = null;
		
		PEMReader pem = new PEMReader(new StringReader(pemString));
		try {
			cert = (X509CertificateObject) pem.readObject();
		} catch (IOException e) {
			Log.e(TAG, "error reading certificate");
		}
		
		//Log.i(TAG, "expires: " + cert.getNotAfter().toString());
		
		return cert;
	}
	
	/**
	 * We need a new certificate, T days before the current one expires
	 */
	public boolean needNewCertificate(){
		return !hasCertificateAtDate(new Date(System.currentTimeMillis() + Constants.DISASTER_DURATION));
	}
	
	/**
	 * Do we have a currently valid certificate?
	 */
	public boolean hasCertificate(){
		return hasCertificateAtDate(new Date());
	}
	
	/**
	 * Loads the current certificate
	 * @return 
	 */
	public String getCertificate(){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		return prefs.getString(X509_CERTIFICATE_PEM, null);
	}
	
	/**
	 * Returns the serial number of the current certificate
	 * @return
	 */
	public String getSerial() {
		X509CertificateObject cert = parsePem(getCertificate());
		if(cert!=null)
			return Long.toHexString(cert.getSerialNumber().longValue());
		else
			return null;
	}
	
	/**
	 * Do we have a valid certificate?
	 * @return
	 */
	public void setCertificate(String pemString){
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.putString(X509_CERTIFICATE_PEM, pemString);
		prefEditor.commit();

	}
	
	/**
	 * Returns true if we have a valid certificate, 
	 */
	private boolean hasCertificateAtDate(Date date){
		
		String pemString = getCertificate();
		
		if(pemString == null) return false;
		
		X509CertificateObject cert = parsePem(pemString);
		
		if(cert == null) return false;
		
		try {
			cert.checkValidity(date);
		} catch (CertificateExpiredException e) {
			return false;
		} catch (CertificateNotYetValidException e) {
			return false;
		}
		
		
		return true;
	}
	
	/**
	 * Checks if the certificate is valid for the provided twitterId
	 * @param cert X509CertificateObject
	 * @param twitterId String user ID
	 * @return true if valid, false otherwise
	 */
	public boolean checkCertificate(X509CertificateObject cert, String twitterId){
		// is it valid?
		try {
			cert.checkValidity();
		} catch (CertificateExpiredException e) {
			Log.e(TAG, "certificate already expired!");
			return false;
		} catch (CertificateNotYetValidException e) {
			Log.e(TAG, "certificate not yet valid");
			return false;
		}
		
		// is it ours?
		Principal subjectDN = cert.getSubjectDN();
		if(!subjectDN.getName().substring(2).equals(twitterId)){
			Log.e(TAG, "wrong DN in certificate! " + subjectDN.getName());
			return false;
		}
			
		// is the public key correct?
		KeyManager km = new KeyManager(context);
		if(!cert.getPublicKey().equals(km.getKey().getPublic())){
			Log.e(TAG, "wrong public key in certificate!");
			return false;
		}
		
		// check the signature
		// TODO
		
		// is it on the revocation list?
		// TODO
		return true;
	}

	/**
	 * Checks if a given certificate is valid for our own Twitter ID
	 * @param cert
	 * @return
	 */
	public boolean checkCertificate(X509CertificateObject cert) {

		return checkCertificate(cert, LoginActivity.getTwitterId(context));
	}

	/**
	 * Deletes the PEM string of the current certificate
	 */
	public void deleteCertificate() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		SharedPreferences.Editor prefEditor = prefs.edit();
		prefEditor.remove(X509_CERTIFICATE_PEM);
		prefEditor.commit();

	}
	
}
