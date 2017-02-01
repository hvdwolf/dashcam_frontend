package tk.rabidbeaver.dashcam;

interface MessengerInterface {
    void loadData();
    int getSize();
    LogRow getItem(int position);
    void clearLog();
}