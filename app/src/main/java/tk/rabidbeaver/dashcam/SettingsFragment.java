package tk.rabidbeaver.dashcam;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;

import java.io.File;
import java.util.ArrayList;

import static android.content.Context.MODE_PRIVATE;

public class SettingsFragment extends Fragment {
    private LinearLayout deviceView;
    private TextView ffmparams;
    private LayoutInflater inflater;
    private ViewGroup container;
    private FFmpeg ffmpeg;
    private ArrayList<String[][]> cameraOPList = new ArrayList<>();
    private SharedPreferences prefs;
    private Spinner segment_length;
    private boolean from_format_selection = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_settings, container, false);
        deviceView = (LinearLayout) rootView.findViewById(R.id.deviceview);
        ffmparams = (TextView) rootView.findViewById(R.id.ffmparams);
        this.inflater = inflater;
        this.container = container;

        prefs = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
        //prefedit = prefs.edit();

        EditText pathinput = (EditText)rootView.findViewById(R.id.basepath);
        pathinput.setText(prefs.getString("path", "/mnt/external_sdio"));

        pathinput.addTextChangedListener(new TextWatcher(){
            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String mpath = s.toString();
                while (mpath.charAt(mpath.length()-1)=='/') mpath = mpath.substring(0, mpath.length()-1);
                Log.d("SettingsFragment", "PATH:"+mpath);
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putString("path", mpath);
                prefedit.apply();
            }
        });

        ffmpeg = FFmpeg.getInstance(this.getContext());
        try {
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {}

                @Override
                public void onSuccess(){
                    loadDevices();
                }
            });
        } catch (Exception e) {e.printStackTrace();}

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

        Switch internal = (Switch) rootView.findViewById(R.id.loginternal);
        internal.setChecked(prefs.getBoolean("loginternal", false));
        internal.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b){
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putBoolean("loginternal",b);
                prefedit.apply();
            }
        });

        Switch verbose = (Switch) rootView.findViewById(R.id.verbose);
        verbose.setChecked(prefs.getBoolean("verbose", false));
        verbose.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b){
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putBoolean("verbose",b);
                prefedit.apply();
            }
        });

        Switch expert = (Switch) rootView.findViewById(R.id.expert);
        expert.setOnCheckedChangeListener(new Switch.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                LinearLayout deviceview = (LinearLayout) rootView.findViewById(R.id.deviceview);
                TextView ffmparams = (TextView) rootView.findViewById(R.id.ffmparams);
                TextView ffmparams_label = (TextView) rootView.findViewById(R.id.ffmparamslabel);
                Button settings_save = (Button) rootView.findViewById(R.id.setting_save);
                LinearLayout slength_layout = (LinearLayout) rootView.findViewById(R.id.slengthlayout);
                TextView expertinput = (TextView) rootView.findViewById(R.id.expertinput);
                SharedPreferences.Editor prefedit = prefs.edit();
                if (b){
                    deviceview.setVisibility(View.GONE);
                    ffmparams.setVisibility(View.GONE);
                    ffmparams_label.setVisibility(View.GONE);
                    settings_save.setVisibility(View.GONE);
                    slength_layout.setVisibility(View.INVISIBLE);
                    expertinput.setVisibility(View.VISIBLE);
                    prefedit.putBoolean("expert",true);

                } else {
                    deviceview.setVisibility(View.VISIBLE);
                    ffmparams.setVisibility(View.VISIBLE);
                    ffmparams_label.setVisibility(View.VISIBLE);
                    settings_save.setVisibility(View.VISIBLE);
                    slength_layout.setVisibility(View.VISIBLE);
                    expertinput.setVisibility(View.GONE);
                    prefedit.putBoolean("expert",false);
                }
                prefedit.apply();
            }
        });

        expert.setChecked(prefs.getBoolean("expert",false));
        TextView expertInput = (TextView) rootView.findViewById(R.id.expertinput);
        String command = prefs.getString("command","-copyts -f v4l2 -input_format mjpeg -video_size 640x480 -i /dev/video5 -c:v copy -f ssegment -strftime 1 -segment_time 60 -segment_atclocktime 1 -reset_timestamps 1 /mnt/external_sdio/cam_%Y-%m-%d_%H-%M-%S.mkv");
        if (command.length() < 1) command = "-copyts -f v4l2 -input_format mjpeg -video_size 640x480 -i /dev/video5 -c:v copy -f ssegment -strftime 1 -segment_time 60 -segment_atclocktime 1 -reset_timestamps 1 /mnt/external_sdio/cam_%Y-%m-%d_%H-%M-%S.mkv";
        expertInput.setText(command);
        expertInput.addTextChangedListener(new TextWatcher(){
            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putString("command", s.toString());
                prefedit.apply();
            }
        });

        segment_length = (Spinner) rootView.findViewById(R.id.segment_length);
        String lengthSelected = prefs.getString("segment_length","60");
        for (int i=0; i<segment_length.getAdapter().getCount(); i++){
            Log.d("SettingsFragment", (String)segment_length.getAdapter().getItem(i));
            if (((String)segment_length.getAdapter().getItem(i)).contains(lengthSelected)){
                segment_length.setSelection(i);
                break;
            }
        }
        segment_length.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos,long id){
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putString("segment_length", (String)parent.getAdapter().getItem(pos));
                prefedit.apply();
                updateParams();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent){
                parent.setSelection(0);
            }
        });

        Button savebutton = (Button) rootView.findViewById(R.id.setting_save);
        savebutton.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v){
                SharedPreferences.Editor prefedit = prefs.edit();
                prefedit.putString("command", ffmparams.getText().toString());

                for (int i=0; i<prefs.getInt("num_cams", 0); i++){
                    prefedit.remove("cam"+Integer.toString(i)+"_path");
                    prefedit.remove("cam"+Integer.toString(i)+"_format");
                    prefedit.remove("cam"+Integer.toString(i)+"_resolution");
                }

                int selectedCams = 0;
                for (int i=0; i<deviceView.getChildCount(); i++){
                    View w = deviceView.getChildAt(i);
                    if (((CheckBox)w.findViewById(R.id.devbox)).isChecked()){
                        prefedit.putString("cam"+Integer.toString(selectedCams)+"_path", ((TextView)w.findViewById(R.id.devlab)).getText().toString());
                        prefedit.putString("cam"+Integer.toString(selectedCams)+"_format", ((Spinner)w.findViewById(R.id.formatspin)).getSelectedItem().toString());
                        prefedit.putString("cam"+Integer.toString(selectedCams)+"_resolution", ((Spinner)w.findViewById(R.id.resspin)).getSelectedItem().toString());
                        selectedCams++;
                    }
                }
                prefedit.putInt("num_cams", selectedCams);
                prefedit.apply();

                Intent service = new Intent(SettingsFragment.this.getContext(), DashCamService.class);
                service.setAction(Constants.ACTION.ACTION_RESTART);
                SettingsFragment.this.getContext().startService(service);
            }
        });

        return rootView;
    }

    private void loadDevices(){
        // NOTE that this WILL NOT work on AOSP7+ with selinux enforcing, since /dev isn't
        // readable for untrusted_app.
        File devfs = new File("/dev");
        File[] devices = devfs.listFiles();
        //File[] devices = new File[2];
        //devices[0] = new File("/dev/video0");
        //devices[1] = new File("/dev/video1");

        LinearLayout device;

        if (devices != null){
            Log.d("SettingsFragment", "devices != null");
            for (File f : devices){
                Log.d("SettingsFragment", f.getName());
                if (/*f.isFile() && */f.getName().contains("video")) {
                    Log.d("SettingsFragment", "name contains video");
                    device = (LinearLayout) inflater.inflate(R.layout.device, container, false);
                    final TextView devLabel = (TextView) device.findViewById(R.id.devlab);
                    final CheckBox devbox = (CheckBox) device.findViewById(R.id.devbox);
                    String devicepath = "/dev/" + f.getName();
                    devLabel.setText(devicepath);

                    final TextView devpar = (TextView) device.findViewById(R.id.devpar);

                    String[][] cameraOP = null;

                    try {
                        cameraOP = ffmpeg.getCameraOutputFormats(devicepath);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (cameraOP != null && cameraOP.length > 0 && cameraOP[0] != null && cameraOP[0].length > 0 && cameraOP[0][0] != null) {

                        if (cameraOP[0][0].contains("Raw")) {
                            String[] tmp = cameraOP[0];
                            cameraOP[0] = cameraOP[1];
                            cameraOP[1] = tmp;
                        }

                        final String[] formats = new String[cameraOP.length];
                        for (int j = 0; j < cameraOP.length; j++) formats[j] = cameraOP[j][1];

                        ArrayAdapter<String> sadapt = new ArrayAdapter<>(this.getContext(), android.R.layout.simple_spinner_item, formats);
                        sadapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        final Spinner format = (Spinner) device.findViewById(R.id.formatspin);
                        format.setAdapter(sadapt);
                        format.setLabelFor(cameraOPList.size()); // abuse the "labelFor" attribute to store array index.

                        final String[] resolutions = new String[cameraOP[0].length - 2];
                        for (int j = 0; j < cameraOP[0].length - 2; j++)
                            resolutions[j] = cameraOP[0][j + 2];

                        cameraOPList.add(cameraOP);

                        final Spinner resolution = (Spinner) device.findViewById(R.id.resspin);
                        ArrayAdapter<String> radapt = new ArrayAdapter<>(this.getContext(), android.R.layout.simple_spinner_item, resolutions);
                        radapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        resolution.setAdapter(radapt);

                        format.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                                String[][] cameraOP = cameraOPList.get(parent.getLabelFor());
                                if (cameraOP != null) {
                                    String[] resolutions = new String[cameraOP[pos].length - 2];
                                    for (int j = 0; j < cameraOP[pos].length - 2; j++)
                                        resolutions[j] = cameraOP[pos][j + 2];
                                    ArrayAdapter<String> radapt = new ArrayAdapter<>(SettingsFragment.this.getContext(), android.R.layout.simple_spinner_item, resolutions);
                                    radapt.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                    from_format_selection = true;
                                    resolution.setAdapter(radapt);
                                }
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                            }
                        });

                        resolution.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                                if (from_format_selection) {

                                    String path = ((TextView) ((View) parent.getParent()).findViewById(R.id.devlab)).getText().toString();
                                    int numdev = prefs.getInt("num_cams", 0);
                                    for (int i = 0; i < numdev; i++) {
                                        if (prefs.getString("cam" + Integer.toString(i) + "_path", "").contentEquals(path)) {
                                            String setres = prefs.getString("cam" + Integer.toString(i) + "_resolution", "640x480");
                                            for (int j = 0; j < resolutions.length; j++)
                                                if (resolutions[j].contentEquals(setres))
                                                    resolution.setSelection(j);
                                            break;
                                        }
                                    }

                                    from_format_selection = false;
                                }
                                devpar.setText("-f v4l2 -input_format " + formats[format.getSelectedItemPosition()] + " -video_size " + resolutions[pos] + " -i " + devLabel.getText() + " -c:v copy");
                                updateParams();
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                            }
                        });

                        for (int j = 0; j < prefs.getInt("num_cams", 0); j++) {
                            String path = prefs.getString("cam" + Integer.toString(j) + "_path", "");
                            if (path.contentEquals(devicepath)) {
                                devbox.setChecked(true);
                                String nformat = prefs.getString("cam" + Integer.toString(j) + "_format", "");
                                for (int k = 0; k < formats.length; k++) {
                                    if (formats[k].contentEquals(nformat)) {
                                        format.setSelection(k);
                                        break;
                                    }
                                }

                                break;
                            }
                        }

                        devpar.setText("-f v4l2 -input_format " + formats[format.getSelectedItemPosition()] + " -video_size " + resolutions[resolution.getSelectedItemPosition()] + " -i " + devLabel.getText() + " -c:v copy");

                        devbox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton cb, boolean b) {
                                updateParams();
                            }
                        });

                        deviceView.addView(device);
                    }
                }
            }
        }
        updateParams();
    }

    private void updateParams(){
        String params = "-regents";
        int selectedCams = 0;
        for (int i=0; i<deviceView.getChildCount(); i++){
            View v = deviceView.getChildAt(i);
            if (((CheckBox)v.findViewById(R.id.devbox)).isChecked()){
                params+=" "+((TextView)v.findViewById(R.id.devpar)).getText();
                selectedCams++;
            }
        }

        for (int i=0; selectedCams > 1 && i < selectedCams; i++) params += " -map " + Integer.toString(i);

        params += " -f ssegment -strftime 1 -segment_time "+segment_length.getSelectedItem()+" -segment_atclocktime 1 -reset_timestamps 1 "+prefs.getString("path", "/mnt/external_sdio")+"/cam_%Y-%m-%d_%H-%M-%S.mkv";
        ffmparams.setText(params);
    }
}
