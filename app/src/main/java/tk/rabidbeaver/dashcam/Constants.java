package tk.rabidbeaver.dashcam;

class Constants {

    interface ACTION {
        String MAIN_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.main";
        String ACTION_RECORD = "tk.rabidbeaver.dashcam.dashcamservice.action.record";
        String ACTION_STOP = "tk.rabidbeaver.dashcam.dashcamservice.action.stop";
        String STARTFOREGROUND_ACTION = "tk.rabidbeaver.dashcam.dashcamservice.action.startforeground";
        String ACTION_RESTART = "tk.rabidbeaver.dashcam.dashcamservice.action.restart";
        String ACTION_CLEANDB = "tk.rabidbeaver.dashcam.dashcamservice.action.cleandb";
    }

    interface NOTIFICATION_ID {
        int FOREGROUND_SERVICE = 101;
    }

    interface VALUES {
        int DATABASE_VERSION = 1;
    }

    interface MESSAGES {
        int LOAD_DATABASE = -1;
        int DATA_LENGTH = -2;
        int CLEAR_LOG = -3;
    }
}
