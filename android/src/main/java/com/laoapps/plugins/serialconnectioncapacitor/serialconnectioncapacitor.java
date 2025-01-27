package com.laoapps.plugins.serialconnectioncapacitor;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SerialConnectionCapacitor {

    private UsbDeviceConnection connection;
    private InputStream inputStream;
    private OutputStream outputStream;

    public boolean openConnection(String portPath, int baudRate) {
        try {
            UsbManager usbManager = (UsbManager) MyApp.getContext().getSystemService(UsbManager.class);
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getDeviceName().equals(portPath)) {
                    connection = usbManager.openDevice(device);
                    if (connection != null) {
                        // Initialize input and output streams
                        inputStream = connection.getFileDescriptor() != -1 ? connection.getInputStream() : null;
                        outputStream = connection.getFileDescriptor() != -1 ? connection.getOutputStream() : null;
                        Log.d("SerialPlugin", "Connection opened on: " + portPath);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e("SerialPlugin", "Error opening connection: " + e.getMessage());
        }
        return false;
    }

    public boolean writeToPort(String data) {
        try {
            if (outputStream != null) {
                outputStream.write(data.getBytes());
                outputStream.flush();
                return true;
            }
        } catch (IOException e) {
            Log.e("SerialPlugin", "Error writing to port: " + e.getMessage());
        }
        return false;
    }

    public String readFromPort() {
        try {
            if (inputStream != null) {
                byte[] buffer = new byte[1024];
                int bytesRead = inputStream.read(buffer);
                if (bytesRead > 0) {
                    return new String(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            Log.e("SerialPlugin", "Error reading from port: " + e.getMessage());
        }
        return null;
    }

    public boolean closeConnection() {
        try {
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            if (connection != null) {
                connection.close();
                connection = null;
            }
            Log.d("SerialPlugin", "Connection closed");
            return true;
        } catch (IOException e) {
            Log.e("SerialPlugin", "Error closing connection: " + e.getMessage());
        }
        return false;
    }
}
