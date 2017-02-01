package tk.rabidbeaver.dashcam;

class LogRow {
    String time;
    String type;
    String value;

    LogRow(String time, String type, String value){
        this.time=time;
        this.type=type;
        this.value=value;
    }
}
