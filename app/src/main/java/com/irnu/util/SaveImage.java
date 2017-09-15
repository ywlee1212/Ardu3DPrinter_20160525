package com.irnu.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Environment;

public class SaveImage {

	public SaveImage() {

	}

	public static final String FOLDER_NAME = "/Ardu3DPrinter/";

	public static String getFolderPath() {
		
		String sdcard = Environment.getExternalStorageState();
		File file = null;
		
		if( !sdcard.equals(Environment.MEDIA_MOUNTED)) {
			file = Environment.getRootDirectory();
		} else {
			file = Environment.getExternalStorageDirectory();
		}
		
		String dir = file.getAbsolutePath() + FOLDER_NAME;
		
		file = new File(dir);
		if(!file.exists()) {
			file.mkdirs();
		}
		
		return dir;
	}

	public static void SaveBitmapToFileCache(Context context, Bitmap bitmap) {
        
		String filename = "Ardu3DPrinter_"+new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date(System.currentTimeMillis()))+".jpg";
		String dirPath = getFolderPath(); 

		File fileCacheItem = new File(dirPath + filename);
        OutputStream out = null;

        try
        {
        	fileCacheItem.createNewFile();
            out = new FileOutputStream(fileCacheItem);
 
            bitmap.compress(CompressFormat.JPEG, 100, out);
            
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + dirPath + filename)));

        }
        catch (Exception e) 
        {
        	e.printStackTrace();
        }
        finally
        {
        	try
            {
            	out.close();
            }
            catch (IOException e)
            {
            	e.printStackTrace();
            }
        }
	}
}
