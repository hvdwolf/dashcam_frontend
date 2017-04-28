package tk.rabidbeaver.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent){
        // Using android.intent.action.USER_PRESENT
        // Hopefully this gets triggered on the china boxes.

        Log.d("Dashcam StateReceiver","Running onReceive");
        Intent service = new Intent(context, DashCamService.class);
        service.setAction(Constants.ACTION.STARTFOREGROUND_ACTION);
        context.startService(service);

        /*Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Set<String> keys = bundle.keySet();
            Iterator<String> it = keys.iterator();
            Log.d("Dashcam StateReceiver","Dumping Intent start");
            while (it.hasNext()) {
                String key = it.next();
                Log.d("Dashcam StateReceiver","[" + key + "=" + bundle.get(key)+"]");
            }
            Log.d("Dashcam StateReceiver","Dumping Intent end");
        }*/
    }
}
