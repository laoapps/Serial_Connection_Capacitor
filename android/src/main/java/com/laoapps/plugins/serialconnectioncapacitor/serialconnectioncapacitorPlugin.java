package com.laoapps.plugins.serialconnectioncapacitor;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
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
    private static final String TAG = "SerialConnectionCapacitor";
    private UsbManager usbManager;
    private SerialConnectionCapacitor serialConnection;
    private boolean isReading = false;

    @Override
    public void load() {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        serialConnection = new SerialConnectionCapacitor(getContext());
    }

    @PluginMethod
    public void listPorts(PluginCall call) {
        JSObject ret = new JSObject();
        JSObject ports = new JSObject();
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        int count = 0;
        for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
            if (count >= 10) break; // Limit to 10 devices
            UsbDevice device = entry.getValue();
            ports.put(device.getDeviceName(), device.getDeviceId());
            count++;
        }
        ret.put("ports", ports);
        call.resolve(ret);

        // JSObject ret = new JSObject();
        // HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        // HashMap<String, Integer> portMap = new HashMap<>();
        // for (UsbDevice device : deviceList.values()) {
        //     portMap.put(device.getDeviceName(), device.getDeviceId());
        // }
        // ret.put("ports", portMap); // Capacitor will serialize this as JSON
        // call.resolve(ret);
    }

    @PluginMethod
    public void open(PluginCall call) {
        if (serialConnection == null) {
            call.reject("SerialConnection not initialized");
            return;
        }

        if (serialConnection.isOpen()) {
            call.reject("A connection is already open. Please close the existing connection first.");
            return;
        }

        String portPath = call.getString("portPath");
        int baudRate = call.getInt("baudRate", 9600);

        if (portPath == null) {
            call.reject("Port path is required");
            return;
        }

        boolean success = serialConnection.openConnection(portPath, baudRate);
        if (success) {
            Log.d(TAG, "Connection opened successfully");
            call.resolve();
            JSObject ret = new JSObject();
            ret.put("message", "Connection opened successfully");
            notifyListeners("connectionOpened", ret);
        } else {
            Log.e(TAG, "Failed to open connection");
            call.reject("Failed to open connection");
            JSObject error = new JSObject();
            error.put("error", "Failed to open connection");
            notifyListeners("connectionError", error);
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        if (serialConnection == null || serialConnection.getOutputStream() == null) {
            call.reject("Port not open or SerialConnection is null");
            return;
        }

        String data = call.getString("data");
        if (data == null) {
            call.reject("Invalid data");
            return;
        }

        try {
            serialConnection.getOutputStream().write(data.getBytes());
            serialConnection.getOutputStream().flush();
            Log.d(TAG, "Data written to serial port: " + data);
            call.resolve();
            JSObject ret = new JSObject();
            ret.put("message", "Data written successfully");
            notifyListeners("writeSuccess", ret);
        } catch (IOException e) {
            Log.e(TAG, "Write error: " + e.getMessage());
            call.reject("Write error: " + e.getMessage());
            JSObject error = new JSObject();
            error.put("error", "Write error: " + e.getMessage());
            notifyListeners("writeError", error);
        }
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        if (serialConnection == null || serialConnection.getInputStream() == null) {
            call.reject("Port not open");
            return;
        }

        isReading = true;
        call.resolve();
        JSObject ret = new JSObject();
        ret.put("message", "Reading started");
        notifyListeners("readingStarted", ret);

        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    int bytesRead = serialConnection.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        JSObject data = new JSObject();
                        data.put("data", new String(buffer, 0, bytesRead));
                        notifyListeners("dataReceived", data);
                    }
                }
            } catch (IOException e) {
                JSObject error = new JSObject();
                error.put("error", "Read error: " + e.getMessage());
                notifyListeners("readError", error);
            }
        }).start();
    }

    @PluginMethod
    public void stopReading(PluginCall call) {
        isReading = false;

        try {
            if (serialConnection != null && serialConnection.getInputStream() != null) {
                serialConnection.getInputStream().close();
            }
        } catch (IOException e) {
            call.reject("Error closing input stream: " + e.getMessage());
            return;
        }

        call.resolve();
        JSObject ret = new JSObject();
        ret.put("message", "Reading stopped");
        notifyListeners("readingStopped", ret);
    }

    @PluginMethod
    public void close(PluginCall call) {
        if (serialConnection != null) {
            serialConnection.closeConnection();
        }
        call.resolve();
        JSObject ret = new JSObject();
        ret.put("message", "Connection closed");
        notifyListeners("connectionClosed", ret);
    }
}