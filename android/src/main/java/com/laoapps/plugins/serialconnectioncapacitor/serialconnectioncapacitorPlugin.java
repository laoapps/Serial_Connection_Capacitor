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

    @Override
    public void load() {
        serialConnection = new SerialConnectionCapacitor(getContext());
    }

    @PluginMethod
    public void listPorts(PluginCall call) {
        JSObject ret = new JSObject();
        JSObject ports = new JSObject();

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
        String data = call.getString("data");
        if (data == null || serialConnection.getOutputStream() == null) {
            call.reject("Invalid parameters or port not open");
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
    public void read(PluginCall call) {
        if (serialConnection.getInputStream() == null) {
            call.reject("Port not open");
            return;
        }

        try {
            byte[] buffer = new byte[1024];
            int bytesRead = serialConnection.getInputStream().read(buffer);
            
            JSObject ret = new JSObject();
            ret.put("data", new String(buffer, 0, bytesRead));
            call.resolve(ret);
        } catch (IOException e) {
            call.reject("Read error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void close(PluginCall call) {
        serialConnection.closeConnection();
        call.resolve();
    }
}