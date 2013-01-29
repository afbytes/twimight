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
package ch.ethz.twimight.activities;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import ch.ethz.twimight.R;
import ch.ethz.twimight.data.HtmlPagesDbHelper;
import ch.ethz.twimight.data.StatisticsDBHelper;
import ch.ethz.twimight.location.LocationHelper;
import ch.ethz.twimight.net.Html.HtmlPage;
import ch.ethz.twimight.net.Html.HtmlService;
import ch.ethz.twimight.net.twitter.Tweets;
import ch.ethz.twimight.net.twitter.TwitterService;
import ch.ethz.twimight.net.twitter.TwitterUsers;
import ch.ethz.twimight.util.Constants;
import ch.ethz.twimight.util.InternalStorageHelper;
import ch.ethz.twimight.util.SDCardHelper;
import ch.ethz.twimight.util.TweetTagHandler;

/**
 * Display a tweet
 * @author thossmann
 * @author pcarta
 */
public class ShowTweetActivity extends TwimightBaseActivity{	
	Cursor c;
	
	// Views
	private TextView screenNameView;
	private TextView realNameView;
	private TextView tweetTextView;
	private TextView createdTextView;
	private TextView createdWithView;
	
	private LinearLayout userInfoView;
	ImageButton retweetButton;
	ImageButton deleteButton;
	ImageButton replyButton;
	ImageButton favoriteButton;
	ImageButton offlineButton;
	
	Uri uri;
	ContentObserver observer;
	Handler handler;
	
	private boolean favorited;
	int flags;
	int buffer;
	int userRowId;
	int rowId;
	String text;
	String screenName;

	protected String TAG = "ShowTweetActivity";
	
	//LOGS
		LocationHelper locHelper ;	
		Intent intent;
		ConnectivityManager cm;
		StatisticsDBHelper locDBHelper;	
	
	//photo
	private String photoPath;
	private final String PHOTO_PATH = "twimight_photos";
	//SDcard helper
	private SDCardHelper sdCardHelper;
	private String userID = null;
	private String tweetId;
	
	
	//offline html pages
	private int htmlStatus;
	private ArrayList<String> htmlUrls;
	private HtmlPagesDbHelper htmlDbHelper;
	private ArrayList<String> htmlsToDownload;
	private boolean htmlsDownloaded = false; // whether htmls of this tweet have been downloaded
	private boolean downloadNotSuccess = false; // whether it's a not successfully downloaded tweet
	
	/** 
	 * Called when the activity is first created. 
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.showtweet);		
		
		sdCardHelper = new SDCardHelper(this);
		
		locDBHelper = new StatisticsDBHelper(this);
		locDBHelper.open();
		cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);		
		locHelper = new LocationHelper(this);
		
		//html database
		htmlDbHelper = new HtmlPagesDbHelper(this);
		htmlDbHelper.open();
		htmlUrls = new ArrayList<String>();
		
		screenNameView = (TextView) findViewById(R.id.showTweetScreenName);
		realNameView = (TextView) findViewById(R.id.showTweetRealName);
		
		tweetTextView = (TextView) findViewById(R.id.showTweetText);
		createdTextView = (TextView) findViewById(R.id.showTweetCreatedAt);
		createdWithView = (TextView) findViewById(R.id.showTweetCreatedWith);
		rowId = getIntent().getIntExtra("rowId", 0);
		

		// If we don't know which tweet to show, we stop the activity
		if(rowId != 0) {		
			
			queryContentProvider();
			
			if(c.getCount() == 0) 
				finish();
			
			else {				
				// register content observer to refresh when user was updated
				
				startManagingCursor(c);	
				handler = new Handler();											
				
				userID = String.valueOf(c.getLong(c.getColumnIndex(TwitterUsers.COL_ID)));
				//locate the directory where the photos are stored
				photoPath = PHOTO_PATH + "/" + userID;
				
				setTweetInfo();
				setUserInfo();			
				setProfilePicture();
				setPhotoAttached();
				
				
				// Tweet background and disaster info
				if(c.getInt(c.getColumnIndex(Tweets.COL_ISDISASTER))>0){
					if(c.getInt(c.getColumnIndex(Tweets.COL_ISVERIFIED))==0){
						LinearLayout unverifiedInfo = (LinearLayout) findViewById(R.id.showTweetUnverified);
						unverifiedInfo.setVisibility(LinearLayout.VISIBLE);
					}
				}
				
				flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
				buffer = c.getInt(c.getColumnIndex(Tweets.COL_BUFFER));
				
				handleTweetFlags();					
				setupButtons();					
				setHtml();
				
				// If there are any flags, schedule the Tweet for synch
				if(c.getInt(c.getColumnIndex(Tweets.COL_FLAGS)) >0){
					Log.i(TAG,"requesting tweet update to twitter");
					Intent i = new Intent(TwitterService.SYNCH_ACTION);
					i.putExtra("synch_request", TwitterService.SYNCH_TWEET);
					i.putExtra("rowId", new Long(uri.getLastPathSegment()));
					startService(i);
				}
			}		
		}
		else 
			finish();
		

		
		
	}
	
	
	private void queryContentProvider() {
		// get data from local DB and mark for update
		uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);		
		c = getContentResolver().query(uri, null, null, null, null);
		if(c.getCount() > 0) 
			c.moveToFirst();
	}


	/**
	 *  Buttons
	 */
	private void setupButtons() {
		
		
		String userString = Long.toString(c.getLong(c.getColumnIndex(TwitterUsers.COL_ID)));
		String localUserString = LoginActivity.getTwitterId(this);		

		
		// Retweet Button
		retweetButton = (ImageButton) findViewById(R.id.showTweetRetweet);
		// we do not show the retweet button for (1) tweets from the local user, (2) tweets which have been flagged to retweeted and (3) tweets which have been marked as retweeted 
		if(userString.equals(localUserString) || ((flags & Tweets.FLAG_TO_RETWEET) > 0) || (c.getInt(c.getColumnIndex(Tweets.COL_RETWEETED))>0)){
			retweetButton.setVisibility(Button.GONE);
		} else {
			retweetButton.setOnClickListener(new OnClickListener(){

				@Override
				public void onClick(View v) {
					showRetweetDialog();
					retweetButton.setVisibility(Button.GONE);
				}
				
			});
		}
		
		// Delete Button
		deleteButton = (ImageButton) findViewById(R.id.showTweetDelete);
		if(userString.equals(localUserString)){			
			
			deleteButton.setVisibility(ImageButton.VISIBLE);			
			if((flags & Tweets.FLAG_TO_DELETE) == 0){
				deleteButton.setOnClickListener(new OnClickListener(){
					@Override
					public void onClick(View v) {
						showDeleteDialog();						
					}
				});				
				
			} else {
				deleteButton.setVisibility(ImageButton.GONE);
			}
		} else {
			deleteButton.setVisibility(ImageButton.GONE);
		}
	
		// Reply button: we show it only if we have a Tweet ID!
		replyButton = (ImageButton) findViewById(R.id.showTweetReply);
		if(c.getLong(c.getColumnIndex(Tweets.COL_TID)) != 0 || 
				PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON)==true){
			replyButton.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent i = new Intent(getBaseContext(), NewTweetActivity.class);
					if(c.getLong(c.getColumnIndex(Tweets.COL_TID)) != 0)
						i.putExtra("isReplyTo", c.getLong(c.getColumnIndex(Tweets.COL_TID)));
					else
						i.putExtra("isReplyTo", -1);
					i.putExtra("text", "@"+c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME))+ " ");
					startActivity(i);
				}
			});
		} else {
			replyButton.setVisibility(Button.GONE);
		}
		
		// Favorite button
		favorited = (c.getInt(c.getColumnIndex(Tweets.COL_FAVORITED)) > 0) || ((flags & Tweets.FLAG_TO_FAVORITE)>0);
		favoriteButton = (ImageButton) findViewById(R.id.showTweetFavorite);
		if( favorited && !((flags & Tweets.FLAG_TO_UNFAVORITE)>0)){
			favoriteButton.setImageResource(R.drawable.btn_twimight_favorite_on);
		}
		favoriteButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				
				if(favorited){
					// unfavorite
					getContentResolver().update(uri, clearFavoriteFlag(flags), null, null);
					((ImageButton) v).setImageResource(R.drawable.btn_twimight_favorite);
					favorited=false;
					
				} else {
					// favorite
					ContentValues cv = setFavoriteFlag(flags);
					if (cv != null) {
						getContentResolver().update(uri, cv , null, null);
						((ImageButton) v).setImageResource(R.drawable.btn_twimight_favorite_on);
						favorited=true;
					} 
				}			
			}
			
		});
		
		// offline view button
		
		//get the html status of this tweet
		htmlStatus = c.getInt(c.getColumnIndex(Tweets.COL_HTMLS));
		
		offlineButton = (ImageButton) findViewById(R.id.showTweetOfflineview);
		//download the pages and store them locally, set up the html database
		int networkActive = 1; 
		if(cm.getActiveNetworkInfo()==null || !cm.getActiveNetworkInfo().isConnected())networkActive = 0;
			
		if( htmlStatus == 0 || networkActive == 0)
		{
			offlineButton.setVisibility(View.GONE);
		}
		offlineButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if(downloadAndInsert()){
					offlineButton.setVisibility(View.GONE);
				}

			}
			
		});
		
	}
		
	/**
	 *  method to handle tweet's flags
	 *  
	 */
	private void handleTweetFlags() {
		LinearLayout toSendNotification = (LinearLayout) findViewById(R.id.showTweetTosend);
		LinearLayout toDeleteNotification = (LinearLayout) findViewById(R.id.showTweetTodelete);
		LinearLayout toFavoriteNotification = (LinearLayout) findViewById(R.id.showTweetTofavorite);
		LinearLayout toUnfavoriteNotification = (LinearLayout) findViewById(R.id.showTweetTounfavorite);
		LinearLayout toRetweetNotification = (LinearLayout) findViewById(R.id.showTweetToretweet);					
		if ( toSendNotification != null) {
			if((flags & Tweets.FLAG_TO_INSERT) ==0 ){						
				toSendNotification.setVisibility(LinearLayout.GONE);						
			} else
				toSendNotification.setVisibility(LinearLayout.VISIBLE);	
		} else 
			Log.i(TAG,"toSendNotification");
		
		if (toDeleteNotification != null) {
			if((flags & Tweets.FLAG_TO_DELETE) ==0){						
				toDeleteNotification.setVisibility(LinearLayout.GONE);						

			} else{
				toDeleteNotification.setVisibility(LinearLayout.VISIBLE);
				TextView toDeleteText = (TextView) findViewById(R.id.showTweetInfoText2);
				if (toDeleteText != null) {
					toDeleteText.setBackgroundResource(android.R.drawable.list_selector_background);						
					toDeleteText.setOnClickListener(new OnClickListener() {

						@Override
						public void onClick(View v) {
							LinearLayout toDeleteNotification = (LinearLayout) findViewById(R.id.showTweetTodelete);
							if (toDeleteNotification != null) {
								
								int num = getContentResolver().update(uri, removeDeleteFlag(flags), null, null);
								toDeleteNotification.setVisibility(LinearLayout.GONE);
								if (num > 0) {
									
									queryContentProvider();
									if (c != null) {													
										flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
										setupButtons();
									}												
								}
							} else 
								Log.i(TAG,"toDeleteNotification");
															 
						}							
					});
				} else
					Log.i(TAG,"toSendNotification");						
			}
		} else 
			Log.i(TAG,"toDeleteNotification");
		
		if ( toFavoriteNotification != null) {
			if((flags & Tweets.FLAG_TO_FAVORITE) ==0){						
				toFavoriteNotification.setVisibility(LinearLayout.GONE);

			} else
				toFavoriteNotification.setVisibility(LinearLayout.VISIBLE);
		} else 
			Log.i(TAG,"toFavoriteNotification");

		if (toUnfavoriteNotification != null) {
			if((flags & Tweets.FLAG_TO_UNFAVORITE) ==0){						
				toUnfavoriteNotification.setVisibility(LinearLayout.GONE);

			} else
				toUnfavoriteNotification.setVisibility(LinearLayout.VISIBLE);
		} else 
			Log.i(TAG,"toUnFavoriteNotification");

		if (toRetweetNotification != null) {
			if((flags & Tweets.FLAG_TO_RETWEET) ==0){						
				toRetweetNotification.setVisibility(LinearLayout.GONE);

			} else
				toRetweetNotification.setVisibility(LinearLayout.VISIBLE);
		}
	}


	/**
	 *  method to set the profile picture
	 *  
	 */
	private void setProfilePicture() {
		// Profile image
		if(!c.isNull(c.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE))){
			
			ImageView picture = (ImageView) findViewById(R.id.showTweetProfileImage);			
			InternalStorageHelper helper = new InternalStorageHelper(this);
			byte[] imageByteArray = helper.readImage(c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME)));
			if (imageByteArray != null) {				
				//is = context.getContentResolver().openInputStream(uri);				
				Bitmap bm = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.length);
				picture.setImageBitmap(bm);	
			} else
				picture.setImageResource(R.drawable.default_profile);
		}
		
	}

	/**
	 *  method to set the photo attached with thi tweet
	 *  
	 */
	
	private void setPhotoAttached() {
		// Profile image
		String[] filePath = {photoPath};
		if(sdCardHelper.checkSDStuff(filePath)){
			if(!c.isNull(c.getColumnIndex(Tweets.COL_MEDIA))){
				ImageView photoView = (ImageView) findViewById(R.id.showPhotoAttached);
				
				String photoFileName =  c.getString(c.getColumnIndex(Tweets.COL_MEDIA));
				Uri photoUri = Uri.fromFile(sdCardHelper.getFileFromSDCard(photoPath, photoFileName));//photoFileParent, photoFilename));
				Bitmap photo = sdCardHelper.decodeBitmapFile(photoUri.getPath());
				photoView.setImageBitmap(photo);
			}
		}

		
	}
		
	/**
	 *  The user info
	 *  
	 */
	private void setUserInfo() {
		
		userRowId = c.getInt(c.getColumnIndex("userRowId")); 
		userInfoView = (LinearLayout) findViewById(R.id.showTweetUserInfo);
		
		userInfoView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent i = new Intent(getBaseContext(), ShowUserActivity.class);
				i.putExtra("rowId", userRowId);
				i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); 
				startActivity(i);
			}
			
		});
		
	}

	private void setHtml() {

		htmlsToDownload = new ArrayList<String>();
		tweetId = String.valueOf(c.getLong(c.getColumnIndex(Tweets.COL_TID)));
		boolean buttonStatus = false;
		//try to retrieve the filename of attached html pages
		if(!htmlUrls.isEmpty()){
			for(String htmlUrl : htmlUrls){
				
				ContentValues htmlCV = htmlDbHelper.getPageInfo(htmlUrl, tweetId);
				boolean fileStatusNormal = true;
				if(htmlCV!=null){
					//check if file status normal, exists and size
					String[] filePath = {HtmlPage.HTML_PATH + "/" + htmlCV.getAsString(HtmlPage.COL_USER)}; 
					
					if(sdCardHelper.checkSDStuff(filePath)){
						String filename = htmlCV.getAsString(HtmlPage.COL_FILENAME);
						if(!sdCardHelper.getFileFromSDCard(filePath[0], filename).exists() || sdCardHelper.getFileFromSDCard(filePath[0], filename).length() < 500){
							fileStatusNormal = false;
						}
					}
					
				}
				
				//if entry does not exist, add the url in to be downloaded list
				
				if(htmlCV == null || (htmlCV.getAsInteger(HtmlPage.COL_DOWNLOADED) == 0) || !fileStatusNormal){
					
					htmlsToDownload.add(htmlUrl);
					buttonStatus = true;
					if(htmlCV != null){
						downloadNotSuccess = true;
						Log.d(TAG, "not downloaded" + htmlCV.toString());
					}
					
				}
				else{
					Log.d(TAG, htmlCV.toString());
				}
			}
			Log.d(TAG, "htmls to be downloaded:" + htmlsToDownload.toString());
		}
		
		if(!buttonStatus){
			htmlsDownloaded = true;
			offlineButton.setVisibility(View.GONE);
		}
	}
	
	private boolean downloadAndInsert(){
		
		//insert database
		boolean result = true;
		String[] filePath = {HtmlPage.HTML_PATH + "/" + userID};
		if(sdCardHelper.checkSDStuff(filePath)){
			String tweetId = String.valueOf(Long.valueOf(c.getLong(c.getColumnIndex(Tweets.COL_TID))));
			for(int i=0; i<htmlsToDownload.size();i++){

				if(downloadNotSuccess){
					result = true;

				}else{
					String filename = "twimight" + String.valueOf(System.currentTimeMillis()) + ".xml";
					result = result && htmlDbHelper.insertPage(htmlsToDownload.get(i), filename, tweetId, userID, 0);

				}
			}
			
			//insert database
			if(result){
				Intent i = new Intent(ShowTweetActivity.this, HtmlService.class);
				Bundle mBundle = new Bundle();
				mBundle.putInt(HtmlService.DOWNLOAD_REQUEST, HtmlService.DOWNLOAD_SINGLE);
				mBundle.putString("user_id", userID);
				Log.d(TAG, userID);
				mBundle.putString("tweetId", tweetId);
				Log.d(TAG, tweetId);
				mBundle.putStringArrayList("urls", htmlsToDownload);

				i.putExtras(mBundle);
				startService(i);
			}
			
			
			htmlsDownloaded = result;
		}
		else{
			htmlsDownloaded = false;
			result = false;
		}
		
		
		return result;
	}
	

	private class InternalURLSpan extends ClickableSpan {      
		String url;
      
        public InternalURLSpan(String url) {  
           
        	this.url=url;
        }  
      
        @Override  
        public void onClick(View widget) {  
           
        	
	        if ((locHelper != null && locHelper.count > 0) && locDBHelper != null && cm != null) {			
	    		locHelper.unRegisterLocationListener();
	    		if(cm.getActiveNetworkInfo()!=null)
	    			locDBHelper.insertRow(locHelper.loc, cm.getActiveNetworkInfo().getTypeName(), ShowTweetListActivity.LINK_CLICKED , url, System.currentTimeMillis());
	    		else locDBHelper.insertRow(locHelper.loc, "offline view", ShowTweetListActivity.LINK_CLICKED , url, System.currentTimeMillis());
	    	} else {}
	        if(cm.getActiveNetworkInfo()!=null && cm.getActiveNetworkInfo().isConnected()){	
        		//if there is active internet access, use normal browser
            	Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            	startActivity(intent);
        	}
        	else{
        		if(htmlsDownloaded){
        			//set up our own web view
            		Intent intent = new Intent(getBaseContext(), WebViewActivity.class);
            		intent.putExtra("url", url);
            		intent.putExtra("user_id", userID);
            		intent.putExtra("tweet_id", tweetId);
            		startActivity(intent);
        		}
        		else{
        			Toast.makeText(getBaseContext(), "unable to view in offline mode because pages have not been downloaded", Toast.LENGTH_LONG).show();
        		}
        	}
    		

        }  
    }  
	
	/**
	 *  The tweet info
	 *  
	 */
	private void setTweetInfo() {
		
		screenName = c.getString(c.getColumnIndex(TwitterUsers.COL_SCREENNAME));
		screenNameView.setText("@"+screenName);
		realNameView.setText(c.getString(c.getColumnIndex(TwitterUsers.COL_NAME)));
		text = c.getString(c.getColumnIndex(Tweets.COL_TEXT));
				
		SpannableString str = new SpannableString(Html.fromHtml(text, null, new TweetTagHandler(this)));
		
		try {
			String substr = str.toString();
			
			String[] strarr = substr.split(" ");

			//save the urls of the tweet in a list
			int passedLen = 0;
			for(String subStrarr : strarr){

				if(subStrarr.indexOf("http://") == 0){
					htmlUrls.add(subStrarr);
					int startIndex = passedLen;
					int endIndex = passedLen + subStrarr.length() - 1;
					str.setSpan(new InternalURLSpan(subStrarr), startIndex, endIndex, Spannable.SPAN_MARK_MARK);
				}	
				passedLen = passedLen + subStrarr.length() + 1;
			}
			Log.d("test1", htmlUrls.toString());			
		} catch (Exception ex) {
		}
		tweetTextView.setText(str);
		tweetTextView.setMovementMethod(LinkMovementMethod.getInstance());

		createdTextView.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(c.getLong(c.getColumnIndex(Tweets.COL_CREATED)))).toString());
		if(c.getString(c.getColumnIndex(Tweets.COL_SOURCE))!=null){
			createdWithView.setText(Html.fromHtml(c.getString(c.getColumnIndex(Tweets.COL_SOURCE))));
		} else {
			createdWithView.setVisibility(TextView.GONE);
		}

				
		String retweeted_by = c.getString(c.getColumnIndex(Tweets.COL_RETWEETED_BY));
		TextView textRetweeted_by = (TextView) findViewById(R.id.showTweetRetweeted_by);
		if (retweeted_by != null) {
			textRetweeted_by.append(retweeted_by);		
			textRetweeted_by.setVisibility(View.VISIBLE);					
		}					
		
	}

	/**
	 * On resume
	 */
	@Override
	public void onResume(){
		super.onResume();
		
		observer = new TweetContentObserver(handler);
		c.registerContentObserver(observer);
		
	}
	


	
	/**
	 * On Pause
	 */
	@Override
	public void onPause(){
		Log.i(TAG, "on pause");
		super.onPause();
		if(c!=null){
			if(observer != null) 
				try {
					c.unregisterContentObserver(observer);
				} catch (IllegalStateException ex) {
					//Log.e(TAG,"error unregistering observer",ex);
				}
		}
		
	}

	/**
	 * Called at the end of the Activity lifecycle
	 */
	@Override
	public void onDestroy(){
		super.onDestroy();
		
		if (locHelper != null) 			
			locHelper.unRegisterLocationListener();    			
		
		userInfoView.setOnClickListener(null);
		retweetButton.setOnClickListener(null);
		deleteButton.setOnClickListener(null);
		replyButton.setOnClickListener(null);
		favoriteButton.setOnClickListener(null);	
		c.close();
		
		unbindDrawables(findViewById(R.id.showTweetRoot));
		
	}
	
	/**
	 * Asks the user if she wants to delete a tweet.
	 */
	private void showDeleteDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to delete your Tweet?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);	        	   
		        	   queryContentProvider();
		        	   Long tid = c.getLong(c.getColumnIndex(Tweets.COL_TID));
		        	   String delPhotoName = c.getString(c.getColumnIndex(Tweets.COL_MEDIA));
		        	   if(delPhotoName != null){
		        		   photoPath = PHOTO_PATH + "/" + userID;
		        		   String[] filePath = {photoPath};
		       			   if(sdCardHelper.checkSDStuff(filePath)){
		       				   File photoFile = sdCardHelper.getFileFromSDCard(photoPath, delPhotoName);//photoFileParent, photoFilename));
				        	   photoFile.delete();
		       			   }
		       			   
		        	   }
		        	   
		        	   //delete html pages
		        	   if(!htmlUrls.isEmpty()){
		        		   for(String htmlUrl:htmlUrls){
		        			   ContentValues htmlCV = htmlDbHelper.getPageInfo(htmlUrl, String.valueOf(tid));
		        			   if(htmlCV != null){
		        				   String[] filePath = {HtmlPage.HTML_PATH + "/" + userID};
				       			   if(sdCardHelper.checkSDStuff(filePath)){
				       				   File htmlFile = sdCardHelper.getFileFromSDCard(filePath[0], htmlCV.getAsString(HtmlPage.COL_FILENAME));//photoFileParent, photoFilename));
				       				   htmlFile.delete();
				       				   htmlDbHelper.deletePage(htmlUrl, String.valueOf(tid));
				       			   }
		        				   
				       			   htmlDbHelper.deletePage(htmlUrl, String.valueOf(tid));
		        		
		        			   }
		        		   }
		        	   }
		  
		        	   if (tid != null && tid != 0)
		        		   getContentResolver().update(uri, setDeleteFlag(flags), null, null);
		        	   else
		        		   getContentResolver().delete(uri,null,null );
		        	   finish();
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		                dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	/**
	 * Asks the user how to retweet a tweet (old or new style)
	 */
	private void showRetweetDialog(){
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Would you like to modify the tweet before retweeting?")
		       .setCancelable(false)
		       .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   Intent i = new Intent(getBaseContext(), NewTweetActivity.class);
		        	   i.putExtra("text", "RT @"+screenName+" " +text);
		        	   i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		        	   startActivity(i);
		           }
		       })
		       .setNegativeButton("No", new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   getContentResolver().update(uri, setRetweetFlag(flags), null, null);
		        	   c.requery();
		               dialog.cancel();
		           }
		       });
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	/**
	 * Removes the delete flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues removeDeleteFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.COL_FLAGS, flags & (~ Tweets.FLAG_TO_DELETE) );
		cv.put(Tweets.COL_BUFFER, buffer);
		return cv;
	}
	
	/**
	 * Adds the delete flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues setDeleteFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.COL_FLAGS, flags | Tweets.FLAG_TO_DELETE);
		cv.put(Tweets.COL_BUFFER, buffer);
		return cv;
	}
	
	/**
	 * Adds the to retweet flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues setRetweetFlag(int flags) {
		ContentValues cv = new ContentValues();
		cv.put(Tweets.COL_FLAGS, flags | Tweets.FLAG_TO_RETWEET);
		
		if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("prefDisasterMode", Constants.DISASTER_DEFAULT_ON)==true) {
			cv.put(Tweets.COL_ISDISASTER, 1);
			cv.put(Tweets.COL_BUFFER, buffer | Tweets.BUFFER_DISASTER);
		} else
			cv.put(Tweets.COL_BUFFER, buffer);
		return cv;
	}
	
	/**
	 * Adds the favorite flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues setFavoriteFlag(int flags) {
		ContentValues cv = new ContentValues();
		
		queryContentProvider();

		try {
			// set favorite flag und clear unfavorite flag
			if (c.getInt(c.getColumnIndexOrThrow(Tweets.COL_FAVORITED)) > 0)
				cv.put(Tweets.COL_FLAGS, (flags  & ~Tweets.FLAG_TO_UNFAVORITE));
			else			
				cv.put(Tweets.COL_FLAGS, (flags | Tweets.FLAG_TO_FAVORITE) & (~Tweets.FLAG_TO_UNFAVORITE));
			// put in favorites bufer
			cv.put(Tweets.COL_BUFFER, buffer|Tweets.BUFFER_FAVORITES);
			return cv;
		} catch (Exception ex){
			Log.e(TAG,"error: ",ex);
			return null;
		}
		
		
		
	}
	
	/**
	 * Clears the favorite flag and returns the flags in a content value structure 
	 * to send to the content provider 
	 * @param flags
	 * @return
	 */
	private ContentValues clearFavoriteFlag(int flags) {
		ContentValues cv = new ContentValues();
		
		// clear favorite flag and set unfavorite flag
		if (c.getInt(c.getColumnIndex(Tweets.COL_FAVORITED)) > 0)
			cv.put(Tweets.COL_FLAGS, (flags & (~Tweets.FLAG_TO_FAVORITE)) | Tweets.FLAG_TO_UNFAVORITE);	
		else
			cv.put(Tweets.COL_FLAGS, (flags & (~Tweets.FLAG_TO_FAVORITE)));	
		
		if ( !c.isNull(c.getColumnIndex(Tweets.COL_TID)) ) {
			cv.put(Tweets.COL_BUFFER, buffer);
		}
		else {
			cv.put(Tweets.COL_BUFFER, buffer & (~Tweets.BUFFER_FAVORITES));
		}
		return cv;
	}
	
	/**
	 * Calls methods if tweet data has been updated
	 * @author pcarta
	 *
	 */
	class TweetContentObserver extends ContentObserver {
		public TweetContentObserver(Handler h) {
			super(h);
		}

		@Override
		public boolean deliverSelfNotifications() {
			
			return true;
		}

		@Override
		public void onChange(boolean selfChange) {
			
			super.onChange(selfChange);

			/* close the old cursor
			if(c!=null) {				
				c.close();
			}*/
			
			// and get a new one
			uri = Uri.parse("content://" + Tweets.TWEET_AUTHORITY + "/" + Tweets.TWEETS + "/" + rowId);		
			c = getContentResolver().query(uri, null, null, null, null);		
			if(c.getCount() == 1) {
				
				
				c.moveToFirst();
				if (c.getColumnIndex(Tweets.COL_FLAGS) > -1)
					flags = c.getInt(c.getColumnIndex(Tweets.COL_FLAGS));
				// update the views
				handleTweetFlags();				
			}
			
			

		}
		
		
		
	}
	
	
	
}
