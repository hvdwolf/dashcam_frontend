package tk.rabidbeaver.dashcam;

class Constants {

    interface ACTION {
        String MAIN_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.main";
        String ACTION_RECORD = "tk.rabidbeaver.dashcam.dashcamservice.action.record";
        String ACTION_STOP = "tk.rabidbeaver.dashcam.dashcamservice.action.stop";
        String STARTFOREGROUND_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.startforeground";
        String STOPFOREGROUND_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.stopforeground";
        String ACTION_RESTART = "tk.rabidbeaver.dashcam.dashcamservice.action.restart";
    }

    interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 101;
    }

    interface VALUES {
        int DATABASE_VERSION = 1;
    }
}
