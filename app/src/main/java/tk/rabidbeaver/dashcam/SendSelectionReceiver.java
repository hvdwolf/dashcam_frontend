package tk.rabidbeaver.dashcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import static android.content.Context.MODE_PRIVATE;

public class SendSelectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= 22) {
            Object extra = intent.getExtras().get(Intent.EXTRA_CHOSEN_COMPONENT);
            String sendto;
            if (extra != null) sendto = extra.toString();
            else return;
            sendto = sendto.replace("ComponentInfo{", "");
            sendto = sendto.replace("}", "");
            Log.d("INFORMATION", "Received intent after selection: " + sendto);
            SharedPreferences prefs = context.getSharedPreferences("Settings", MODE_PRIVATE);
            SharedPreferences.Editor prefedit = prefs.edit();
            prefedit.putString("sendto", sendto);
            prefedit.apply();
        }
    }
}
