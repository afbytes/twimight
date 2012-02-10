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
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.spongycastle.jce.provider.X509CertificateObject;
import org.spongycastle.openssl.PEMReader;

import ch.ethz.twimight.util.Constants;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

/**
 * Manages the cryptographic key.
 * @author thossmann
 *
 */
public class KeyManager {

	// The names of the fields in shared preferences
	private static final String PPK_EXPONENT = "ppk_exponent";
	private static final String PPK_MODULUS = "ppk_modulus";
	private static final String PK_EXPONENT = "pk_exponent";
	private static final String PK_MODULUS = "pk_modulus";

	private static final String TAG = "KeyManager"; /** Logging */
	SharedPreferences prefs;

	/**
	 * Constructor
	 */
	public KeyManager(Context context){
		prefs = PreferenceManager.getDefaultSharedPreferences(context);
	}


	/**
	 * Returns the valid key pair
	 * @return
	 */
	public KeyPair getKey(){

		// load all the ingredients from shared preferences
		String pkModulusString = prefs.getString(PK_MODULUS, null);
		String pkExponentString = prefs.getString(PK_EXPONENT, null);
		String ppkModulusString = prefs.getString(PPK_MODULUS, null);
		String ppkExponentString = prefs.getString(PPK_EXPONENT, null);

		//return generateKey();

		// if we had a key saved, this should be true. otherwise we will now create one.
		if(pkModulusString != null && pkExponentString != null && ppkModulusString != null && ppkExponentString != null){

			try{

				KeyFactory fact = KeyFactory.getInstance("RSA");
				RSAPublicKeySpec pub = new RSAPublicKeySpec(new BigInteger(pkModulusString), new BigInteger(pkExponentString));
				RSAPublicKey publicKey = (RSAPublicKey) fact.generatePublic(pub);

				RSAPrivateKeySpec priv = new RSAPrivateKeySpec(new BigInteger(ppkModulusString),new BigInteger(ppkExponentString));
				RSAPrivateKey privKey = (RSAPrivateKey) fact.generatePrivate(priv);

				KeyPair kp = new KeyPair(publicKey, privKey);
				return kp;

			} catch(Exception e) {
				Log.e(TAG, "Exception while getting keys!");
			}
		} else {
			KeyPair kp = generateKey();
			return kp;
		}

		return null;

	}

	/**
	 * Creates PEM Format of the encoded (PKCS #8?) public key
	 * TODO: Let BouncyCastle (SpongyCastle) take care of this
	 * @param kp
	 * @return
	 */
	public static String getPemPublicKey(KeyPair kp){
		String encoded = new String(Base64.encode(kp.getPublic().getEncoded(), Base64.DEFAULT));
		encoded = encoded.replace("\n", "");
		StringBuilder builder = new StringBuilder();
		builder.append("-----BEGIN PUBLIC KEY-----");
		builder.append("\n");
		int i = 0;
		while (i < encoded.length()) {
			builder.append(encoded.substring(i,
					Math.min(i + 64, encoded.length())));
			builder.append("\n");
			i += 64;
		}
		builder.append("-----END PUBLIC KEY-----");

		return builder.toString();
	}

	/**
	 * Generate a new public/private key pair
	 */
	public KeyPair generateKey(){

		try{

			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(Constants.SECURITY_KEY_SIZE);
			KeyPair kp = kpg.genKeyPair();	        
			Log.i(TAG,"keys created");

			// now we have to save the newly created keys!
			if(saveKey(kp)){
				return kp;
			} else {
				return null;
			}

		}
		catch(Exception e){
			Log.e(TAG , "Exception while generating keys!");
			return null;
		}
	}

	/**
	 * Save a newly generated Key pair
	 */
	public boolean saveKey(KeyPair kp){

		try{
			KeyFactory fact = KeyFactory.getInstance("RSA");
			RSAPublicKeySpec pub = fact.getKeySpec(kp.getPublic(), RSAPublicKeySpec.class);
			RSAPrivateKeySpec priv = fact.getKeySpec(kp.getPrivate(), RSAPrivateKeySpec.class);


			SharedPreferences.Editor editor = prefs.edit();

			// public key
			editor.putString(PK_MODULUS, pub.getModulus().toString());
			editor.putString(PK_EXPONENT, pub.getPublicExponent().toString());

			// private key
			editor.putString(PPK_MODULUS, priv.getModulus().toString());
			editor.putString(PPK_EXPONENT, priv.getPrivateExponent().toString());

			// finally, commit the changes to shared preferences
			editor.commit();

			Log.i(TAG, "keys saved");
			return true;
		} catch(Exception e) {
			Log.e(TAG, "Exception while saving keys!");
			return false;
		}

	}

	/**
	 * Deletes the current key from the shared preferences
	 */
	public void deleteKey(){

		SharedPreferences.Editor editor = prefs.edit();
		editor.remove(PK_MODULUS);
		editor.remove(PK_EXPONENT);
		editor.remove(PPK_MODULUS);
		editor.remove(PPK_EXPONENT);
		editor.commit();

	}

	/**
	 * Parse key in PEM format
	 */
	public static RSAPublicKey parsePem(String pemString){


		RSAPublicKey pk = null;

		PEMReader pem = new PEMReader(new StringReader(pemString));
		try {
			pk = (RSAPublicKey) pem.readObject();
		} catch (IOException e) {
			Log.e(TAG, "error reading key");
		}

		return pk;
	}

	/**
	 * Computes a signature: RSA Encrypted the hash of the text.
	 * @param text Text to sign
	 * @return String the Base64 encoded string of the signature.
	 */
	public String getSignature(String text){

		String hash = Long.toString(text.hashCode());

		try {			
			Cipher cipher = Cipher.getInstance("RSA");
			KeyPair kp = getKey();			    

			RSAPrivateKey privateKey = (RSAPrivateKey) kp.getPrivate();

			cipher.init(Cipher.ENCRYPT_MODE, privateKey);
			byte[] signature = cipher.doFinal(hash.getBytes());

			String signatureString = Base64.encodeToString(signature, Base64.DEFAULT);
			Log.d(TAG, "Signature: "+signatureString);

			return signatureString;			

		} catch (NoSuchAlgorithmException e) {				
		} catch (NoSuchPaddingException e) {				
		} catch (IllegalBlockSizeException e) {				
		} catch (BadPaddingException e) {				
		} catch (InvalidKeyException e) {				
		} 
		return null;
	}


	/**
	 * Checks if a given signature matches the text, for the public key provided in the certificate object
	 * @param cert
	 * @param signature
	 * @param text
	 * @return
	 */
	public boolean checkSinature(X509CertificateObject cert, String signature, String text) {

		
		String originalHash = Long.toString(text.hashCode());
		  		  
		  try {
			  
				// get the public key from the certificate
			  	RSAPublicKey pk = (RSAPublicKey) cert.getPublicKey();
			  	
				Cipher cipher = Cipher.getInstance("RSA");			    	    
				cipher.init(Cipher.DECRYPT_MODE, pk);
				// we get the signature in base 64 encoding -> decode first
				String decryptedHash = new String(cipher.doFinal(Base64.decode(signature, Base64.DEFAULT)));
				return originalHash.equals(decryptedHash);
				
		  	} catch (NoSuchAlgorithmException e) {			
			} catch (NoSuchPaddingException e) {			
			} catch (InvalidKeyException e) {			
			} catch (IllegalBlockSizeException e) {			
			} catch (BadPaddingException e) {			
			} 
		return false;
	}

}
