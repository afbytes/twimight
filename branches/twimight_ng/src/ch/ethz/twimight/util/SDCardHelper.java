package ch.ethz.twimight.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;


public class SDCardHelper {
	private final static String TAG = "SDCardHelper";
	private File SDcardPath;
	private String state;
	private boolean isSDAvail;
	private boolean isSDWritable;
	private Uri tmpPhotoUri;
	public final static int IMAGE_WIDTH = 500;
	public final static int IMAGE_HEIGHT = 500;
	public static final int TYPE_XML = 1;
	//document type
	public static final int TYPE_PDF = 20;
	//picture type
	public static final int TYPE_JPG = 30;
	public static final int TYPE_PNG = 31;
	public static final int TYPE_GIF = 32;
	//audio type
	public static final int TYPE_MP3 = 40;
	//video type
	public static final int TYPE_FLV = 50;
	public static final int TYPE_RMVB = 51;
	public static final int TYPE_MP4 = 52;
	
	
	
	
	public SDCardHelper(Context context) {
		SDcardPath = null;
		state = null;
		isSDAvail = false;
		isSDWritable = false;
		tmpPhotoUri = null;
//		this.context=context;
	}
	public boolean checkSDStuff(String[] filePath){
		state = Environment.getExternalStorageState();
		if(Environment.MEDIA_MOUNTED.equals(state)){
			isSDAvail = true;
			isSDWritable = true;
			for(String path:filePath){
				SDcardPath = Environment.getExternalStoragePublicDirectory(path);
				SDcardPath.mkdirs();
			}
			Log.d(TAG, "sdcard storage check success");
			return true;
		}
		else if(Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)){
			isSDAvail = true;
			isSDWritable = false;
		}
		else{
			isSDAvail = false;
			isSDWritable = false;
		}
		Log.d("check", "check fail");
		return false;
	}

	//create the path storing tmp files
	public Uri createTmpPhotoStoragePath(String tmpPhotoPath){
		if(isSDWritable && isSDAvail){
			//uri where the photo will be stored temporally
			tmpPhotoUri = Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory(tmpPhotoPath), "tmp" + String.valueOf(System.currentTimeMillis()) + ".jpg"));//photoFileParent, photoFilename));
			return tmpPhotoUri;
		}
		else{
			return null;
		}
	}
	
	public boolean copyFile(String fromFile, String toFile){
		boolean copyResult = false;
		try {
			InputStream fosfrom = new FileInputStream(fromFile);
			OutputStream fosto = new FileOutputStream(toFile);
			byte bt[] = new byte[1024];
			int count;
			while ((count = fosfrom.read(bt)) > 0) {
				fosto.write(bt, 0, count);
			}
			fosfrom.close();
			fosto.close();
			copyResult = true;
		} catch (Exception e) {
			Log.i(TAG, "file io error");
		}
		Log.i(TAG, "file copy finished");
		return copyResult;
	}
	
	public void deleteFile(String delFilePath){
		File delFile = new File(delFilePath);
		delFile.delete();
		Log.i(TAG, "delete file finished");
	}
	
	/**
	 * get real file path from uri for picking a picture from the gallery
	 * @param activity
	 * @param contentUri
	 * @return
	 */
	public String getRealPathFromUri(Activity activity, Uri contentUri) {
	    String[] proj = { MediaStore.Images.Media.DATA };
	    //Cursor cursor = (new CursorLoader(CONTEXT, contentUri, proj, null, null, null)).loadInBackground();
		@SuppressWarnings("deprecation")
		Cursor cursor = activity.managedQuery(contentUri, proj, null, null, null);
	    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
	    cursor.moveToFirst();
	    return cursor.getString(column_index);
	}
	
	
	/**
	 * resize the picture to fit the size of imageView
	 * @param path
	 * @return
	 */
	public Bitmap decodeBitmapFile(String path){
		//Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
	    BitmapFactory.decodeFile(path,o);

	    //Find the correct scale value. It should be the power of 2.
	    int scale=1;
	    while(o.outWidth/scale > IMAGE_WIDTH && o.outHeight/scale > IMAGE_HEIGHT)
	    	scale*=2;
	    
	    //Decode with inSampleSize
	    BitmapFactory.Options o2 = new BitmapFactory.Options();
	    o2.inSampleSize=scale;
	    return BitmapFactory.decodeFile(path, o2);
	}
	
	/**
	 * return file in SD card with directory name = pathName and file name = fileName
	 * @param pathName
	 * @param fileName
	 * @return
	 */
	public File getFileFromSDCard(String pathName, String fileName){
		
		return new File(Environment.getExternalStoragePublicDirectory(pathName), fileName);
		
	}
	
	public boolean clearTempDirectory(String tmpPath){
		File tmpDir = Environment.getExternalStoragePublicDirectory(tmpPath);
		if (tmpDir != null){ //check if dir is not null
			File[] filenames = tmpDir.listFiles();
			for (File tmpFile : filenames)tmpFile.delete();
			return true;
		}
		else return false;
		
	}
	
	public int checkFileType(String url){

		if(url.endsWith(".pdf")){
			return TYPE_PDF;
		}
		else if(url.endsWith(".jpg")){
			return TYPE_JPG;
		}
		else if(url.endsWith(".png")){
			return TYPE_PNG;
		}
		else if(url.endsWith(".gif")){
			return TYPE_GIF;
		}
		else if(url.endsWith(".mp3")){
			return TYPE_MP3;
		}
		else if(url.endsWith(".flv")){
			return TYPE_FLV;
		}
		else if(url.endsWith(".rmvb")){
			return TYPE_RMVB;
		}
		else if(url.endsWith(".mp4")){
			return TYPE_MP4;
		}
		else{
			return TYPE_XML;
		}

	}


}
