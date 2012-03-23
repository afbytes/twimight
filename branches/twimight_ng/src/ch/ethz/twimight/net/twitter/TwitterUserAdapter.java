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

package ch.ethz.twimight.net.twitter;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import ch.ethz.twimight.R;
import ch.ethz.twimight.util.InternalStorageHelper;

/** 
 * Cursor adapter for a cursor containing users.
 */
public class TwitterUserAdapter extends SimpleCursorAdapter {
	
	static final String[] from = {TwitterUsers.COL_SCREENNAME, TwitterUsers.COL_NAME, TwitterUsers.COL_LOCATION};
	static final int[] to = {R.id.showUserScreenName, R.id.showUserRealName, R.id.showUserLocation};

	/** Constructor */
	public TwitterUserAdapter(Context context, Cursor c) {
		super(context, R.layout.userrow, c, from, to);  
	}

	/** This is where data is mapped to its view */
	@Override
	public void bindView(View userrow, Context context, Cursor cursor) {
		super.bindView(userrow, context, cursor);
			
		// Profile image
		ImageView picture = (ImageView) userrow.findViewById(R.id.showUserProfileImage);
		if(!cursor.isNull(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE))){
			InternalStorageHelper helper = new InternalStorageHelper(context);
			byte[] imageByteArray = helper.readImage(cursor.getString(cursor.getColumnIndex(TwitterUsers.COL_PROFILEIMAGE)));
			if (imageByteArray != null) {				
				//is = context.getContentResolver().openInputStream(uri);				
				Bitmap bm = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.length);
				picture.setImageBitmap(bm);	
			} else
				picture.setImageResource(R.drawable.default_profile);
		} else {
			picture.setImageResource(R.drawable.default_profile);
		}
		LinearLayout rowLayout = (LinearLayout) userrow.findViewById(R.id.showUserInfo);		
		rowLayout.setBackgroundResource(R.drawable.normal_tweet_background);
	}
	
}
