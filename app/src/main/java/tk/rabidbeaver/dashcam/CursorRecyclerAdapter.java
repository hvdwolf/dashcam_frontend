package tk.rabidbeaver.dashcam;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;

abstract class CursorRecyclerAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    private boolean mDataValid;
    private Cursor mCursor;

    CursorRecyclerAdapter(Cursor c) {
        init(c);
    }

    private void init(Cursor c) {
        boolean cursorPresent = c != null;
        mCursor = c;
        mDataValid = cursorPresent;
    }

    @Override
    public final void onBindViewHolder (VH holder, int position) {
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }

        onBindViewHolder(holder, mCursor);
    }

    public abstract void onBindViewHolder(VH holder, Cursor cursor);

    @Override
    public int getItemCount () {
        if (mDataValid && mCursor != null) return mCursor.getCount();
        else return 0;
    }

    void changeCursor(Cursor cursor) {
        Cursor old = swapCursor(cursor);
        if (old != null) old.close();
    }

    private Cursor swapCursor(Cursor newCursor) {
        if (newCursor == mCursor) return null;
        Cursor oldCursor = mCursor;
        mCursor = newCursor;
        if (newCursor != null) {
            mDataValid = true;
            notifyDataSetChanged();
        } else {
            mDataValid = false;
            notifyItemRangeRemoved(0, getItemCount());
        }
        return oldCursor;
    }
}
