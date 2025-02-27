package android.serialport;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.File;
public class RootCheck {
    private static final String TAG = "RootUtil";

    public static boolean isRoot() {
        String binPath = "/system/bin/su";
        String xBinPath = "/system/xbin/su";
        return (new File(binPath)).exists() && isExecutable(binPath) ||
               (new File(xBinPath)).exists() && isExecutable(xBinPath);
    }

    private static boolean isExecutable(String filePath) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("ls -l " + filePath);
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String str = in.readLine();
            Log.i(TAG, str);
            if (str == null || str.length() < 4) return false;
            char flag = str.charAt(3);
            return flag == 's' || flag == 'x';
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (p != null) p.destroy();
        }
    }
}