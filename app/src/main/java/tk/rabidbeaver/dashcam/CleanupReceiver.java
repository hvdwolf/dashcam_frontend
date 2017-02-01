package tk.rabidbeaver.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Note: This receiver is defined in the manifest using
// android:process="tk.rabidbeaver.dashcam.cleanupreceiver"
// so it should run in its own process.
public class CleanupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        // Get the data from intent
        int minfree = intent.getIntExtra("minfree", 25);
        File path = new File(intent.getStringExtra("path"));

        // Read the directory into an ArrayList
        List<File> files = new ArrayList<>(Arrays.asList(path.listFiles(new FilenameFilter(){
            public boolean accept(File path, String name){
                return name.endsWith(".mkv");
            }
        })));

        // Sort the ArrayList according to lastModified time
        Collections.sort(files, new Comparator<File>(){
            public int compare(File f1, File f2){
                if (f1.lastModified() < f2.lastModified()) return -1;
                else if (f1.lastModified() > f2.lastModified()) return 1;
                return 0;
            }
        });

        // Calculate amount of cleanup required for filesystem
        StatFs sdcard = new StatFs(path.getAbsolutePath());
        long totalBytes = sdcard.getTotalBytes();
        long freeBytes = sdcard.getFreeBytes();
        long reqFreeBytes = (long)(((float)minfree)/100.0 * (float) totalBytes);

        // Delete oldest data until the required amount has been achieved
        if (freeBytes < reqFreeBytes){
            long recover = reqFreeBytes - freeBytes;
            long recovered = 0;
            long lastModified = 0;
            while (recovered < recover && !files.isEmpty()){
                File f = files.remove(0);
                recovered += f.length();
                lastModified = f.lastModified();
                if (!f.delete()) Log.d("FilesFragment", "error");
            }

            if (lastModified > 0){
                // Send the last modified time of the last deleted file (newest file) to the
                // service, so it can clean old logs out of the database.
                Intent service = new Intent(context, DashCamService.class);
                service.setAction(Constants.ACTION.ACTION_CLEANDB);
                service.putExtra("cleanup_threshold", lastModified);
                context.startService(service);
            }
        }
    }
}
