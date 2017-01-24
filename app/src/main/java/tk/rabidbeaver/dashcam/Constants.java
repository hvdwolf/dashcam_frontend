package tk.rabidbeaver.dashcam;

public class Constants {

    public interface ACTION {
        public static String MAIN_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.main";
        public static String ACTION_RECORD = "tk.rabidbeaver.dashcam.dashcamservice.action.record";
        public static String ACTION_STOP = "tk.rabidbeaver.dashcam.dashcamservice.action.stop";
        public static String STARTFOREGROUND_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.startforeground";
        public static String STOPFOREGROUND_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.stopforeground";
        public static String ACTION_RESTART = "tk.rabidbeaver.dashcam.dashcamservice.action.restart";
    }

    public interface NOTIFICATION_ID {
        public static int FOREGROUND_SERVICE = 101;
    }
}
