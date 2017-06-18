package tk.rabidbeaver.dashcam;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
    private boolean created = false;
    private SwipeRefreshLayout pullrefresher;
    private RecyclerViewAdapter adapter;
    private List<File> data;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("FilesFragment", "onCreateView");
        rootView = inflater.inflate(R.layout.fragment_files, container, false);
        created = true;
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        pullrefresher = (SwipeRefreshLayout) rootView.findViewById(R.id.pullrefresher);
        pullrefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                reloadData();
            }
        });
        data = new ArrayList<>();

        RecyclerView recycler = (RecyclerView) rootView.findViewById(R.id.fileslayout);

        recycler.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        recycler.setLayoutManager(llm);

        adapter = new RecyclerViewAdapter();
        recycler.setAdapter(adapter);

        reloadData();
    }

    @Override
    public void setUserVisibleHint(boolean visible) {
        super.setUserVisibleHint(visible);
        Log.d("FilesFragment", "setUserVisibleHint: " + Boolean.toString(visible));
        if (created && visible) reloadData();
    }

    private void reloadData() {
        // if not exists, create directory to store protected videos;
        SharedPreferences prefs = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
        String rootpath = prefs.getString("path", "/mnt/external_sdio");
        Log.d("FilesFragment", rootpath);
        File ptect = new File(rootpath + "/protected");
        if (!ptect.exists()) {
            if (!ptect.mkdir()) Log.d("FilesFragment", "error");
        } else if (!ptect.isDirectory()) {
            if (!ptect.delete()) Log.d("FilesFragment", "error");
            if (!ptect.mkdir()) Log.d("FilesFragment", "error");
        }

        try {
            File path = new File(rootpath);
            if (!path.exists()) return;
            File ppath = new File(rootpath + "/protected");

            // Read the directory into an ArrayList
            List<File> files = new ArrayList<>(Arrays.asList(path.listFiles(new FilenameFilter() {
                public boolean accept(File path, String name) {
                    return name.endsWith(".mkv");
                }
            })));
            List<File> pfiles = new ArrayList<>(Arrays.asList(ppath.listFiles(new FilenameFilter() {
                public boolean accept(File path, String name) {
                    return name.endsWith(".mkv");
                }
            })));

            // Sort the ArrayList according to lastModified time
            Collections.sort(files, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    if (f1.lastModified() > f2.lastModified()) return -1;
                    else if (f1.lastModified() < f2.lastModified()) return 1;
                    return 0;
                }
            });
            Collections.sort(pfiles, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    if (f1.lastModified() > f2.lastModified()) return -1;
                    else if (f1.lastModified() < f2.lastModified()) return 1;
                    return 0;
                }
            });

            data.clear();
            data.addAll(pfiles);
            data.addAll(files);

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (pullrefresher != null) pullrefresher.setRefreshing(false);

        adapter.notifyDataSetChanged();
    }

    private void moveFile(String p, Boolean prot) {
        SharedPreferences sp = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
        String rootpath = sp.getString("path", "/mnt/external_sdio") + "/";
        boolean autosave = sp.getBoolean("autosave", false);
        String startpath;
        String destpath;
        Log.d("FilesFragment", "moveFile: " + Boolean.toString(prot));
        if (prot) {
            startpath = rootpath + p;
            destpath = rootpath + "protected/" + p;
        } else {
            startpath = rootpath + "protected/" + p;
            destpath = rootpath + p;
        }
        Log.d("FilesFragment", "Startpath=" + startpath + ", Destpath=" + destpath);
        File f = new File(startpath);
        if (!f.renameTo(new File(destpath))) Log.d("FilesFragment", "error");
        if (autosave && prot) {
            Intent intent = new Intent(android.content.Intent.ACTION_SEND);
            intent.setType("video/mkv");
            intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + destpath));

            SharedPreferences sd = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
            String send = sd.getString("sendto","");

            if (send.length() == 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1){
                Intent rIntent = new Intent(this.getContext(), SendSelectionReceiver.class);
                PendingIntent pIntent = PendingIntent.getBroadcast(this.getContext(), 0, rIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                startActivity(Intent.createChooser(intent, "Send to...", pIntent.getIntentSender()));
            } else {
                if (send.length() > 0){
                    String[] cmp = send.split("/");
                    intent.setComponent(new ComponentName(cmp[0], cmp[1]));
                }
                startActivity(intent);
            }
        }
        reloadData();
    }

    class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ListItemViewHolder> {
        RecyclerViewAdapter() {}

        @Override
        public ListItemViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            CardView cardView = (CardView)LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file, viewGroup, false);
            setupCard(cardView);

            return new ListItemViewHolder(cardView);
        }

        private void setupCard(CardView cardView){
            CheckBox protbox = (CheckBox) cardView.findViewById(R.id.protbox);
            cardView.setOnClickListener(new CardView.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TextView filename = (TextView)v.findViewById(R.id.filelab);
                    ListItemViewHolder vh = (ListItemViewHolder)filename.getTag();
                    int position = vh.getAdapterPosition();
                    File f = data.get(position);

                    String uri = "file://"+f.getAbsolutePath();

                    Log.d("FilesFragment", "Clicked file with URI: " + uri);
                    Intent videoPlay = new Intent(Intent.ACTION_VIEW);
                    videoPlay.setDataAndType(Uri.parse(uri), "video/*");
                    startActivity(videoPlay);
                }
            });
            cardView.setOnLongClickListener(new CardView.OnLongClickListener() {
                @Override
                public boolean onLongClick(final View v) {
                    new AlertDialog.Builder(v.getContext())
                            .setTitle("Confirm Delete")
                            .setMessage("Confirm delete file.")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    TextView filename = (TextView)v.findViewById(R.id.filelab);
                                    ListItemViewHolder vh = (ListItemViewHolder)filename.getTag();
                                    int position = vh.getAdapterPosition();
                                    File f = data.get(position);

                                    if (!f.delete()) Log.d("FilesFragment", "error");
                                    reloadData();
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    return true;
                }
            });
            protbox.setOnCheckedChangeListener(new CheckBox.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton button, boolean b) {
                    ListItemViewHolder vh = (ListItemViewHolder)button.getTag();
                    if (!vh.protbox_disabled){
                        int position = vh.getAdapterPosition();
                        File f = data.get(position);
                        moveFile(f.getName(), b);
                    }
                    vh.protbox_disabled = false;
                }
            });
        }

        @Override
        public void onBindViewHolder(ListItemViewHolder viewHolder, int position) {
            File f = data.get(position);
            viewHolder.filename.setText(String.valueOf(f.getName()));
            boolean shouldBeChecked = f.getAbsolutePath().contains("/protected/");
            boolean isChecked = viewHolder.protbox.isChecked();
            if (shouldBeChecked != isChecked) {
                viewHolder.protbox_disabled = true;
                viewHolder.protbox.setChecked(shouldBeChecked);
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        final class ListItemViewHolder extends RecyclerView.ViewHolder {
            TextView filename;
            CheckBox protbox;
            boolean protbox_disabled;

            ListItemViewHolder(View itemView) {
                super(itemView);
                filename = (TextView) itemView.findViewById(R.id.filelab);
                filename.setTag(this);
                protbox = (CheckBox) itemView.findViewById(R.id.protbox);
                protbox.setTag(this);
            }
        }
    }
}
