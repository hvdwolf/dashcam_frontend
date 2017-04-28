package tk.rabidbeaver.dashcam;

import android.widget.TextView;

interface MessengerInterface {
    void loadData();
    int getSize();
    void getItem(int position, TextView dest);
    void clearLog();
}