package tk.rabidbeaver.dashcam;

import android.content.Intent;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v7.app.AppCompatActivity;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.view.View;

public class DashCam extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dash_cam);

        SectionsPagerAdapter mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        ViewPager mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new FloatingActionButton.OnClickListener(){
            @Override
            public void onClick(View v) {
                DashCamService.uploadLogs(DashCam.this, Constants.LOG_ID.GPS_LOG);
            }
        });

        if (getIntent() != null && getIntent().getAction() != null && getIntent().getAction().contentEquals(Constants.ACTION.ERROR_ACT)) mViewPager.setCurrentItem(2);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!DashCamService.IS_SERVICE_RUNNING) {
            Intent service = new Intent(DashCam.this, DashCamService.class);
            service.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
            startService(service);
        }
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
                    return "Error Logs";
            }
            return null;
        }
    }
}
