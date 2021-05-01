package com.example.scopedstoragesamsungtest;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivityJava extends AppCompatActivity {
    private static final ExecutorService mExecutorService = Executors.newFixedThreadPool(2);
    private static final String TAG = "MainActivityJava";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (isAboveQ()) {
            saveImage(this);
        } else {
            Toast.makeText(this, "This device is not running Android 11. This test is only for selected models running Android 11.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Responsible for Saving the image,
     * first from Drawable Resources to Internal Directory and
     * then from copying from Internal Directory to MediaStore.
     *
     * @param mContext Activity Context.
     */
    private void saveImage(final Context mContext) {
        final SettableFuture<String> settableFuture = getFutureToSaveFileToMediaStore(mContext);
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                File internalDirFile = saveFileToInternalStorage(mContext, settableFuture);
                settableFuture.set(internalDirFile.getPath());
            }
        });
    }

    /**
     * Returns the future, which is responsible for saving the file to MediaStore.
     *
     * @return SettableFuture Containing the InternalDirectory file path.
     */
    private SettableFuture<String> getFutureToSaveFileToMediaStore(final Context mContext) {
        SettableFuture<String> settableFuture = SettableFuture.create();
        Futures.addCallback(settableFuture, new FutureCallback<String>() {

            @Override
            public void onSuccess(@NullableDecl String result) {
                Log.e(TAG, "Successful FilePath: " + result);
                try {
                    String externalFilePath = saveToMediaStore(result);
                    Log.e(TAG, "Successful Internal FilePath: " + result +
                            " ExternalFilePath: " + externalFilePath);
                } catch (IOException e) {
                    Log.e(TAG, "Failed copying to external Storage");
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(TAG, "Failed");
            }
        });

        return settableFuture;
    }

    /**
     * Saves File from Drawable resource to Internal App Directory.
     *
     * @param mContext
     * @param settableFuture
     * @return
     */
    private File saveFileToInternalStorage(Context mContext, SettableFuture<String> settableFuture) {
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.scoped_storage_sample);
        File file = new File(mContext.getFilesDir(), "scoped_storage_sample.jpg");
        FileOutputStream outStream = null;
        try {
            outStream = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, outStream);
            outStream.flush();
            outStream.close();
        } catch (FileNotFoundException e) {
            settableFuture.setException(e);
            e.printStackTrace();
        } catch (IOException e) {
            settableFuture.setException(e);
        }
        Log.e(TAG, "Internal FilePath: " + file.getPath());

        return file;
    }

    /**
     * Copies the file from Internal Directory File path to MediaStore.
     * This method is responsible for creating exception when ContentResolver.insert method returns a null Uri.
     * A Uri is required to get access to the saved file path using DATA column.
     *
     * @param originalFilePath
     * @return
     * @throws IOException
     */
    private String saveToMediaStore(String originalFilePath) throws IOException {
        ContentResolver resolver = getApplicationContext().getContentResolver();
        ContentValues contentValues = new ContentValues();

        Uri collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);

        // The actual display_name and mimetype are picked from file. This is hardcoded just for reference.
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, "image.jpg");
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");

        // On some devices running android 11, the below insert method returns null Uri.
        Uri result = resolver.insert(collection, contentValues);
        if (result == null) {
            return null;
        }
        File externalAppDirPath = new File(originalFilePath);
        copyFile(externalAppDirPath, result);

        String filePath = getFilePath(result);
        return filePath;
    }

    public void copyFile(File source, Uri destUri) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = getContentResolver().openOutputStream(destUri);
            copyStream(input, output);
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
    }

    public static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) > 0) {
            output.write(buffer, 0, bytesRead);
        }
    }

    private String getFilePath(Uri contentUri) {
        Cursor cursor = null;
        try {
            String[] proj = {MediaStore.Images.Media.DATA};
            cursor = getContentResolver().query(contentUri, proj, null, null, null);
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(columnIndex);
        } catch (Exception e) {
            return "";
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private boolean isAboveQ() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.Q;
    }
}
