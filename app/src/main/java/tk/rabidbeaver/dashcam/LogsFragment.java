package tk.rabidbeaver.dashcam;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import static android.content.Context.MODE_PRIVATE;

public class LogsFragment extends Fragment {
    private SwipeRefreshLayout pullrefresher;
    private TextView logView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("LogsFragment", "onCreateView");
        View rootView = inflater.inflate(R.layout.fragment_logs, container, false);
        pullrefresher = (SwipeRefreshLayout) rootView.findViewById(R.id.pullrefresher);
        pullrefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
            }
        });
        logView = (TextView) rootView.findViewById(R.id.logview);
        logView.setOnLongClickListener(new TextView.OnLongClickListener(){
            @Override
            public boolean onLongClick(final View v){
                new AlertDialog.Builder(v.getContext())
                        .setTitle("Clear Log")
                        .setMessage("Confirm clear log.")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                String path = getActivity().getSharedPreferences("Settings", MODE_PRIVATE).getString("path", "/mnt/external_sdio")+"/dashcam.log";
                                File f = new File(path);
                                if (!f.delete()) Log.d("FilesFragment", "error");
                                refresh();
                            }})
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            }
        });

        return rootView;
    }

    @Override
    public void setUserVisibleHint(boolean visible) {
        super.setUserVisibleHint(visible);
        Log.d("LogsFragment", "setUserVisibleHint: "+Boolean.toString(visible));
        if (visible) refresh();
    }

    private void refresh(){
        SharedPreferences prefs = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
        String rootpath = prefs.getString("path", "/mnt/external_sdio");

        try {
            File logfile = new File(rootpath+"/dashcam.log");
            FileInputStream fIn = new FileInputStream(logfile);
            BufferedReader myReader = new BufferedReader(new InputStreamReader(fIn));
            String aDataRow;
            String aBuffer = "";
            while ((aDataRow = myReader.readLine()) != null) {
                aBuffer += aDataRow + "\n";
            }
            logView.setText(aBuffer);
            myReader.close();
        } catch (Exception e) {
            e.printStackTrace();
            logView.setText("");
        }
        if (pullrefresher != null) pullrefresher.setRefreshing(false);
    }
}
