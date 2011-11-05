package ch.ethz.twimight.net.twitter;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;

import org.apache.http.util.ByteArrayBuffer;

import winterwell.jtwitter.Twitter.Status;
import android.content.ContentValues;
import android.content.Context;
import ch.ethz.twimight.data.DbOpenHelper;
import ch.ethz.twimight.data.TweetDbActions;

public class FetchProfilePic implements Runnable {
	List<Status> timeline;	
	TweetDbActions dbActions;
	Context cont;
  	
  	public FetchProfilePic(List<Status> timeline, TweetDbActions dbActions, Context cont) {
  		this.timeline = timeline;	
  		this.cont = cont;
  		this.dbActions = dbActions;
  		
  	}
  	
  	public void run() {
  		for (Status status : timeline) {
  			try {  	       		
  	  			URL url = new URL(status.getUser().getProfileImageUrl().toURL().toString());
  	  			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
  	  			connection.setDoInput(true);
  	  			connection.connect();
  	  			InputStream input = connection.getInputStream();	  			
  	  			
  	  			BufferedInputStream bis = new BufferedInputStream(input,128);			
  	  			ByteArrayBuffer baf = new ByteArrayBuffer(128);			
  	  			//get the bytes one by one			
  	  			int current = 0;			
  	  			while ((current = bis.read()) != -1) {			
  	  			        baf.append((byte) current);			
  	  			}		
  	  			ContentValues dataToInsert = new ContentValues(); 		
  	  			dataToInsert.put(DbOpenHelper.C_IMAGE,baf.toByteArray());	
  	  			dataToInsert.put(DbOpenHelper.C_USER,status.getUser().getScreenName());
  	  			dataToInsert.put(DbOpenHelper.C_CREATED_AT , new Date().getTime());
  	  			dbActions.insertPicture(dataToInsert);
  	  			
  	  		} catch (Exception e) {	  	  			
  	  		}
  	  		try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}  		
  		
  		
  	} 
  	
  }

