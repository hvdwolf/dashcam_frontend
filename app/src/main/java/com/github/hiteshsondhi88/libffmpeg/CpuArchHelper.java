package com.github.hiteshsondhi88.libffmpeg;

import android.os.Build;

class CpuArchHelper {
    
    static CpuArch getCpuArch() {
        Log.d("Build.CPU_ABI : " + Build.CPU_ABI);
        // check if device is x86 or x86_64
        if (Build.CPU_ABI.equals("x86") || Build.CPU_ABI.equals("x86_64")) {
            return CpuArch.x86;
        } else  if (Build.CPU_ABI.equals("armeabi-v7a") || Build.CPU_ABI.equals("arm64-v8a")) {
            return CpuArch.ARMv7;
        }
        return CpuArch.NONE;
    }
}
