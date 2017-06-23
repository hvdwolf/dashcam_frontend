package tk.rabidbeaver.dashcam;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;

import static android.content.Context.MODE_PRIVATE;

public class SettingsFragment extends Fragment {
    private SharedPreferences prefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_settings, container, false);

        prefs = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);

        Switch autostart = (Switch) rootView.findViewById(R.id.autostart);
        autostart.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putBoolean("autostart",b);
                prefedit.apply();
            }
        });
        autostart.setChecked(prefs.getBoolean("autostart",true));

        Switch autosave = (Switch) rootView.findViewById(R.id.autosave);
        autosave.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putBoolean("autosave",b);
                prefedit.remove("sendto");
                prefedit.apply();
            }
        });
        autosave.setChecked(prefs.getBoolean("autosave",false));

        Switch gpslog = (Switch) rootView.findViewById(R.id.loggps);
        gpslog.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putBoolean("gpslog",b);
                prefedit.apply();
                Intent service = new Intent(getContext(), DashCamService.class);
                service.setAction(Constants.ACTION.RELOADGPS);
                getContext().startService(service);
            }
        });
        gpslog.setChecked(prefs.getBoolean("gpslog",false));

        Switch autohotspot = (Switch) rootView.findViewById(R.id.autohotspot);
        autohotspot.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putBoolean("autohotspot",b);
                prefedit.apply();
            }
        });
        autohotspot.setChecked(prefs.getBoolean("autohotspot",false));

        return rootView;
    }
}
