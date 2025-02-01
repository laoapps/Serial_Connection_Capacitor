package com.laoapps.plugins.serialconnectioncapacitor;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class SerialConnectionCapacitor {
    private static final String TAG = "SerialConnection";
    private Context context;
    private UsbManager usbManager;
    private UsbDeviceConnection connection;
    private InputStream inputStream;
    private OutputStream outputStream;

    public SerialConnectionCapacitor(Context context) {
        this.context = context;
    }

    public boolean openConnection(String portPath, int baudRate) {
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                Log.e(TAG, "UsbManager is null");
                return false;
            }

            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getDeviceName().equals(portPath)) {
                    connection = usbManager.openDevice(device);
                    if (connection != null) {
                        UsbInterface usbInterface = device.getInterface(0);
                        UsbEndpoint endpointIn = usbInterface.getEndpoint(0);
                        UsbEndpoint endpointOut = usbInterface.getEndpoint(1);

                        connection.claimInterface(usbInterface, true);

                        inputStream = new SerialInputStream(connection, endpointIn);
                        outputStream = new SerialOutputStream(connection, endpointOut);

                        Log.d(TAG, "Connection opened on port: " + portPath);
                        return true;
                    }
                }
            }
            Log.e(TAG, "Device not found");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error opening serial port", e);
            return false;
        }
    }

    public void closeConnection() {
        if (connection != null) {
            connection.close();
            connection = null;
            inputStream = null;
            outputStream = null;
        }
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public Map<String, Integer> listPorts() {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        Map<String, Integer> ports = new HashMap<>();

        if (usbManager != null) {
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                ports.put(device.getDeviceName(), device.getDeviceId());
            }
        }

        return ports;
    }
}