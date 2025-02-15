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
        if (serialConnection == null) { // ✅ FIX: Check initialization
            call.reject("SerialConnection not initialized");
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
            call.resolve();
        } else {
            call.reject("Failed to open connection");
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        if (serialConnection == null || serialConnection.getOutputStream() == null) { // ✅ FIX: Check initialization
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
            call.resolve();
        } catch (IOException e) {
            call.reject("Write error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        if (serialConnection == null || serialConnection.getInputStream() == null) { // ✅ FIX: Check initialization
            call.reject("Port not open");
            return;
        }

        isReading = true;
        call.resolve(); // ✅ FIX: Resolve before starting thread

        new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    int bytesRead = serialConnection.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        JSObject ret = new JSObject();
                        ret.put("data", new String(buffer, 0, bytesRead));
                        notifyListeners("dataReceived", ret); // ✅ Send event to TypeScript
                    }
                }
            } catch (IOException e) {
                JSObject error = new JSObject();
                error.put("error", "Read error: " + e.getMessage());
                notifyListeners("readError", error); // ✅ Notify TypeScript about error
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
    }

    @PluginMethod
    public void close(PluginCall call) {
        if (serialConnection != null) {
            serialConnection.closeConnection();
        }
        call.resolve();
    }
}