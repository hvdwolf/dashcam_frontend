package tk.rabidbeaver.libffmpeg;

import android.os.Build;
import android.util.Log;

import java.util.Arrays;

class CpuArchHelper {
    
    static CpuArch getCpuArch() {
        Log.d("FFMPEG", "Build.SUPPORTED_ABIS[0] : " + Build.SUPPORTED_ABIS[0]);
        // check if device is x86 or x86_64
        if (Arrays.asList(Build.SUPPORTED_ABIS).contains("x86")) return CpuArch.x86;
        else if (Arrays.asList(Build.SUPPORTED_ABIS).contains("armeabi-v7a")) return CpuArch.ARMv7;
        return CpuArch.NONE;
    }
}
