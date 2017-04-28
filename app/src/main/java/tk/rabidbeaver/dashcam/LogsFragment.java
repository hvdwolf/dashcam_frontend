package tk.rabidbeaver.dashcam;

import android.content.DialogInterface;
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

public class LogsFragment extends Fragment {
    private View rootView;
    private SwipeRefreshLayout pullrefresher;
    private MessengerRecyclerAdapter adapter;
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
                adapter.refreshSource();
                if (pullrefresher != null) pullrefresher.setRefreshing(false);
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
                                adapter.clearLog();
                                adapter.refreshSource();
                                if (pullrefresher != null) pullrefresher.setRefreshing(false);
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

        if (pullrefresher != null) pullrefresher.setRefreshing(false);

        adapter = new MessengerRecyclerAdapter((MessengerInterface)getActivity()){

            public LogRowViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
                TextView view = new TextView(getContext());
                view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                view.setOnLongClickListener(lcl);
                return new LogRowViewHolder(view);
            }

            public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, MessengerInterface m, int position){
                //TODO: I believe that the loading delay is due to m.getItem() being synchronized.
                //  The problem is a compound problem; 1) The function itself has a synchronized block,
                //  (2) the IPC adds another delay. Rather than setting all the data here, we should send
                //  a reference to the viewholder over to getItem, have getItem return immediately and fill
                //  in the viewholder itself when the data becomes available.

                m.getItem(position, ((LogRowViewHolder)viewHolder).data);
                /*LogRow row = m.getItem(position);
                String time = row.time;
                String type = row.type;
                String value = row.value;

                ((LogRowViewHolder)viewHolder).data.setText(time+": "+type+": "+value);*/
            }
        };
        logView.setAdapter(adapter);
    }

    @Override
    public void setUserVisibleHint(boolean visible) {
        super.setUserVisibleHint(visible);
        Log.d("LogsFragment", "setUserVisibleHint: "+Boolean.toString(visible));
        if (visible){
            adapter.refreshSource();
            if (pullrefresher != null) pullrefresher.setRefreshing(false);
        }
    }

    private final class LogRowViewHolder extends RecyclerView.ViewHolder {
        TextView data;

        LogRowViewHolder(View itemView) {
            super(itemView);
            data = (TextView) itemView;
        }
    }
}
