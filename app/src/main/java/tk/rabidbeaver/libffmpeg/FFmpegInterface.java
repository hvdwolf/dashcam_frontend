package tk.rabidbeaver.libffmpeg;

import java.util.Map;

import tk.rabidbeaver.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import tk.rabidbeaver.libffmpeg.exceptions.FFmpegNotSupportedException;

@SuppressWarnings("unused")
interface FFmpegInterface {

    /**
     * Load binary to the device according to archituecture. This also updates FFmpeg binary if the binary on device have old version.
     * @param ffmpegLoadBinaryResponseHandler {@link FFmpegLoadBinaryResponseHandler}
     * @throws FFmpegNotSupportedException architecture not supported
     */
    void loadBinary(FFmpegLoadBinaryResponseHandler ffmpegLoadBinaryResponseHandler) throws FFmpegNotSupportedException;

    /**
     * Executes a command
     * @param environvenmentVars Environment variables
     * @param cmd command to execute
     * @param ffmpegExecuteResponseHandler {@link FFmpegExecuteResponseHandler}
     * @throws FFmpegCommandAlreadyRunningException ffmpeg process is running
     */
    void execute(Map<String, String> environvenmentVars, String[] cmd, FFmpegExecuteResponseHandler ffmpegExecuteResponseHandler) throws FFmpegCommandAlreadyRunningException;

    /**
     * Executes a command
     * @param cmd command to execute
     * @param ffmpegExecuteResponseHandler {@link FFmpegExecuteResponseHandler}
     * @throws FFmpegCommandAlreadyRunningException ffmpeg process is running
     */
    void execute(String[] cmd, FFmpegExecuteResponseHandler ffmpegExecuteResponseHandler) throws FFmpegCommandAlreadyRunningException;

    /**
     * Tells FFmpeg version currently on device
     * @return FFmpeg version currently on device
     * @throws FFmpegCommandAlreadyRunningException ffmpeg process is running
     */
    String getDeviceFFmpegVersion() throws FFmpegCommandAlreadyRunningException;

    /**
     * Obtain camera specifications via FFmpeg
     * @return 2-d array of strings for this camera, one 1-d array for each output format with first element being format name
     * @throws FFmpegCommandAlreadyRunningException ffmpeg process is running
     */
    String[][] getCameraOutputFormats(String path) throws FFmpegCommandAlreadyRunningException;

    /**
     * Tells FFmpeg version shipped with current library
     * @return FFmpeg version shipped with Library
     */
    String getLibraryFFmpegVersion();

    /**
     * Checks if FFmpeg command is Currently running
     * @return true if FFmpeg command is running
     */
    boolean isFFmpegCommandRunning();

    /**
     * Kill Running FFmpeg process
     * @return true if process is killed successfully
     */
    boolean killRunningProcesses();

    /**
     * Timeout for FFmpeg process, should be minimum of 10 seconds
     * @param timeout in milliseconds
     */
    void setTimeout(long timeout);

}
