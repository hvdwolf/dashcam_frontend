package tk.rabidbeaver.dashcam;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;

public class FilesFragment extends Fragment {
    private View rootView;
    private LayoutInflater inf;
    private boolean created = false;
    private SwipeRefreshLayout pullrefresher;
    ViewGroup container;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("FilesFragment", "onCreateView");
        this.container=container;
        rootView = inflater.inflate(R.layout.fragment_files, container, false);
        inf=inflater;
        created = true;
        refreshView();
        pullrefresher = (SwipeRefreshLayout) rootView.findViewById(R.id.pullrefresher);
        pullrefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshView();
            }
        });

        return rootView;
    }

    @Override
    public void setUserVisibleHint(boolean visible) {
        super.setUserVisibleHint(visible);
        Log.d("FilesFragment", "setUserVisibleHint: "+Boolean.toString(visible));
        if (created && visible) refreshView();
    }

    private void refreshView(){
        // if not exists, create directory to store protected videos;
        SharedPreferences prefs = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
        String rootpath = prefs.getString("path", "/mnt/external_sdio");
        Log.d("FilesFragment", rootpath);
        File ptect = new File(rootpath+"/protected");
        if (!ptect.exists()){
            if (!ptect.mkdir()) Log.d("FilesFragment", "error");
        } else if (!ptect.isDirectory()){
            if (!ptect.delete()) Log.d("FilesFragment", "error");
            if (!ptect.mkdir()) Log.d("FilesFragment", "error");
        }

        LinearLayout fileslayout = (LinearLayout) rootView.findViewById(R.id.fileslayout);
        fileslayout.removeAllViews();

        try {
            File path = new File(rootpath);
            if (!path.exists()) return;
            File ppath = new File(rootpath+"/protected");

            // Read the directory into an ArrayList
            List<File> files = new ArrayList<>(Arrays.asList(path.listFiles(new FilenameFilter(){
                public boolean accept(File path, String name){
                    return name.endsWith(".mkv");
                }
            })));
            List<File> pfiles = new ArrayList<>(Arrays.asList(ppath.listFiles(new FilenameFilter(){
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
            Collections.sort(pfiles, new Comparator<File>(){
                public int compare(File f1, File f2){
                    if (f1.lastModified() < f2.lastModified()) return -1;
                    else if (f1.lastModified() > f2.lastModified()) return 1;
                    return 0;
                }
            });

            // Create view for each file and add to layout
            for (int i=pfiles.size()-1; i>=0; i--){
                CardView video = (CardView) inf.inflate(R.layout.file, container, false);
                TextView fileLabel = (TextView) video.findViewById(R.id.filelab);
                CheckBox protbox = (CheckBox) video.findViewById(R.id.protbox);
                protbox.setChecked(true);
                protbox.setContentDescription(pfiles.get(i).getName());
                fileLabel.setText(pfiles.get(i).getName());
                video.setOnClickListener(new CardView.OnClickListener(){
                    @Override
                    public void onClick(View v){
                        String uri = "file://"+getActivity().getSharedPreferences("Settings", MODE_PRIVATE).getString("path", "/mnt/external_sdio")+"/protected/"+((TextView)v.findViewById(R.id.filelab)).getText().toString();
                        Log.d("FilesFragment", "Clicked file with URI: "+uri);
                        Intent videoPlay = new Intent(Intent.ACTION_VIEW);
                        videoPlay.setDataAndType(Uri.parse(uri), "video/*");
                        startActivity(videoPlay);
                    }
                });
                video.setOnLongClickListener(new CardView.OnLongClickListener(){
                    @Override
                    public boolean onLongClick(final View v){
                        new AlertDialog.Builder(v.getContext())
                                .setTitle("Confirm Delete")
                                .setMessage("Confirm delete file.")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        String path = getActivity().getSharedPreferences("Settings", MODE_PRIVATE).getString("path", "/mnt/external_sdio")+"/protected/"+((TextView)v.findViewById(R.id.filelab)).getText().toString();
                                        File f = new File(path);
                                        if (!f.delete()) Log.d("FilesFragment", "error");
                                        refreshView();
                                    }})
                                .setNegativeButton(android.R.string.no, null)
                                .show();
                        return true;
                    }
                });
                protbox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener(){
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean b){
                        CheckBox box = (CheckBox)button;
                        moveFile(box.getContentDescription().toString(), b);
                    }
                });
                fileslayout.addView(video);
            }
            for (int i=files.size()-1; i>=0; i--){
                CardView video = (CardView) inf.inflate(R.layout.file, container, false);
                TextView fileLabel = (TextView) video.findViewById(R.id.filelab);
                CheckBox protbox = (CheckBox) video.findViewById(R.id.protbox);
                protbox.setChecked(false);
                protbox.setContentDescription(files.get(i).getName());
                fileLabel.setText(files.get(i).getName());
                video.setOnClickListener(new CardView.OnClickListener(){
                    @Override
                    public void onClick(View v){
                        String uri = "file://"+getActivity().getSharedPreferences("Settings", MODE_PRIVATE).getString("path", "/mnt/external_sdio")+"/"+((TextView)v.findViewById(R.id.filelab)).getText().toString();
                        Log.d("FilesFragment", "Clicked file with URI: "+uri);
                        Intent videoPlay = new Intent(Intent.ACTION_VIEW);
                        videoPlay.setDataAndType(Uri.parse(uri), "video/*");
                        startActivity(videoPlay);
                    }
                });
                video.setOnLongClickListener(new CardView.OnLongClickListener(){
                    @Override
                    public boolean onLongClick(final View v){
                        new AlertDialog.Builder(v.getContext())
                                .setTitle("Confirm Delete")
                                .setMessage("Confirm delete file.")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        String path = getActivity().getSharedPreferences("Settings", MODE_PRIVATE).getString("path", "/mnt/external_sdio")+"/"+((TextView)v.findViewById(R.id.filelab)).getText().toString();
                                        File f = new File(path);
                                        if (!f.delete()) Log.d("FilesFragment", "error");
                                        refreshView();
                                    }})
                                .setNegativeButton(android.R.string.no, null)
                                .show();
                        return true;
                    }
                });
                protbox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener(){
                    @Override
                    public void onCheckedChanged(CompoundButton button, boolean b){
                        CheckBox box = (CheckBox)button;
                        moveFile(box.getContentDescription().toString(), b);
                    }
                });
                fileslayout.addView(video);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (pullrefresher != null) pullrefresher.setRefreshing(false);
    }

    private void moveFile(String p, Boolean prot){
        String rootpath = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE).getString("path", "/mnt/external_sdio")+"/";
        String startpath;
        String destpath;
        Log.d("FilesFragment", "moveFile: "+Boolean.toString(prot));
        if (prot){
            startpath=rootpath+p;
            destpath=rootpath+"protected/"+p;
        } else {
            startpath=rootpath+"protected/"+p;
            destpath=rootpath+p;
        }
        Log.d("FilesFragment", "Startpath="+startpath+", Destpath="+destpath);
        File f = new File(startpath);
        if (!f.renameTo(new File(destpath))) Log.d("FilesFragment", "error");
        refreshView();
    }
}
