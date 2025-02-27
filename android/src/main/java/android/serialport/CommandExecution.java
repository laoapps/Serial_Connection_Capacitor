package android.serialport;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
public class CommandExecution {
    public static class CommandResult {
        public int result;
        public String successMsg;
        public String errorMsg;

        public CommandResult(int result) {
            this.result = result;
        }

        public CommandResult(int result, String successMsg, String errorMsg) {
            this.result = result;
            this.successMsg = successMsg;
            this.errorMsg = errorMsg;
        }
    }

    private static final String TAG = "CommandExecution";

    public static CommandResult execCommand(String command, boolean isRoot) {
        Process process = null;
        DataOutputStream os = null;
        BufferedReader successReader = null;
        BufferedReader errorReader = null;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder errorMsg = new StringBuilder();

        try {
            process = Runtime.getRuntime().exec(isRoot ? "su" : "sh");
            os = new DataOutputStream(process.getOutputStream());
            os.write(command.getBytes());
            os.writeBytes("\n");
            os.flush();
            os.writeBytes("exit\n");
            os.flush();

            successReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = successReader.readLine()) != null) successMsg.append(line).append("\n");
            while ((line = errorReader.readLine()) != null) errorMsg.append(line).append("\n");

            int result = process.waitFor();
            return new CommandResult(result, successMsg.toString(), errorMsg.toString());
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed: " + e.getMessage());
            return new CommandResult(-1, null, e.getMessage());
        } finally {
            try {
                if (os != null) os.close();
                if (successReader != null) successReader.close();
                if (errorReader != null) errorReader.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close streams: " + e.getMessage());
            }
            if (process != null) process.destroy();
        }
    }
}