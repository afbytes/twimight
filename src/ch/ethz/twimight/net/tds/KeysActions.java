package ch.ethz.twimight.net.tds;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class KeysActions {

	private static final String TAG = "KeysActions";
	
	public boolean postKey(String modulus, String exponent, String username, String  id) {
	
		try {
	        HttpClient client = new DefaultHttpClient();  
	        String postURL = "http://test.php";
	        HttpPost post = new HttpPost(postURL);
	        
	            List<NameValuePair> params = new ArrayList<NameValuePair>();
	            params.add(new BasicNameValuePair("modulus",modulus ));
	            params.add(new BasicNameValuePair("exponent",exponent ));
	            params.add(new BasicNameValuePair("username", username ));
	            params.add(new BasicNameValuePair("id", id));
	           // UrlEncodedFormEntity ent = new UrlEncodedFormEntity(params,HTTP.UTF_8);
	            UrlEncodedFormEntity ent = new UrlEncodedFormEntity(params);
	            post.setEntity(ent);
	            
	            HttpResponse responsePOST = client.execute(post);  
	            HttpEntity resEntity = responsePOST.getEntity();  
	            
	            if (resEntity != null) {    
	                Log.i(TAG,EntityUtils.toString(resEntity));
	                //HERE I NEED TO CHECK THE RESPONSE AND THEN I DECIDE WHETHER TO RETURN TRUE OR NOT
	                return true;
	            }
	    } catch (Exception e) {
	    	Log.e(TAG, "Error in http connection "+ e.toString());	    	
	    }
	    return false;
	}
	
	
	public RSAPublicKeySpec getKey(String id) {
		String result;
		
			try {
				HttpClient client = new DefaultHttpClient();  
				String postURL = "http://test.php";
				HttpPost post = new HttpPost(postURL);
				List<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("id",id));
				UrlEncodedFormEntity ent = new UrlEncodedFormEntity(params);
				post.setEntity(ent);			
				HttpResponse response = client.execute(post);			
				HttpEntity resEntity = response.getEntity();
				if (resEntity != null) {		
					
					/*
					InputStream is = resEntity.getContent();
					//convert response to string
					try {
						BufferedReader reader = new BufferedReader(new InputStreamReader(is));
						StringBuilder sb = new StringBuilder();
						String line = null;
						while ((line = reader.readLine()) != null) {
							sb.append(line + "\n");
						}
						is.close();
						result = sb.toString();
						
						
					}catch (Exception ex) {}
					*/
					
					// I CAN USE EITHER THE PREVIOUS COMMENTED READING OR THE SECOND ONE
					result = EntityUtils.toString(resEntity);
					Log.i(TAG,"result = " + result);
					
					JSONObject jObject = new JSONObject(result);					
					String modulus  = jObject.getString("modulus");
					String exponent = jObject.getString("exponent");
					
					BigInteger mod = new BigInteger(modulus);
					BigInteger exp = new BigInteger(exponent);										
				
					RSAPublicKeySpec pub = new RSAPublicKeySpec(mod,exp);		 
				    
				    return pub;					
				}				
	            
			} catch (UnsupportedEncodingException e) {				
			} catch (ClientProtocolException e) {				
			} catch (IOException e) {
				Log.e(TAG, "IO Exception "+ e.toString());
			} catch (JSONException e) {
				Log.e(TAG, "Error in JSON "+e.toString());
			} 
			return null;
		
	}
	

}
