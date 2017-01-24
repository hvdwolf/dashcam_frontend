package com.github.hiteshsondhi88.libffmpeg;

import android.content.Context;
import android.text.TextUtils;

import java.lang.reflect.Array;
import java.util.Map;

import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

@SuppressWarnings("unused")
public class FFmpeg implements FFmpegInterface {

    private final Context context;
    private FFmpegExecuteAsyncTask ffmpegExecuteAsyncTask;
    private FFmpegLoadLibraryAsyncTask ffmpegLoadLibraryAsyncTask;

    private static final long MINIMUM_TIMEOUT = 10 * 1000;
    private long timeout = Long.MAX_VALUE;

    private static FFmpeg instance = null;

    private FFmpeg(Context context) {
        this.context = context.getApplicationContext();
        Log.setDEBUG(Util.isDebug(this.context));
    }

    public static FFmpeg getInstance(Context context) {
        if (instance == null) {
            instance = new FFmpeg(context);
        }
        return instance;
    }

    @Override
    public void loadBinary(FFmpegLoadBinaryResponseHandler ffmpegLoadBinaryResponseHandler) throws FFmpegNotSupportedException {
        String cpuArchNameFromAssets = null;
        switch (CpuArchHelper.getCpuArch()) {
            case x86:
                Log.i("Loading FFmpeg for x86 CPU");
                cpuArchNameFromAssets = "x86";
                break;
            case ARMv7:
                Log.i("Loading FFmpeg for armv7 CPU");
                cpuArchNameFromAssets = "armeabi-v7a";
                break;
            case NONE:
                throw new FFmpegNotSupportedException("Device not supported");
        }

        if (!TextUtils.isEmpty(cpuArchNameFromAssets)) {
            ffmpegLoadLibraryAsyncTask = new FFmpegLoadLibraryAsyncTask(context, cpuArchNameFromAssets, ffmpegLoadBinaryResponseHandler);
            ffmpegLoadLibraryAsyncTask.execute();
        } else {
            throw new FFmpegNotSupportedException("Device not supported");
        }
    }

    @Override
    public void execute(Map<String, String> environvenmentVars, String[] cmd, FFmpegExecuteResponseHandler ffmpegExecuteResponseHandler) throws FFmpegCommandAlreadyRunningException {
        if (ffmpegExecuteAsyncTask != null && !ffmpegExecuteAsyncTask.isProcessCompleted()) {
            throw new FFmpegCommandAlreadyRunningException("FFmpeg command is already running, you are only allowed to run single command at a time");
        }
        if (cmd.length != 0) {
            String[] ffmpegBinary = new String[] { FileUtils.getFFmpeg(context, environvenmentVars) };
            String[] command = concatenate(ffmpegBinary, cmd);
            ffmpegExecuteAsyncTask = new FFmpegExecuteAsyncTask(command , timeout, ffmpegExecuteResponseHandler);
            ffmpegExecuteAsyncTask.execute();
        } else {
            throw new IllegalArgumentException("shell command cannot be empty");
        }
    }

    public <T> T[] concatenate (T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    @Override
    public void execute(String[] cmd, FFmpegExecuteResponseHandler ffmpegExecuteResponseHandler) throws FFmpegCommandAlreadyRunningException {
        execute(null, cmd, ffmpegExecuteResponseHandler);
    }

    @Override
    public String getDeviceFFmpegVersion() throws FFmpegCommandAlreadyRunningException {
        ShellCommand shellCommand = new ShellCommand();
        CommandResult commandResult = shellCommand.runWaitFor(new String[] { FileUtils.getFFmpeg(context), "-version" });
        if (commandResult.success) {
            return commandResult.output.split(" ")[2];
        }
        // if unable to find version then return "" to avoid NPE
        return "";
    }

    @Override
    public String[][] getCameraOutputFormats(String path) throws FFmpegCommandAlreadyRunningException{
        //ShellCommand shellCommand = new ShellCommand();
        //CommandResult commandResult = shellCommand.runWaitFor(new String[] { FileUtils.getFFmpeg(context), "-hide_banner -f v4l2 -list_formats all -i "+path});
        //if (commandResult.success){
            String[][] retar;
            String cresout = "[video4linux2,v4l2 @ 0x56101916ff40] Raw       :     yuyv422 :           YUYV 4:2:2 : 640x480 640x360 320x240 424x240 320x180\n" +
                    "[video4linux2,v4l2 @ 0x56101916ff40] Compressed:       mjpeg :          Motion-JPEG : 640x480 640x360 320x240 424x240 320x180 848x480 960x540 1280x720\n" +
                    "/dev/video0: Immediate exit requested\n";

            String[] lines = cresout.split("\n");
            retar = new String[lines.length-1][];
            int pos = 0;
            for (int i=0; i<lines.length; i++){
                if (lines[i].contains("Raw") || lines[i].contains("Compressed")){
                    String[] format = lines[i].split(": ");

                    String[] resolutions = format[3].trim().split(" ");
                    retar[pos] = new String[resolutions.length + 2];
                    retar[pos][0] = format[0].contains("Compressed")?"Compressed":"Raw";
                    retar[pos][1] = format[1].trim();

                    for (int j=0; j<resolutions.length; j++){
                        retar[pos][j+2] = resolutions[j];
                    }
                    pos++;
                }
            }
            return retar;
        //}
        //return null;
    }

    @Override
    public String getLibraryFFmpegVersion() {
        return "3.0.2-hacked";//context.getString(R.string.shipped_ffmpeg_version);
    }

    @Override
    public boolean isFFmpegCommandRunning() {
        return ffmpegExecuteAsyncTask != null && !ffmpegExecuteAsyncTask.isProcessCompleted();
    }

    @Override
    public boolean killRunningProcesses() {
        return Util.killAsync(ffmpegLoadLibraryAsyncTask) || Util.killAsync(ffmpegExecuteAsyncTask);
    }

    @Override
    public void setTimeout(long timeout) {
        if (timeout >= MINIMUM_TIMEOUT) {
            this.timeout = timeout;
        }
    }
}
