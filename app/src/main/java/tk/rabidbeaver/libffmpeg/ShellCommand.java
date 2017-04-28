package tk.rabidbeaver.libffmpeg;

import android.util.Log;

import java.io.IOException;
import java.util.Arrays;

class ShellCommand {

    Process run(String[] commandString) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(commandString);
        } catch (IOException e) {
            Log.e("FFMPEG", "Exception while trying to run: " + Arrays.toString(commandString) + e);
        }
        return process;
    }

    CommandResult runWaitFor(String[] s) {
        Process process = run(s);

        Integer exitValue = null;
        String output = null;
        try {
            if (process != null) {
                exitValue = process.waitFor();

                if (CommandResult.success(exitValue)) {
                    output = Util.convertInputStreamToString(process.getInputStream());
                } else {
                    output = Util.convertInputStreamToString(process.getErrorStream());
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            Util.destroyProcess(process);
        }

        return new CommandResult(CommandResult.success(exitValue), output);
    }

}