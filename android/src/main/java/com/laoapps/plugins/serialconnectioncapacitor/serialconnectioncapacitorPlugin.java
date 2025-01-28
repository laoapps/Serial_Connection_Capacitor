package com.laoapps.plugins.serialconnectioncapacitor;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.JSObject;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@CapacitorPlugin(name = "SerialConnectionCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private UsbDeviceConnection connection;
    private InputStream inputStream;
    private OutputStream outputStream;

    public void open(PluginCall call) {
        String portPath = call.getString("portPath");
        int baudRate = call.getInt("baudRate", 9600);

        if (portPath == null) {
            call.reject("Port path is required");
            return;
        }

        try {
            UsbManager manager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
            for (UsbDevice device : manager.getDeviceList().values()) {
                if (device.getDeviceName().equals(portPath)) {
                    connection = manager.openDevice(device);
                    if (connection != null) {
                        UsbInterface usbInterface = device.getInterface(0);
                        UsbEndpoint endpointIn = usbInterface.getEndpoint(0);
                        UsbEndpoint endpointOut = usbInterface.getEndpoint(1);

                        connection.claimInterface(usbInterface, true);

                        inputStream = new SerialInputStream(connection, endpointIn);
                        outputStream = new SerialOutputStream(connection, endpointOut);
                        call.resolve();
                        return;
                    }
                }
            }
            call.reject("Device not found");
        } catch (Exception e) {
            call.reject("Error opening serial port: " + e.getMessage());
        }
    }

    public void write(PluginCall call) {
        String data = call.getString("data");
        if (data == null || outputStream == null) {
            call.reject("Invalid parameters or port not open");
            return;
        }

        try {
            outputStream.write(data.getBytes());
            outputStream.flush();
            call.resolve();
        } catch (IOException e) {
            call.reject("Write error: " + e.getMessage());
        }
    }

    public void read(PluginCall call) {
        if (inputStream == null) {
            call.reject("Port not open");
            return;
        }

        try {
            byte[] buffer = new byte[1024];
            int bytesRead = inputStream.read(buffer);
            
            JSObject ret = new JSObject();
            ret.put("data", new String(buffer, 0, bytesRead));
            call.resolve(ret);
        } catch (IOException e) {
            call.reject("Read error: " + e.getMessage());
        }
    }

    public void close(PluginCall call) {
        if (connection != null) {
            connection.close();
            connection = null;
            inputStream = null;
            outputStream = null;
        }
        call.resolve();
    }
}