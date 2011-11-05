package ch.ethz.twimight.data;

import java.io.ByteArrayInputStream;

import ch.ethz.twimight.R;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;

/** Custom adapter so we can apply transformations on the data */
public class FriendsAdapter extends SimpleCursorAdapter {
Cursor cPic ;
private static String TAG = "FriendsAdapter";

  static final String[] from = {DbOpenHelper.C_USER };
  static final int[] to = { R.id.friendsText};

  /** Constructor */
  public FriendsAdapter(Context context, Cursor c, Cursor cPic) {
    super(context, R.layout.friendsrow, c, from, to);  
	this.cPic = cPic;
  }

  /** This is where data is mapped to its view */
  @Override
  public void bindView(View row, Context context, Cursor cursor) {
	  
    super.bindView(row, context, cursor);
    byte[] imageByteArray = null;
    // Get the individual pieces of data
   
    // Find views by id   
    ImageView picture = (ImageView) row.findViewById(R.id.friendsImage);
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
    	
    
    } catch (Exception ex) {}
    

   
  }

}
