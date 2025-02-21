package com.laoapps.plugins.serialconnectioncapacitor;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@CapacitorPlugin(name = "SerialConnectionCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private UsbManager usbManager;
    private SerialConnectionCapacitor serialConnection;
    private boolean isReading = false; // ✅ FIX: Declare class-level variable

    @Override
    public void load() {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE); // ✅ FIX: Initialize usbManager
        serialConnection = new SerialConnectionCapacitor(getContext());
    }

    @PluginMethod
    public void listPorts(PluginCall call) {
        JSObject ret = new JSObject();
        JSObject ports = new JSObject();

        if (usbManager == null) { // ✅ FIX: Handle uninitialized usbManager
            call.reject("USB Manager not initialized");
            return;
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
            UsbDevice device = entry.getValue();
            ports.put(device.getDeviceName(), device.getDeviceId());
        }

        ret.put("ports", ports);
        call.resolve(ret);
    }

    @PluginMethod
    public void open(PluginCall call) {
        if (serialConnection == null) {
            call.reject("SerialConnection not initialized");
            return;
        }

        String portPath = call.getString("portPath");
        int baudRate = call.getInt("baudRate", 9600);

        if (portPath == null || portPath.isEmpty()) {
            call.reject("Port path is required");
            return;
        }

        try {
            boolean success = serialConnection.openConnection(portPath, baudRate);
            if (success) {
                JSObject ret = new JSObject();
                ret.put("message", "Connection opened successfully");
                call.resolve(ret);
                notifyListeners("connectionOpened", ret);
            } else {
                throw new Exception("Failed to open connection");
            }
        } catch (Exception e) {
            JSObject error = new JSObject();
            error.put("error", e.getMessage());
            call.reject("Connection error: " + e.getMessage());
            notifyListeners("connectionError", error);
        }
    }
    @PluginMethod
    public void write(PluginCall call) {
        String data = call.getString("data");

        if (outputStream == null) {
            call.reject("Serial connection is not open.");
            return;
        }

        try {
            outputStream.write(data.getBytes());
            outputStream.flush();
            Log.d(TAG, "Data written to serial port: " + data);
            call.resolve();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to serial port", e);
            call.reject("Failed to write data: " + e.getMessage());
        }
    }

    @PluginMethod
    public void read(PluginCall call) {
        if (inputStream == null) {
            call.reject("Serial connection is not open.");
            return;
        }

        executorService.submit(() -> {
            byte[] buffer = new byte[1024];
            int bytesRead;

            try {
                bytesRead = inputStream.read(buffer);
                if (bytesRead > 0) {
                    String data = new String(buffer, 0, bytesRead);
                    Log.d(TAG, "Data read from serial port: " + data);

                    getBridge().saveCall(call);
                    call.resolve();
                    call.success("data", data);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from serial port", e);
                call.reject("Failed to read data: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        if (serialConnection == null || serialConnection.getInputStream() == null) {
            call.reject("Port not open");
            return;
        }

        if (isReading) {
            call.reject("Already reading");
            return;
        }

        isReading = true;
        JSObject success = new JSObject();
        success.put("message", "Reading started");
        call.resolve(success);
        notifyListeners("readingStarted", success);

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isReading && serialConnection != null) {
                try {
                    int bytesRead = serialConnection.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        String data = new String(buffer, 0, bytesRead);
                        JSObject ret = new JSObject();
                        ret.put("data", data);
                        notifyListeners("dataReceived", ret);
                    }
                    Thread.sleep(100); // Prevent tight loop
                } catch (Exception e) {
                    if (isReading) { // Only notify if we didn't intentionally stop
                        JSObject error = new JSObject();
                        error.put("error", "Read error: " + e.getMessage());
                        notifyListeners("readError", error);
                    }
                    break;
                }
            }
        }).start();
    }

    @PluginMethod
    public void stopReading(PluginCall call) {
        isReading = false;

        try {
            if (serialConnection != null && serialConnection.getInputStream() != null) {
                serialConnection.getInputStream().close(); // ✅ FIX: Forcefully close stream
            }
        } catch (IOException e) {
            call.reject("Error closing input stream: " + e.getMessage());
            return;
        }

        call.resolve();
        JSObject ret = new JSObject();
        ret.put("message", "Reading stopped");
        notifyListeners("readingStopped", ret); // ✅ Notify TypeScript about reading stopped
    }

    @PluginMethod
    public void close(PluginCall call) {
        if (serialConnection != null) {
            serialConnection.closeConnection();
        }
        call.resolve();
        JSObject ret = new JSObject();
        ret.put("message", "Connection closed");
        notifyListeners("connectionClosed", ret); // ✅ Notify TypeScript about connection closed
    }
}