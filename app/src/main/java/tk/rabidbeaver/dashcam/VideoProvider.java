package tk.rabidbeaver.dashcam;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VideoProvider extends ContentProvider implements ContentProvider.PipeDataWriter<String> {
    public String getType(@NonNull Uri uri){
        return "video/mkv";
    }

    public int update (@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs){
        return 0;
    }

    public Uri insert (@NonNull Uri uri, ContentValues values){
        return null;
    }

    public boolean onCreate (){
        return true;
    }

    public int delete (@NonNull Uri uri, String selection, String[] selectionArgs){
        return 0;
    }

    public Cursor query (@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder){
        return null;
    }

    @Override public ParcelFileDescriptor openFile(@NonNull Uri uri,@ NonNull String mode){
        String type = getType(uri);
        if (type == null) type = "video/mkv";
        try {
            return openPipeHelper(uri, type, null, uri.getEncodedPath(), this);
        } catch (Exception  e){
            return null;
        }
    }

    public void writeDataToPipe(@NonNull ParcelFileDescriptor output, @NonNull Uri uri, @NonNull String mimeType, Bundle opts, String path) {
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());

        HttpURLConnection urlConnection;
        InputStream in = null;
        int len;
        byte[] buf = new byte[1024];

        try {
            URL url = new URL("http://" + DashCamService.mRPiAddress + ":8888"+uri.getEncodedPath());
            urlConnection = (HttpURLConnection) url.openConnection();

            in = new BufferedInputStream(urlConnection.getInputStream());

            while ((len = in.read(buf)) > 0) {
                fout.write(buf, 0, len);
            }
            fout.flush();
        } catch (Exception e){
            e.printStackTrace();
        }
        finally {
            try {
                if (in != null) in.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                fout.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
