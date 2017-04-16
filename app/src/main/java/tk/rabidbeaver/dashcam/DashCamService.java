package tk.rabidbeaver.dashcam;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.support.v7.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DashCamService extends Service {

    private static final String LOG_TAG = "DashCamService";
    protected static boolean IS_SERVICE_RUNNING = false;
    private NotificationCompat.Builder notification;
    private FFmpeg ffmpeg;
    private AlarmManager alarmManager;
    private PendingIntent cleanupIntent;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private boolean verboseLogging = false;
    private static SQLiteDatabase db = null;
    private SharedPreferences prefs;
    private static Cursor logCursor;
    final Messenger mLogMessenger = new Messenger(new LogHandler());
    private boolean autostarter = false;
    private boolean stopped = false;

    @Override
    public void onCreate() {
        super.onCreate();
        ffmpeg = FFmpeg.getInstance(DashCamService.this);
        loadFFMpegBinary();
        setupGpsListeners();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getSharedPreferences("Settings", MODE_MULTI_PROCESS).getBoolean("autostart", true)) autostarter = true;
        if (!IS_SERVICE_RUNNING) {
            showNotification(autostarter);
        } else {
            if (intent.getAction().equals(Constants.ACTION.ACTION_RECORD) || (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION) && autostarter)) {
                stopped=false;
                startFFmpeg();
            } else if (intent.getAction().equals(Constants.ACTION.ACTION_STOP)) {
                stopped=true;
                stopFFmpeg();
            } else if (intent.getAction().equals(Constants.ACTION.ACTION_RESTART)){
                stopFFmpeg();
                stopped=false;
                startFFmpeg();
            } else if (intent.getAction().equals(Constants.ACTION.ACTION_CLEANDB)){
                long lastModified = intent.getLongExtra("cleanup_threshold", 0);
                if (lastModified > 0 && db != null){
                    db.execSQL("DELETE FROM log WHERE time <= datetime("+lastModified+", 'unixepoch', 'localtime')");
                    db.execSQL("DELETE FROM gps WHERE time <= datetime("+lastModified+", 'unixepoch', 'localtime')");
                }
            }
        }
        return START_STICKY;
    }

    private void setupGpsListeners(){
        locationListener = new LocationListener() {

            // Processed coordinates
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                //makeUseOfNewLocation(location);
                Log.d("DashCam GPS", new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z").format(new Date(location.getTime())) + ": " + location.getLatitude() + ", " + location.getLongitude() + ", accuracy: (" + location.getAccuracy() + " m), altitude: (" + location.getAltitude() + " m over WGS 84), bearing: (" + location.getBearing() + " deg), speed: (" + location.getSpeed() + " m/s), satellites: (" + location.getExtras().getInt("satellites") + ")");
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                if (status == LocationProvider.AVAILABLE){
                    Log.d("DashCam GPS", "GPS fix acquired");
                } else {
                    Log.d("DashCam GPS", "No valid GPS fix");
                }
            }

            public void onProviderEnabled(String provider) {}
            public void onProviderDisabled(String provider) {}
        };
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Details about the actual satellites used in the current "fix"
                /*locationManager.addGpsStatusListener(new GpsStatus.Listener(){
                    @Override
                    public void onGpsStatusChanged(int event){
                        Log.d("DashCam GPS", "event: "+Integer.toString(event));
                        gs=locationManager.getGpsStatus(gs);
                        if (gs != null){
                            Iterable<GpsSatellite>satellites = gs.getSatellites();
                            Iterator<GpsSatellite> sat = satellites.iterator();
                            while (sat.hasNext()) {
                                GpsSatellite satellite = sat.next();
                                Log.d("DashCam GPS", satellite.getPrn() + "," + satellite.usedInFix() + "," + satellite.getSnr() + "," + satellite.getAzimuth() + "," + satellite.getElevation());
                            }
                        }
                    }
                });*/

        // Raw NMEA stream (this is good for location logging)
        locationManager.addNmeaListener(new GpsStatus.NmeaListener(){
            @Override
            public void onNmeaReceived(long timestamp, String nmea){
                writeGpsLog(nmea);
                Log.d("DashCam GPS", new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z").format(new Date(timestamp))+": NMEA: "+nmea);
                //TODO: The following actually runs startFFmpeg() every about 1 second.
                // seems that ffmpeg.isFFmpegCommandRunning() is returning incorrect values
                // AND, this onNmeaReceived gets triggered more than every update, rather it gets triffered
                // about 15 times per second. The former could be a side-effect of the latter.
                // TODO: if (timestamp%15 == 0)? -- timestamp is in millisecond.

                if (!stopped && autostarter && (((timestamp / 1000) % 15) == 0) && !ffmpeg.isFFmpegCommandRunning()){
                    startFFmpeg();
                }
            }
        });
    }

    private void showNotification(boolean start) {
        Intent notificationIntent = new Intent(this, DashCam.class);
        notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.lens);

        notification = new NotificationCompat.Builder(this);
        notification.setContentTitle("DashCam Recorder")
            .setTicker("DashCam Recorder")
            .setSmallIcon(R.drawable.ic_videocam_off)
            .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
            .setContentIntent(pendingIntent)
            .setOngoing(true);

        startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification.build());
        IS_SERVICE_RUNNING=true;

        prefs = getSharedPreferences("Settings", MODE_MULTI_PROCESS);
        String path=prefs.getString("path", "/mnt/external_sdio");
        try {
            boolean internalLogging = prefs.getBoolean("loginternal", false);
            Context dbc = getApplicationContext();
            if (!internalLogging) dbc = new DatabaseContext(dbc);
            String dbpath = (internalLogging?"":path+"/")+"dashcam.db";
            db = new SQLiteOpenHelper(dbc, dbpath, null, Constants.VALUES.DATABASE_VERSION){
                public void onCreate(SQLiteDatabase db) {
                    // note %f fractional (1/1000th) seconds
                    db.execSQL("CREATE TABLE log (time TEXT PRIMARY KEY DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')), type TEXT, value TEXT)");
                    db.execSQL("CREATE TABLE gps (time TEXT PRIMARY KEY DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')), value TEXT)");
                }

                public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                    // This is still first version, so there isn't anything to upgrade to.
                    // DO NOTHING.
                }
            }.getWritableDatabase();
        } catch (Exception e){
            e.printStackTrace();
            db = null;
        }

        //if set to auto-record, then do this:
        if (start) startFFmpeg();
    }

    private void startFFmpeg(){
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        String path=prefs.getString("path", "/mnt/external_sdio");

        verboseLogging = prefs.getBoolean("verbose", false);

        // Fire cleanup alarm every X minutes
        int microseconds = prefs.getInt("cleancycle", 5) * 60 * 1000; // default 5 minutes = 300000 microseconds
        alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, CleanupReceiver.class);
        // extra named "minfree" carries the percent free space that must be maintained.
        intent.putExtra("minfree", prefs.getInt("minfree", 25));
        intent.putExtra("path", path);
        cleanupIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + (5 * 60 * 1000),
                microseconds, cleanupIntent);

        //NOTE: This ffmpeg library has a timeout function. Its default value is Long.MAX_VALUE
        //But the logic behind it is very nice, it essentially disables itself if timeout == Long.MAX_VALUE,
        //so it will never timeout.
        if (!ffmpeg.isFFmpegCommandRunning()) {
            String cmd = prefs.getString("command","-copyts -f v4l2 -input_format mjpeg -video_size 640x480 -i /dev/video5 -c:v copy -f ssegment -strftime 1 -segment_time 60 -segment_atclocktime 1 -reset_timestamps 1 /mnt/external_sdio/cam_%Y-%m-%d_%H-%M-%S.mkv");
            if (cmd.length() < 1) cmd = "-copyts -f v4l2 -input_format mjpeg -video_size 640x480 -i /dev/video5 -c:v copy -f ssegment -strftime 1 -segment_time 60 -segment_atclocktime 1 -reset_timestamps 1 /mnt/external_sdio/cam_%Y-%m-%d_%H-%M-%S.mkv";
            String[] command = cmd.split(" ");
            if (command.length != 0) {
                execFFmpegBinary(command);
            } else {
                Toast.makeText(this, "No Command Given", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void updateNotification(Boolean recording) {
        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), DashCam.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.setContentText(recording?"Recording in Progress":"Recording Stopped");
        notification.mActions.clear();
        if (recording){
            notification.setSmallIcon(R.drawable.ic_videocam);
            Intent stopIntent = new Intent(this, DashCamService.class);
            stopIntent.setAction(Constants.ACTION.ACTION_STOP);
            PendingIntent pStopIntent = PendingIntent.getService(this, 0, stopIntent, 0);
            notification.addAction(android.R.drawable.ic_media_pause, "Stop", pStopIntent);
        } else {
            notification.setSmallIcon(R.drawable.ic_videocam_off);
            Intent recordIntent = new Intent(this, DashCamService.class);
            recordIntent.setAction(Constants.ACTION.ACTION_RECORD);
            PendingIntent pRecordIntent = PendingIntent.getService(this, 0, recordIntent, 0);
            notification.addAction(android.R.drawable.ic_media_play, "Record", pRecordIntent);
        }
        notification.setContentIntent(pi);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification.build());

        // Cancel the cleanup alarm if we stop recording.
        if (!recording && alarmManager != null) alarmManager.cancel(cleanupIntent);
    }

    private void stopFFmpeg(){
        if (ffmpeg.isFFmpegCommandRunning()) ffmpeg.killRunningProcesses();
        updateNotification(false); // I think that killRunningProcesses doesn't always run its callback.
    }

    private void loadFFMpegBinary() {
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    Toast.makeText(DashCamService.this, "FFMPEG Load Binary Failed", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (FFmpegNotSupportedException e) {
            Toast.makeText(this, "FFMPEG Load Binary Failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void execFFmpegBinary(final String[] command) {
        try {
            ffmpeg.execute(command, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String s) {
                    Log.d(LOG_TAG, "FAILED with output : "+s);
                    writelog("E", s);
                    updateNotification(false);
                    locationManager.removeUpdates(locationListener);
                }

                @Override
                public void onSuccess(String s) {
                    Log.d(LOG_TAG, "SUCCESS with output : "+s);
                    writelog("S", s);
                    updateNotification(false);
                    locationManager.removeUpdates(locationListener);
                }

                @Override
                public void onProgress(String s) {
                    if (verboseLogging) writelog("P", s);
                }

                @Override
                public void onStart() {
                    Log.d(LOG_TAG, "Started command : ffmpeg " + TextUtils.join(" ", command));
                    writelog("S", "ffmpeg " + TextUtils.join(" ", command));
                    updateNotification(true);
                }

                @Override
                public void onFinish() {
                    Log.d(LOG_TAG, "Finished command : ffmpeg "+ TextUtils.join(" ", command));
                    writelog("F", "Command completed");
                    updateNotification(false);
                    locationManager.removeUpdates(locationListener);
                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {e.printStackTrace();}
    }

    private void writelog(String code, String value){
        if (db != null) {
            for(String val : value.split("\n")) {
                ContentValues cv = new ContentValues();
                cv.put("type", code);
                cv.put("value", val);
                db.insert("log", null, cv);
            }
        }
    }

    private void writeGpsLog(String s){
        if (db != null){
            ContentValues cv = new ContentValues();
            cv.put("value", s.replace("\n",""));
            db.insert("gps", null, cv);
        }
    }

    public static void loadLog(){
        if (db != null){
            String[] columns = {"time", "type", "value"};
            logCursor = db.query("log", columns, null, null, null, null, "time DESC");
        } else logCursor = null;
    }

    @Override
    public void onDestroy() {
        // This will probably be executed after ignition is off for a few minutes.
        super.onDestroy();
        stopFFmpeg();
        Log.d(LOG_TAG, "In onDestroy");
        IS_SERVICE_RUNNING=false;
    }

    private class DatabaseContext extends ContextWrapper {

        public DatabaseContext(Context base) {
            super(base);
        }

        @Override
        public File getDatabasePath(String name)  {

            File file = new File(name);

            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            return file;
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
            return openOrCreateDatabase(name, mode, factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), null);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mLogMessenger.getBinder();
    }

    static class LogHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == Constants.MESSAGES.LOAD_DATABASE) loadLog();
            else if (msg.what == Constants.MESSAGES.DATA_LENGTH){
                Message m = new Message();
                Bundle b = new Bundle();
                if (logCursor == null) loadLog();
                try {
                    b.putInt("length", logCursor.getCount());
                } catch (Exception e){
                    b.putInt("length", 0);
                }
                m.setData(b);
                m.what = msg.what;
                try {
                    msg.replyTo.send(m);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (msg.what == Constants.MESSAGES.CLEAR_LOG){
                if (db != null){
                    db.execSQL("DELETE FROM log");
                }
            } else {
                logCursor.moveToPosition(msg.what);

                Message m = new Message();
                Bundle b = new Bundle();
                b.putString("time", logCursor.getString(0));
                b.putString("type", logCursor.getString(1));
                b.putString("value", logCursor.getString(2));
                m.setData(b);
                m.what = msg.what;
                try {
                    msg.replyTo.send(m);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
