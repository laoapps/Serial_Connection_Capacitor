package android.serialport;

import android.util.Log;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

public class SerialPort {
    private static final String TAG = "SerialPort";
    private FileDescriptor mFd;
    private InputStream mFileInputStream;  // Changed from FileInputStream
    private OutputStream mFileOutputStream; // Changed from FileOutputStream
    private String devicePath;

    // Default values matching the original native implementation
    private static final int DEFAULT_DATA_BITS = 8;    // Matches cfmakeraw default
    private static final int DEFAULT_STOP_BITS = 1;    // Matches cfmakeraw default
    private static final String DEFAULT_PARITY = "none"; // Matches cfmakeraw default
    private static final int DEFAULT_BUFFER_SIZE = 0;  // No buffering by default (original behavior)

    // Original constructor with defaults
    public SerialPort(File device, int baudrate, int flags) throws SecurityException, IOException {
        this(device, baudrate, flags, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY, DEFAULT_BUFFER_SIZE);
    }

    public SerialPort(String devicePath, int baudrate, int flags) throws SecurityException, IOException {
        this(new File(devicePath), baudrate, flags, DEFAULT_DATA_BITS, DEFAULT_STOP_BITS, DEFAULT_PARITY, DEFAULT_BUFFER_SIZE);
    }

    public SerialPort(File device, int baudrate) throws SecurityException, IOException {
        this(device, baudrate, 0);
    }

    public SerialPort(String devicePath, int baudrate) throws SecurityException, IOException {
        this(new File(devicePath), baudrate, 0);
    }

    // New constructor with configurable parameters
    public SerialPort(File device, int baudrate, int flags, int dataBits, int stopBits, String parity, int bufferSize) throws SecurityException, IOException {
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
        mFd = open(device.getAbsolutePath(), baudrate, flags, dataBits, stopBits, parityToInt(parity));
        if (mFd == null) {
            Log.e(TAG, "Native open returns null");
            throw new IOException("Failed to open serial port");
        }
        // Apply buffering only if bufferSize > 0, otherwise use unbuffered streams (original behavior)
        if (bufferSize > 0) {
            mFileInputStream = new BufferedInputStream(new FileInputStream(mFd), bufferSize);
            mFileOutputStream = new BufferedOutputStream(new FileOutputStream(mFd), bufferSize);
        } else {
            mFileInputStream = new FileInputStream(mFd);
            mFileOutputStream = new FileOutputStream(mFd);
        }
    }

    // Convenience constructor for string path with configurable parameters
    public SerialPort(String devicePath, int baudrate, int flags, int dataBits, int stopBits, String parity, int bufferSize) throws SecurityException, IOException {
        this(new File(devicePath), baudrate, flags, dataBits, stopBits, parity, bufferSize);
    }

    public String getDevicePath() { // Fixed typo from gettDdevicePath to getDevicePath
        return devicePath;
    }

    public InputStream getInputStream() {
        return mFileInputStream;
    }

    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }

    private int parityToInt(String parity) {
        switch (parity.toLowerCase()) {
            case "none": return 0;
            case "odd": return 1;
            case "even": return 2;
            default: throw new IllegalArgumentException("Invalid parity: " + parity);
        }
    }

    private native static FileDescriptor open(String path, int baudrate, int flags, int dataBits, int stopBits, int parity);
    public native void close() throws IOException;

    static {
        System.loadLibrary("serial_port");
    }
}
