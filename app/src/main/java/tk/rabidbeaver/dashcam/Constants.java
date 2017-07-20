package tk.rabidbeaver.dashcam;

class Constants {

    interface ACTION {
        String MAIN_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.main";
        String ACTION_RECORD = "tk.rabidbeaver.dashcam.dashcamservice.action.record";
        String ACTION_STOP = "tk.rabidbeaver.dashcam.dashcamservice.action.stop";
        String STARTFOREGROUND_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.startforeground";
        String RELOADGPS = "tk.rabidbeaver.dashcam.dashcamservice.action.reloadgps";
        String STOPSERVICE = "tk.rabidbeaver.dashcam.dascamservice.action.stopservice";
    }

    interface LOG_ID {
        int GPS_LOG = 0;
        int CRASH_LOG = 1;
    }

    interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 101;
    }
}
