package ch.ethz.twimight;

import java.io.ByteArrayInputStream;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

/** Custom adapter so we can apply transformations on the data */
public class TimelineAdapter extends SimpleCursorAdapter {
Cursor cPic ;
private static String TAG = "TimelineAdapter";

  static final String[] from = { DbOpenHelper.C_CREATED_AT, DbOpenHelper.C_USER,
      DbOpenHelper.C_TEXT };
  static final int[] to = { R.id.textCreatedAt, R.id.textUser, R.id.textText };

  /** Constructor */
  public TimelineAdapter(Context context, Cursor c, Cursor cPic) {
    super(context, R.layout.row, c, from, to);  
	this.cPic = cPic;
  }

  /** This is where data is mapped to its view */
  @Override
  public void bindView(View row, Context context, Cursor cursor) {
    super.bindView(row, context, cursor);
    byte[] imageByteArray = null;
    // Get the individual pieces of data
    long createdAt = cursor.getLong(cursor
        .getColumnIndex(DbOpenHelper.C_CREATED_AT));

    // Find views by id
    TextView textCreatedAt = (TextView) row.findViewById(R.id.textCreatedAt);
    ImageView picture = (ImageView) row.findViewById(R.id.imageView1);
    try {
    	if (cPic != null) {    		
    		String user = cursor.getString(cursor.getColumnIndex(DbOpenHelper.C_USER));    		
    		for (int i = 0; i < cPic.getCount(); i++) {
    			if (cPic.getString(cPic.getColumnIndexOrThrow(DbOpenHelper.C_USER)).equals(user) ) {
    				imageByteArray = cPic.getBlob(cPic.getColumnIndex(DbOpenHelper.C_IMAGE));    				
    				break;
    			}
    			if (!cPic.isAfterLast())
    				cPic.moveToNext();
    		}
    		cPic.moveToFirst();
    		
    		if (imageByteArray != null) {    			
    			ByteArrayInputStream imageStream = new ByteArrayInputStream(imageByteArray);    	
        		Bitmap theImage = BitmapFactory.decodeStream(imageStream);
        		picture.setImageBitmap(theImage);
        		imageByteArray = null;
    		}  
    		else {    			
    			picture.setImageResource(R.drawable.default_profile);
    		}    		
    	}    	
    	int index = cursor.getColumnIndexOrThrow(DbOpenHelper.C_IS_DISASTER);
    	int isDisaster =  cursor.getInt(index);
    	if (isDisaster == Timeline.TRUE) {
    		row.setBackgroundColor(0xcccc2200);    		
    	}
    	else 
    		row.setBackgroundColor(0xff000000);
    	
    
    } catch (Exception ex) {
    	//Log.e(TAG,"error inside timeline adapter",ex);
    }
    

    // Apply custom transformations
    textCreatedAt.setText(DateUtils.getRelativeTimeSpanString(createdAt));
  }

}
