package tk.rabidbeaver.dashcam;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.Hashtable;

public class DashCam extends AppCompatActivity implements MessengerInterface {
    static DashCam dc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dc = DashCam.this;

        setContentView(R.layout.activity_dash_cam);

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        ViewPager mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        HandlerThread handlerThread = new HandlerThread("IPChandlerThread");
        handlerThread.start();
        IncomingHandler handler = new IncomingHandler(handlerThread);
        mClientMessenger = new Messenger(handler);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new FloatingActionButton.OnClickListener(){
            @Override
            public void onClick(View v) {
                uploadLogs();
            }
        });
    }

    private void uploadLogs(){
        if (mBound){
            Message msg = Message.obtain(null, Constants.MESSAGES.UPLOAD_LOGS, 0, 0);
            msg.replyTo = mClientMessenger;
            try {
                mServiceMessenger.send(msg);
            } catch (RemoteException e){
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!DashCamService.IS_SERVICE_RUNNING) {
            Intent service = new Intent(DashCam.this, DashCamService.class);
            service.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
            startService(service);
        }

        if (!mBound){
            bindService(new Intent(DashCam.this, DashCamService.class), mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop(){
        if (mBound){
            unbindService(mConnection);
            mBound=false;
        }
        super.onStop();
    }

    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return new FilesFragment();
                case 1:
                    return new SettingsFragment();
                case 2:
                    return new LogsFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "Files";
                case 1:
                    return "Settings";
                case 2:
                    return "Logs";
            }
            return null;
        }
    }

    private static Messenger mServiceMessenger = null;
    private static Messenger mClientMessenger = null;
    private static boolean mBound = false;
    private static int dataSize = 0;
    private static final Object lock = new Object();
    private static Hashtable<Integer, TextView> store = new Hashtable<>();

    private static class IncomingHandler extends Handler {

        IncomingHandler(HandlerThread thr) {
            super(thr.getLooper());
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == Constants.MESSAGES.DATA_LENGTH){
                dataSize = msg.getData().getInt("length");
                synchronized(lock) {
                    lock.notify();
                }
            } else if (msg.what >= 0){
                Bundle b = msg.getData();
                final TextView dest = store.get(b.getInt("position"));
                final String line = b.getString("time")+": "+b.getString("type")+": "+b.getString("value");

                dc.runOnUiThread(new Runnable(){
                    public void run(){
                        dest.setText(line);
                    }
                });
            }
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mServiceMessenger = new Messenger(service);
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            mServiceMessenger = null;
            mBound = false;
            //synchronized(lock){
                lock.notify();
            //}
        }
    };

    // This just sends command to service to run loadLog()
    public void loadData(){
        if (mBound) {
            Message msg = Message.obtain(null, Constants.MESSAGES.LOAD_DATABASE, 0, 0);
            msg.replyTo = mClientMessenger;
            try {
                mServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public int getSize(){
        int length = 0;
        if (mBound) {
            Message msg = Message.obtain(null, Constants.MESSAGES.DATA_LENGTH, 0, 0);
            msg.replyTo = mClientMessenger;
            try {
                synchronized (lock) {
                    mServiceMessenger.send(msg);
                    lock.wait();
                }
                length = dataSize;
            } catch (RemoteException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        return length;
    }

    public void getItem(int position, TextView dest){
        if (mBound) {
            Message msg = Message.obtain(null, position, 0, 0);
            msg.replyTo = mClientMessenger;
            try {
                store.put(position, dest);
                mServiceMessenger.send(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else {
            dest.setText("");
        }
    }

    public void clearLog(){
        if (mBound){
            Message msg = Message.obtain(null, Constants.MESSAGES.CLEAR_LOG, 0, 0);
            msg.replyTo = mClientMessenger;
            try {
                mServiceMessenger.send(msg);
            } catch (RemoteException e){
                e.printStackTrace();
            }
        }
    }
}
