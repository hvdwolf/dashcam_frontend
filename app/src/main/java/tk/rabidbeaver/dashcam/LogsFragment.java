package tk.rabidbeaver.dashcam;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import static android.content.Context.MODE_PRIVATE;

public class LogsFragment extends Fragment {
    private View rootView;
    private SwipeRefreshLayout pullrefresher;
    private CursorRecyclerAdapter adapter;
    private Cursor cursor;
    private TextView.OnLongClickListener lcl;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d("LogsFragment", "onCreateView");
        rootView = inflater.inflate(R.layout.fragment_logs, container, false);
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        pullrefresher = (SwipeRefreshLayout) rootView.findViewById(R.id.pullrefresher);
        pullrefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refresh();
                adapter.changeCursor(cursor);
            }
        });
        RecyclerView logView = (RecyclerView) rootView.findViewById(R.id.logview);

        lcl = new TextView.OnLongClickListener(){
            @Override
            public boolean onLongClick(final View v){
                new AlertDialog.Builder(v.getContext())
                        .setTitle("Clear Log")
                        .setMessage("Confirm clear log.")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                SharedPreferences prefs = getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
                                String rootpath = prefs.getString("path", "/mnt/external_sdio");
                                boolean internalLogging = prefs.getBoolean("loginternal", false);
                                Context dbc = getContext();
                                if (!internalLogging) dbc = new DatabaseContext(dbc);
                                String dbpath = (internalLogging?"":rootpath+"/")+"dashcam.db";
                                new BetterSQLiteOpenHelper(dbc, dbpath, null, Constants.VALUES.DATABASE_VERSION).getWritableDatabase()
                                        .execSQL("DELETE FROM log");

                                refresh();
                                adapter.changeCursor(cursor);
                            }})
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                return true;
            }
        };

        logView.setHasFixedSize(false);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        logView.setLayoutManager(llm);

        refresh();

        adapter = new CursorRecyclerAdapter(cursor){
            public LogRowViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
                TextView view = new TextView(getContext());
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                view.setOnLongClickListener(lcl);
                return new LogRowViewHolder(view);
            }

            public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, Cursor c) {
                String time = c.getString(0);
                String type = c.getString(1);
                String value = c.getString(2);

                ((LogRowViewHolder)viewHolder).data.setText(time+": "+type+": "+value);
            }
        };
        logView.setAdapter(adapter);
    }

    @Override
    public void setUserVisibleHint(boolean visible) {
        super.setUserVisibleHint(visible);
        Log.d("LogsFragment", "setUserVisibleHint: "+Boolean.toString(visible));
        if (visible){
            refresh();
            adapter.changeCursor(cursor);
        }
    }

    private void refresh(){
        SharedPreferences prefs = this.getActivity().getSharedPreferences("Settings", MODE_PRIVATE);
        String rootpath = prefs.getString("path", "/mnt/external_sdio");
        boolean internalLogging = prefs.getBoolean("loginternal", false);
        Context dbc = getContext();
        if (!internalLogging) dbc = new DatabaseContext(dbc);
        String dbpath = (internalLogging?"":rootpath+"/")+"dashcam.db";
        try {
            String[] columns = {"time", "type", "value"};
            cursor = new BetterSQLiteOpenHelper(dbc, dbpath, null, Constants.VALUES.DATABASE_VERSION).getWritableDatabase()
                .query("log", columns, null, null, null, null, "time DESC");
        } catch (Exception e){
            e.printStackTrace();
            cursor = null;
        }

        if (pullrefresher != null) pullrefresher.setRefreshing(false);
    }

    final class LogRowViewHolder extends RecyclerView.ViewHolder {
        TextView data;

        LogRowViewHolder(View itemView) {
            super(itemView);
            data = (TextView) itemView;
        }
    }
}
