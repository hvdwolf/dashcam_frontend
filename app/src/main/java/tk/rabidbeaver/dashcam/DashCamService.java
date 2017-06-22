package tk.rabidbeaver.dashcam;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

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

public class DashCamService extends Service {

    private static final String LOG_TAG = "DashCamService";
    protected static boolean IS_SERVICE_RUNNING = false;
    private NotificationCompat.Builder notification;
    private boolean autostarter = false;
    private boolean dispose = false;
    private boolean forcestopped = false;

    private NsdManager mNsdManager;
    private NsdManager.DiscoveryListener mDiscoveryListener;
    private NsdServiceInfo mServiceInfo;
    public static String mRPiAddress = "";

    // The NSD service type that the RPi exposes.
    private static final String SERVICE_TYPE = "_workstation._tcp.";

    @Override
    public void onCreate() {
        super.onCreate();
        mRPiAddress = "";
        forcestopped = false;
        mNsdManager = (NsdManager)(getApplicationContext().getSystemService(Context.NSD_SERVICE));
        initializeDiscoveryListener();
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getSharedPreferences("Settings", MODE_PRIVATE).getBoolean("autostart", true)) autostarter = true;
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
                dispose = false;
                while (!dispose) {
                    checkFFmpeg();
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }).start();
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
                Log.d("RECORD",response);
                if (urlConnection != null) urlConnection.disconnect();

                if (response.contains("<ffmpeg status=\"running\" />")) updateNotification(true, true);
            }
        }).start();
    }

    private void stopFFmpeg(){
        forcestopped = true;
        new Thread(new Runnable() {
            public void run() {
                String response = "";
                HttpURLConnection urlConnection = null;
                try {
                    URL url = new URL("http://"+mRPiAddress+":8888/stop");
                    urlConnection = (HttpURLConnection) url.openConnection();

                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = br.readLine()) != null) {
                        response += line;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.d("STOP",response);
                if (urlConnection != null) urlConnection.disconnect();

                if (response.contains("<ffmpeg status=\"terminated\" />")) updateNotification(true, false);
            }
        }).start();
    }

    private void checkFFmpeg(){
        String response = "";
        HttpURLConnection urlConnection;
        try {
            URL url = new URL("http://" + mRPiAddress + ":8888/check");
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(2000);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }

        try {
            urlConnection.connect();
        } catch (SocketTimeoutException ste) {
            ste.printStackTrace();
            updateNotification(false, false);
            return;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return;
        }

        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                response += line;
            }
        } catch (IOException ioe){
            ioe.printStackTrace();
        }
        Log.d("CHECK",response);
        urlConnection.disconnect();

        //TODO: this is an ugly way to use XML...
        if (response.contains("<ffmpeg status=\"running\" />")) updateNotification(true, true);
        else if (!forcestopped) startFFmpeg();
        else updateNotification(true, false);
    }

    private void updateNotification(Boolean connected, Boolean recording) {
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
                //dispose = true;
                //mRPiAddress = "";
                //updateNotification(false);
                //TODO: I dont think this is the network service being lost, I think its the NSD being lost.
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
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

                if (autostarter) startFFmpeg();
            }
        };
    }
}
