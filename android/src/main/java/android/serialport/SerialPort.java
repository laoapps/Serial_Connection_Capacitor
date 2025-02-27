package android.serialport;

import android.util.Log;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.serialport.SerialPort;

public class SerialPort {
    private static final String TAG = "SerialPort";
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;
    private String devicePath;

    public SerialPort(File device, int baudrate, int flags) throws SecurityException, IOException {
        this.devicePath = device.getPath();
        if (!device.canRead() || !device.canWrite()) {
            try {
                String cmd = "chmod 777 " + device.getAbsolutePath() + "\nexit\n";
                CommandExecution.CommandResult result = CommandExecution.execCommand(cmd, RootCheck.isRoot());
                if (result.result != 0 || !device.canRead() || !device.canWrite()) {
                    throw new SecurityException("Root permission denied or chmod failed");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to gain permissions: " + e.getMessage());
                throw new SecurityException("Failed to gain read/write permissions: " + e.getMessage());
            }
        }
        mFd = open(device.getAbsolutePath(), baudrate, flags);
        if (mFd == null) {
            Log.e(TAG, "Native open returns null");
            throw new IOException("Failed to open serial port");
        }
        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }

    public SerialPort(String devicePath, int baudrate, int flags) throws SecurityException, IOException {
        this(new File(devicePath), baudrate, flags);
    }

    public String gettDdevicePath() {
        return devicePath;
    }

    public SerialPort(File device, int baudrate) throws SecurityException, IOException {
        this(device, baudrate, 0);
    }

    public SerialPort(String devicePath, int baudrate) throws SecurityException, IOException {
        this(new File(devicePath), baudrate, 0);
    }

    public InputStream getInputStream() {
        return mFileInputStream;
    }

    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }

    private native static FileDescriptor open(String path, int baudrate, int flags);
    public native void close() throws IOException;

    static {
        System.loadLibrary("serial_port");
    }
}