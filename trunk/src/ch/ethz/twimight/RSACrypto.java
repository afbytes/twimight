package ch.ethz.twimight;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import android.content.SharedPreferences;
import android.util.Log;
import ch.ethz.twimight.packets.SignedTweet;


public class RSACrypto {
	private static final String TAG = "RSACrypto";	
	SharedPreferences mSettings;
		
	public RSACrypto(SharedPreferences mSettings) {
		this.mSettings = mSettings;
	}
	
	public void createKeys() {
		
	    RSAPrivateKey privateKey;
	    RSAPublicKey publicKey;
	    
		 try{
			    
		        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
		        kpg.initialize(1024);
		        KeyPair kp = kpg.genKeyPair();
		       // privateKey = (RSAPrivateKey)kp.getPrivate();
		       // publicKey = (RSAPublicKey)kp.getPublic();
		        KeyFactory fact = KeyFactory.getInstance("RSA");
		        RSAPublicKeySpec pub = fact.getKeySpec(kp.getPublic(), RSAPublicKeySpec.class);
		        RSAPrivateKeySpec priv = fact.getKeySpec(kp.getPrivate(), RSAPrivateKeySpec.class);
		        
		        saveToSharedPrefs("private", priv.getModulus(), priv.getPrivateExponent());
		        saveToSharedPrefs("public", pub.getModulus(), pub.getPublicExponent());	
		        
		        Log.i(TAG,"keys created");
		    }
		  catch(Exception e){
		        System.out.println(e.getMessage());
		    }

	 }
	
	 public RSAPublicKey encodePublicKey(String modulus, String exponent) {
		 try {
		    BigInteger mod = new BigInteger(modulus);
			BigInteger exp = new BigInteger(exponent);
								
			KeyFactory fact = KeyFactory.getInstance("RSA");
			RSAPublicKeySpec pub = new RSAPublicKeySpec(mod,exp);
		    RSAPublicKey publicKey = (RSAPublicKey) fact.generatePublic(pub);
		    return publicKey;
		 } catch (Exception ex) {
			 return null;
		 }
	 }
	 
	 public RSAPrivateKey encodePrivateKey(String modulus, String exponent) {
		 try {
		    BigInteger mod = new BigInteger(modulus);
			BigInteger exp = new BigInteger(exponent);
								
			KeyFactory fact = KeyFactory.getInstance("RSA");
			RSAPrivateKeySpec priv = new RSAPrivateKeySpec(mod,exp);
		    RSAPrivateKey privKey = (RSAPrivateKey) fact.generatePrivate(priv);
		    return privKey;
		 } catch (Exception ex) {
			 return null;
		 }
	 }

	 public byte[] encypt(String message, String modulus, String exponent ) {		 
		 try {
			
			Cipher cipher = Cipher.getInstance("RSA");			
		    RSAPublicKey publicKey = encodePublicKey(modulus,exponent);		    
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
			byte[] cipherData = cipher.doFinal(message.getBytes());
			return cipherData;
			
		} catch (NoSuchAlgorithmException e) {			
		} catch (NoSuchPaddingException e) {			
		} catch (InvalidKeyException e) {			
		} catch (IllegalBlockSizeException e) {			
		} catch (BadPaddingException e) {			
		} 
		return null;

	 }
	  
	  public String decrypt(byte[] cipherData) {
		 
		  try {			
			    Cipher cipher = Cipher.getInstance("RSA");
			    
			    String mod = mSettings.getString("modulus_private", null);
			    String exp = mSettings.getString("exponent_private", null);			    
			   
			    RSAPrivateKey privateKey = encodePrivateKey(mod,exp);
			    
			    cipher.init(Cipher.DECRYPT_MODE, privateKey);
				byte[] decryptedData = cipher.doFinal(cipherData);
				String decrypted = new String(decryptedData);
				return decrypted;				
				
			} catch (NoSuchAlgorithmException e) {				
			} catch (NoSuchPaddingException e) {				
			} catch (IllegalBlockSizeException e) {				
			} catch (BadPaddingException e) {				
			} catch (InvalidKeyException e) {				
			} 
			return null;
		    
	  }
	  
	  public byte[] sign(SignedTweet tweet) {
		  
		  String hash = Long.toString(tweet.hashCode());
		  
		  try {			
			    Cipher cipher = Cipher.getInstance("RSA");
			    
			    String mod = mSettings.getString("modulus_private", null);
			    String exp = mSettings.getString("exponent_private", null);			    
			   
			    RSAPrivateKey privateKey = encodePrivateKey(mod,exp);
			    
			    cipher.init(Cipher.ENCRYPT_MODE, privateKey);
				byte[] signature = cipher.doFinal(hash.getBytes());
				
				return signature;			
				
			} catch (NoSuchAlgorithmException e) {				
			} catch (NoSuchPaddingException e) {				
			} catch (IllegalBlockSizeException e) {				
			} catch (BadPaddingException e) {				
			} catch (InvalidKeyException e) {				
			} 
			return null;
	  }
	  
	  public boolean verifySignature(SignedTweet tweet) {
		  
		  long hash = tweet.hashCode();
		  
		  try {				
				Cipher cipher = Cipher.getInstance("RSA");			
			    	    
				cipher.init(Cipher.DECRYPT_MODE, tweet.publicKey);
				if (tweet != null) {					
					byte[] decryptedHash = cipher.doFinal(tweet.signature);
					String originalHash = new String(decryptedHash);
					
					if (Long.parseLong(originalHash) == hash) {
						
						return true;
					}								
				}								
			} catch (NoSuchAlgorithmException e) {			
			} catch (NoSuchPaddingException e) {			
			} catch (InvalidKeyException e) {			
			} catch (IllegalBlockSizeException e) {			
			} catch (BadPaddingException e) {			
			} 
		  return false;
		  
	  }
	 
	 private void saveToSharedPrefs(String name, BigInteger modulus, BigInteger exponent) {		
		 SharedPreferences.Editor editor = mSettings.edit();		
		 editor.putString("modulus_" + name, modulus.toString());
		 editor.putString("exponent_" + name, exponent.toString());
		 editor.putLong("generated_at", new Date().getTime());
		 editor.commit();
	 } 
	 
	

}
