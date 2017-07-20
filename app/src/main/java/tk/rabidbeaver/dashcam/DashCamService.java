package tk.rabidbeaver.dashcam;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.display.DisplayManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.view.Display;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class DashCamService extends Service {

    private static final String LOG_TAG = "DashCamService";
    protected static boolean IS_SERVICE_RUNNING = false;
    private NotificationCompat.Builder notification;
    private boolean autostarter = false;
    private boolean dispose = false;
    private static boolean forcestopped = false;

    private static final int DATABASE_VERSION = 1;

    private LocationManager locationManager;
    private LocationListener locationListener;

    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdServiceInfo mServiceInfo;
    public static String mRPiAddress = "";
    private boolean autohotspot = false;
    private static boolean gpslogpi = false;
    private boolean gpslog = false;

    protected SQLiteDatabase db = null;

    // The NSD service type that the RPi exposes.
    private static final String SERVICE_TYPE = "_workstation._tcp.";

    @Override
    public void onCreate() {
        super.onCreate();
        mRPiAddress = "";

        db = new SQLiteOpenHelper(this, "dashcam.db", null, DATABASE_VERSION){
            public void onCreate(SQLiteDatabase db) {
                // note %f fractional (1/1000th) seconds
                db.execSQL("CREATE TABLE gps (time TEXT PRIMARY KEY DEFAULT(STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW')), value TEXT)");
            }
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                // This is still first version, so there isn't anything to upgrade to.
                // DO NOTHING.
            }
        }.getWritableDatabase();

        mNsdManager = (NsdManager)(getApplicationContext().getSystemService(Context.NSD_SERVICE));
        initializeDiscoveryListener();
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getAction().equals(Constants.ACTION.STOPSERVICE)){
            stopFFmpeg();
            dispose = true;
            IS_SERVICE_RUNNING=false;
            if (ApManager.isApOn(getApplicationContext())) ApManager.configApState(getApplicationContext());
            stopSelf();
            return START_STICKY;
        }

        SharedPreferences prefs = getSharedPreferences("Settings", MODE_PRIVATE);
        if (prefs.getBoolean("autostart", true)) autostarter = true;
        if (prefs.getBoolean("autohotspot", false)) autohotspot = true;
        if (prefs.getBoolean("gpslogpi", false)) gpslogpi = true;
        gpslog = prefs.getBoolean("gpslog",false);

        if (gpslog) {
            setupGpsListeners();
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        } else if (locationManager != null){
            locationManager.removeUpdates(locationListener);
            locationManager = null;
            locationListener = null;
        }

        if (!IS_SERVICE_RUNNING) {
            showNotification();
        } else {
            if (intent.getAction().equals(Constants.ACTION.ACTION_RECORD) || (intent.getAction().equals(Constants.ACTION.STARTFOREGROUND_ACTION) && autostarter)) {
                startFFmpeg();
            } else if (intent.getAction().equals(Constants.ACTION.ACTION_STOP)) {
                stopFFmpeg();
            }
        }
        return START_STICKY;
    }

    private void mainLooper(){
        new Thread(new Runnable() {
            public void run() {
                BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                dispose = false;
                int counter = 0;
                int btCounter = 0;
                boolean hotspotTerminated = false;
                boolean weForceStopped = false;
                while (!dispose) {
                    if (!mBluetoothAdapter.isEnabled() && btCounter < 5) btCounter++;
                    else if (mBluetoothAdapter.isEnabled()){
                        btCounter = 0;
                        hotspotTerminated = false;
                    }

                    if (btCounter < 5) {
                        if (autohotspot && !ApManager.isApOn(getApplicationContext()))
                            ApManager.configApState(getApplicationContext());
                        checkFFmpeg();
                        if (weForceStopped){
                            startFFmpeg();
                            weForceStopped = false;
                        }

                        // REAP the gps database
                        if (counter == 0 && db != null)
                            db.rawQuery("DELETE FROM gps WHERE time < (SELECT time FROM gps ORDER BY time DESC LIMIT 1 OFFSET 1000000)", null);
                        else if (counter >= 720) counter = 0;
                        else counter++;
                    } else if (!hotspotTerminated){
                        stopFFmpeg();
                        weForceStopped = true;
                        if (ApManager.isApOn(getApplicationContext())) ApManager.configApState(getApplicationContext());
                        hotspotTerminated = true;
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @SuppressWarnings("deprecation")
    private void setupGpsListeners(){
        locationListener = new LocationListener() {
            // Processed coordinates
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                Log.d("DashCam GPS", new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z", Locale.US).format(new Date(location.getTime())) + ": " + location.getLatitude() + ", " + location.getLongitude() + ", accuracy: (" + location.getAccuracy() + " m), altitude: (" + location.getAltitude() + " m over WGS 84), bearing: (" + location.getBearing() + " deg), speed: (" + location.getSpeed() + " m/s), satellites: (" + location.getExtras().getInt("satellites") + ")");
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
        // NOTE: the following is deprecated, but its replacement isn't added until API 24 -- we target API 22.
        locationManager.addNmeaListener(new GpsStatus.NmeaListener(){
            @Override
            public void onNmeaReceived(long timestamp, String nmea){
                writeGpsLog(nmea);
                Log.d("DashCam GPS", new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z", Locale.US).format(new Date(timestamp))+": NMEA: "+nmea);
            }
        });
    }

    private void writeGpsLog(final String s){
        if (gpslogpi){
            new Thread(new Runnable() {
                String POSTDATA;
                public void run() {
                    String response = "";
                    HttpURLConnection urlConnection = null;

                    POSTDATA = "gpslog="+s+":"+System.currentTimeMillis()/1000;
                    URL url;
                    try {
                        url = new URL("http://"+mRPiAddress+":8888");

                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setConnectTimeout(1000);
                        urlConnection.setReadTimeout(1000);
                        urlConnection.setRequestMethod("POST");
                        urlConnection.setDoOutput(true);

                        OutputStream os = urlConnection.getOutputStream();
                        os.write(POSTDATA.getBytes());
                        os.flush();
                        os.close();
                        // For POST only - END

                        int responseCode = urlConnection.getResponseCode();
                        Log.d("GPSLOG","code: " + responseCode);

                        if (responseCode == HttpURLConnection.HTTP_OK) { //success
                            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                            String inputLine;

                            while ((inputLine = in.readLine()) != null) {
                                response+=inputLine;
                            }
                            in.close();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Log.d("GPSLOG",response);
                    if (urlConnection != null) urlConnection.disconnect();
                }
            }).start();
        } else if (db != null){
            ContentValues cv = new ContentValues();
            cv.put("value", s.replace("\n",""));
            db.insert("gps", null, cv);
        }
    }

    private void showNotification() {
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

        updateNotification(false, false);
        mainLooper();
    }

    private void startFFmpeg(){
        forcestopped = false;
        new Thread(new Runnable() {
            public void run() {
                Log.d("RECORD","running startFFmpeg");
                String response = "";
                HttpURLConnection urlConnection = null;

                Long tsLong = System.currentTimeMillis()/1000;
                String POSTDATA = "record="+tsLong.toString();
                if (gpslogpi && !gpslog) POSTDATA = "record=gps";

                HashMap<String, String> mXml = null;

                try {
                    URL url = new URL("http://"+mRPiAddress+":8888/record");
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setDoOutput(true);
                    OutputStream os = urlConnection.getOutputStream();
                    os.write(POSTDATA.getBytes());
                    os.flush();
                    os.close();
                    // For POST only - END

                    int responseCode = urlConnection.getResponseCode();
                    Log.d("RECORD","code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) //success
                        mXml = XmlParser.parse(urlConnection.getInputStream(), "ffmpeg");

                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d("RECORD",response);
                if (urlConnection != null) urlConnection.disconnect();

                if (mXml != null && mXml.containsKey("status") && mXml.get("status").contentEquals("running")) updateNotification(true, true);
            }
        }).start();
    }

    private void stopFFmpeg(){
        forcestopped = true;
        new Thread(new Runnable() {
            public void run() {
                String response = "";
                HttpURLConnection urlConnection = null;
                HashMap<String, String> mXml = null;
                try {
                    URL url = new URL("http://"+mRPiAddress+":8888/stop");
                    urlConnection = (HttpURLConnection) url.openConnection();

                    int responseCode = urlConnection.getResponseCode();
                    Log.d("RECORD","code: " + responseCode);

                    if (responseCode == HttpURLConnection.HTTP_OK) //success
                        mXml = XmlParser.parse(urlConnection.getInputStream(), "ffmpeg");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d("STOP",response);
                if (urlConnection != null) urlConnection.disconnect();

                if (mXml != null && mXml.containsKey("status") && mXml.get("status").contentEquals("terminated")) updateNotification(true, false);
            }
        }).start();
    }

    private void checkFFmpeg(){
        String response = "";
        HttpURLConnection urlConnection;
        HashMap<String, String> mXml = null;
        try {
            URL url = new URL("http://" + mRPiAddress + ":8888/check");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(2000);
            urlConnection.setReadTimeout(2000);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }

        try {
            urlConnection.connect();
        } catch (SocketTimeoutException ste) {
            ste.printStackTrace();
            return;
        } catch (IOException ioe) {
            updateNotification(false, false);
            ioe.printStackTrace();
            return;
        }

        try {
            int responseCode = urlConnection.getResponseCode();
            Log.d("RECORD","code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) //success
                mXml = XmlParser.parse(urlConnection.getInputStream(), "ffmpeg");
        } catch (IOException ioe){
            ioe.printStackTrace();
        }
        Log.d("CHECK",response);
        urlConnection.disconnect();

        if (mXml != null && mXml.containsKey("status") && mXml.get("status").contentEquals("running")) updateNotification(true, true);
        else if (!forcestopped) startFFmpeg();
        else updateNotification(true, false);
    }

    private void updateNotification(Boolean connected, Boolean recording) {
        if (notification == null) showNotification();

        PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), DashCam.class),
                PendingIntent.FLAG_UPDATE_CURRENT);

        if (!connected){
            notification.setContentText("Connecting...");
            notification.mActions.clear();
            notification.setSmallIcon(R.drawable.ic_videocam_nc);
        } else {
            if (recording) {
                notification.setContentText("Recording in Progress");
                notification.mActions.clear();
                notification.setSmallIcon(R.drawable.ic_videocam);
                Intent stopIntent = new Intent(this, DashCamService.class);
                stopIntent.setAction(Constants.ACTION.ACTION_STOP);
                PendingIntent pStopIntent = PendingIntent.getService(this, 0, stopIntent, 0);
                notification.addAction(android.R.drawable.ic_media_pause, "Stop", pStopIntent);
            } else {
                notification.setContentText("Recording Stopped");
                notification.mActions.clear();
                notification.setSmallIcon(R.drawable.ic_videocam_off);
                Intent recordIntent = new Intent(this, DashCamService.class);
                recordIntent.setAction(Constants.ACTION.ACTION_RECORD);
                PendingIntent pRecordIntent = PendingIntent.getService(this, 0, recordIntent, 0);
                notification.addAction(android.R.drawable.ic_media_play, "Record", pRecordIntent);
            }
        }

        notification.setContentIntent(pi);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE, notification.build());
    }

    @Override
    public void onDestroy() {
        // This will probably be executed after ignition is off for a few minutes.
        super.onDestroy();
        stopFFmpeg();
        dispose = true;
        mRPiAddress = "";
        Log.d(LOG_TAG, "In onDestroy");
        IS_SERVICE_RUNNING=false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected static void uploadLogs(Context ctx){
        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        SharedPreferences st = ctx.getSharedPreferences("Settings", MODE_PRIVATE);

        String send = st.getString("sendto","");

        if (gpslogpi)
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://tk.rabidbeaver.dashcam.DataProvider/gps.db"));
        else
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://tk.rabidbeaver.dashcam.DataProvider/databases/dashcam.db"));
        intent.putExtra(Intent.EXTRA_SUBJECT, "dashcam_"+new SimpleDateFormat("yyyy.MM.dd HH:mm:ss z", Locale.US).format(new Date())+".db");

        if (send.length() == 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1){
            Intent rIntent = new Intent(ctx, SendSelectionReceiver.class);
            PendingIntent pIntent = PendingIntent.getBroadcast(ctx, 0, rIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            ctx.startActivity(Intent.createChooser(intent, "Send Logs to...", pIntent.getIntentSender()));
        } else {
            if (send.length() > 0){
                String[] cmp = send.split("/");
                intent.setComponent(new ComponentName(cmp[0], cmp[1]));
            }
            ctx.startActivity(intent);
        }
    }

    private void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                String name = service.getServiceName();
                String type = service.getServiceType();
                Log.d("NSD", "Service Name=" + name);
                Log.d("NSD", "Service Type=" + type);
                if (type.equals(SERVICE_TYPE) && name.contains("pizwcam")) {
                    Log.d("NSD", "Service Found @ '" + name + "'");
                    mNsdManager.resolveService(service, getResolveListener());
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }

    private NsdManager.ResolveListener getResolveListener() {
        return new NsdManager.ResolveListener() {

            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                // Called when the resolve fails.  Use the error code to debug.
                Log.e("NSD", "Resolve failed" + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                mServiceInfo = serviceInfo;

                // Port is being returned as 9. Not needed.
                //int port = mServiceInfo.getPort();

                InetAddress host = mServiceInfo.getHost();
                String address = host.getHostAddress();
                Log.d("NSD", "Resolved address = " + address);

                mRPiAddress = address;

                if (autostarter && !forcestopped) startFFmpeg();
            }
        };
    }
}
