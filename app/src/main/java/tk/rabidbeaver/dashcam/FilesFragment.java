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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.MODE_PRIVATE;
import static java.lang.Thread.sleep;

public class FilesFragment extends Fragment {
    private View rootView;
    private boolean created = false;
    private SwipeRefreshLayout pullrefresher;
    private RecyclerViewAdapter adapter;
    private List<ProtectString> data;

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
        new Thread(new Runnable() {
            public void run() {
                Log.d("LIST", "mRPiAddress: " + DashCamService.mRPiAddress);
                List<ProtectString> files = new ArrayList<>();
                if (DashCamService.mRPiAddress.length() > 0) {

                    String response = "";
                    HttpURLConnection urlConnection = null;
                    try {
                        URL url = new URL("http://" + DashCamService.mRPiAddress + ":8888/list");
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
                    Log.d("LIST", response);
                    if (urlConnection != null) urlConnection.disconnect();

                    try {
                        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                        factory.setNamespaceAware(true);
                        XmlPullParser xpp = factory.newPullParser();
                        xpp.setInput(new StringReader(response));

                        int eventType = xpp.getEventType();
                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            if (eventType == XmlPullParser.START_TAG) {
                                if (xpp.getName().equals("file")) {
                                    String filename = xpp.getAttributeValue(null, "name");
                                    boolean prot = xpp.getAttributeValue(null, "protected").contentEquals("1");
                                    files.add(new ProtectString(filename,prot));
                                    Log.d("LIST","Added: "+filename+", "+Boolean.toString(prot));
                                }
                            }
                            eventType = xpp.next();
                        }
                    } catch (Exception e){e.printStackTrace();}

                } else {
                    new Thread(new Runnable() {
                        public void run() {
                            while (DashCamService.mRPiAddress.length() == 0){
                                try {
                                    sleep(500);
                                } catch (Exception e){e.printStackTrace();}
                            }
                            reloadData();
                        }
                    }).run();
                }

                data.clear();
                data.addAll(files);

                if (pullrefresher != null) {
                    pullrefresher.post(new Runnable() {
                        @Override
                        public void run() {
                            pullrefresher.setRefreshing(false);
                            adapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }).start();
    }

    private void moveFile(final String p, final Boolean prot) {

        new Thread(new Runnable() {
            public void run() {
                String action = "protect";
                if (!prot) action = "unprotect";
                Log.d("MOVE","moving file");
                String response = "";
                HttpURLConnection urlConnection = null;
                String POSTDATA = action+"="+p;
                try {
                    URL url = new URL("http://"+DashCamService.mRPiAddress+":8888");
                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setDoOutput(true);
                    OutputStream os = urlConnection.getOutputStream();
                    os.write(POSTDATA.getBytes());
                    os.flush();
                    os.close();
                    // For POST only - END

                    int responseCode = urlConnection.getResponseCode();
                    Log.d("MOVE","code: " + responseCode);

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
                Log.d("MOVE",response);
                if (urlConnection != null) urlConnection.disconnect();

                if (response.contains("<fileop status=\"success\" />")){
                    reloadData();
                    SharedPreferences sp = FilesFragment.this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
                    if (prot && sp.getBoolean("autosave", false)){
                        Intent intent = new Intent(android.content.Intent.ACTION_SEND);
                        intent.setType("video/mkv");
                        //intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("http://"+DashCamService.mRPiAddress+":8888/protected/"+p));
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("content://tk.rabidbeaver.dashcam.DataProvider/protected/"+p));

                        SharedPreferences sd = FilesFragment.this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
                        String send = sd.getString("sendto","");

                        if (send.length() == 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1){
                            Intent rIntent = new Intent(FilesFragment.this.getContext(), SendSelectionReceiver.class);
                            PendingIntent pIntent = PendingIntent.getBroadcast(FilesFragment.this.getContext(), 0, rIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                            startActivity(Intent.createChooser(intent, "Send to...", pIntent.getIntentSender()));
                        } else {
                            if (send.length() > 0){
                                String[] cmp = send.split("/");
                                intent.setComponent(new ComponentName(cmp[0], cmp[1]));
                            }
                            startActivity(intent);
                        }
                    }
                }

            }
        }).start();

        reloadData();
    }

    private class ProtectString {
        String filename;
        boolean isProtected;
        ProtectString(String filename, boolean isProtected){
            this.filename=filename;
            this.isProtected=isProtected;
        }
    }

    class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ListItemViewHolder> {
        RecyclerViewAdapter() {
        }

        @Override
        public ListItemViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            CardView cardView = (CardView) LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.file, viewGroup, false);
            setupCard(cardView);

            return new ListItemViewHolder(cardView);
        }

        private void setupCard(CardView cardView) {
            CheckBox protbox = (CheckBox) cardView.findViewById(R.id.protbox);
            cardView.setOnClickListener(new CardView.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TextView filename = (TextView) v.findViewById(R.id.filelab);
                    ListItemViewHolder vh = (ListItemViewHolder) filename.getTag();
                    int position = vh.getAdapterPosition();
                    ProtectString f = data.get(position);

                    String uri = "http://"+DashCamService.mRPiAddress+":8888/"+(f.isProtected?"protected/":"")+f.filename;

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
                                    TextView filename = (TextView) v.findViewById(R.id.filelab);
                                    ListItemViewHolder vh = (ListItemViewHolder) filename.getTag();
                                    int position = vh.getAdapterPosition();
                                    final ProtectString f = data.get(position);

                                    new Thread(new Runnable() {
                                        public void run() {

                                            Log.d("DELETE","deleting file");
                                            String response = "";
                                            HttpURLConnection urlConnection = null;
                                            String POSTDATA = "delete="+f.filename;
                                            try {
                                                URL url = new URL("http://"+DashCamService.mRPiAddress+":8888");
                                                urlConnection = (HttpURLConnection) url.openConnection();
                                                urlConnection.setRequestMethod("POST");
                                                urlConnection.setDoOutput(true);
                                                OutputStream os = urlConnection.getOutputStream();
                                                os.write(POSTDATA.getBytes());
                                                os.flush();
                                                os.close();
                                                // For POST only - END

                                                int responseCode = urlConnection.getResponseCode();
                                                Log.d("DELETE","code: " + responseCode);

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
                                            Log.d("DELETE",response);
                                            if (urlConnection != null) urlConnection.disconnect();

                                            if (response.contains("<fileop status=\"success\" />")) reloadData();

                                        }
                                    }).start();

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
                    ListItemViewHolder vh = (ListItemViewHolder) button.getTag();
                    if (!vh.protbox_disabled) {
                        int position = vh.getAdapterPosition();
                        ProtectString f = data.get(position);
                        moveFile(f.filename, b);
                    }
                    vh.protbox_disabled = false;
                }
            });
        }

        @Override
        public void onBindViewHolder(ListItemViewHolder viewHolder, int position) {
            ProtectString f = data.get(position);
            viewHolder.filename.setText(String.valueOf(f.filename));
            boolean shouldBeChecked = f.isProtected;
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
