package tk.rabidbeaver.dashcam;

import android.support.v7.widget.RecyclerView;

abstract class MessengerRecyclerAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private boolean mDataValid;
    private MessengerInterface mInterface = null;

    MessengerRecyclerAdapter(MessengerInterface mInterface) {
        this.mInterface = mInterface;
        mDataValid = mInterface != null;
    }

    @Override
    public final void onBindViewHolder (VH holder, int position) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }

        onBindViewHolder(holder, mInterface, position);
    }

    public abstract void onBindViewHolder(VH holder, MessengerInterface intf, int position);

    @Override
    public int getItemCount () {
        if (mDataValid && mInterface != null) return mInterface.getSize();
        else return 0;
    }

    void refreshSource(){
        mInterface.loadData();
        notifyDataSetChanged();
    }

    void clearLog(){
        mInterface.clearLog();
    }
}
