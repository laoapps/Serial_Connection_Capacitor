/*
 * Copyright 2009 Cedric Priscal
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */

package com.laoapps.plugins.serialconnectioncapacitor;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SerialPort {

    private static final String TAG = "SerialPort";

    private static final String DEFAULT_SU_PATH = "/system/xbin/su";

    private static String sSuPath = DEFAULT_SU_PATH;

    /**
     * Set the su binary path, the default su binary path is {@link #DEFAULT_SU_PATH}
     *
     * @param suPath su binary path
     */
    public static void setSuPath(String suPath) {
        if (suPath == null) {
            return;
        }
        sSuPath = suPath;
    }

    /*
         * Do not remove or rename the field mFd: it is used by native method close();
         */
    private FileDescriptor mFd;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;
    private String devicePath;

    public SerialPort(File device, int baudrate, int flags) throws SecurityException, IOException {
		/* Check access permission */
        this.devicePath = device.getPath();

        if (!device.canRead() || !device.canWrite()) {
            Process process = null;
            try {
                /* Missing read/write permission, trying to chmod the file */
                DataOutputStream os = null;//////////////////////////////////////////////////////////////////////////
                DataInputStream is = null;
                process = Runtime.getRuntime().exec(sSuPath);
                //String cmd = "chmod 666 " + device.getAbsolutePath() + "\n" + "exit\n";
//                String cmd = "/system/xbin/chmod -R 777 " + device.getAbsolutePath() + "\n" + " exit\n";
//                su.getOutputStream().write(cmd.getBytes());
                os = new DataOutputStream(process.getOutputStream());
                is = new DataInputStream(process.getInputStream());
                os.writeBytes("/system/bin/chmod -R 777 " + device.getAbsolutePath() + " \n");
                os.writeBytes(" exit \n");
                os.flush();
                if ((process.waitFor() != 0) || !device.canRead() || !device.canWrite()) {
                    throw new SecurityException();
                }
            } catch (Exception e) {
                Log.i("serialport","open serialport{"+devicePath+":"+baudrate+"} failed");
                throw new SecurityException();
            }finally {
                if(process != null) {
                    process.destroy();
                }
            }
        }
        mFd = open(device.getAbsolutePath(), baudrate, flags);
        if (mFd == null) {
            Log.e(TAG, "native open returns null");
            throw new IOException();
        }
        mFileInputStream = new FileInputStream(mFd);
        mFileOutputStream = new FileOutputStream(mFd);
    }


    public SerialPort(String devicePath, int baudrate, int flags) throws SecurityException, IOException {
        this(new File(devicePath), baudrate, flags);

    }

    public String gettDdevicePath(){
        return devicePath;
    }

    public SerialPort(File device, int baudrate) throws SecurityException, IOException {
        this(device, baudrate, 0);
    }

    public SerialPort(String devicePath, int baudrate) throws SecurityException, IOException {
        this(new File(devicePath), baudrate, 0);
    }

    // Getters and setters
    public InputStream getInputStream() {
        return mFileInputStream;
    }

    public OutputStream getOutputStream() {
        return mFileOutputStream;
    }

    // JNI
    private native static FileDescriptor open(String path, int baudrate, int flags);

    public native void close() throws IOException; // Add throws clause

    static {
        System.loadLibrary("serial_port");
    }
}
