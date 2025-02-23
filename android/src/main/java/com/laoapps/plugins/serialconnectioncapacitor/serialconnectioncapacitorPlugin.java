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
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.HashMap;
import java.util.List;

@CapacitorPlugin(name = "SerialConnectionCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private static final String TAG = "SerialConnectionCapacitor";
    private UsbSerialPort serialPort;  // Changed from UsbSerialDevice
    private boolean isReading = false;
    private UsbManager usbManager;

    @Override
    public void load() {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
    }

    @PluginMethod
    public void listPorts(PluginCall call) {
        JSObject ret = new JSObject();
        JSObject ports = new JSObject();

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        int index = 0;
        for (UsbDevice device : deviceList.values()) {
            ports.put(device.getDeviceName(), index++);
        }

        ret.put("ports", ports);
        call.resolve(ret);
    }

    @PluginMethod
    public void open(PluginCall call) {
        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        if (portName == null) {
            call.reject("Port name is required");
            return;
        }

        if (serialPort != null) {
            call.reject("Connection already open");
            return;
        }

        UsbDevice device = null;
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice d : deviceList.values()) {
            if (d.getDeviceName().equals(portName)) {
                device = d;
                break;
            }
        }

        if (device == null) {
            call.reject("Device not found");
            return;
        }

        // Probe for drivers
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        UsbSerialDriver driver = null;
        for (UsbSerialDriver d : availableDrivers) {
            if (d.getDevice().getDeviceName().equals(portName)) {
                driver = d;
                break;
            }
        }

        if (driver == null) {
            call.reject("No compatible driver found");
            return;
        }

        serialPort = driver.getPorts().get(0);  // Use first port (adjust for multi-port devices if needed)
        try {
            serialPort.open(usbManager.openDevice(device));
            serialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            serialPort.setDTR(true);  // Optional, mimics some flow control
            serialPort.setRTS(true);

            Log.d(TAG, "Connection opened successfully on " + portName);
            JSObject ret = new JSObject();
            ret.put("message", "Connection opened successfully");
            notifyListeners("connectionOpened", ret);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to open connection: " + e.getMessage());
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        if (serialPort == null) {
            call.reject("Port not open");
            return;
        }

        String data = call.getString("data");
        if (data == null) {
            call.reject("Invalid data");
            return;
        }

        try {
            byte[] bytes = data.getBytes();
            serialPort.write(bytes, 5000);  // 5-second timeout
            Log.d(TAG, "Data written to serial port: " + data);
            JSObject ret = new JSObject();
            ret.put("message", "Data written successfully");
            notifyListeners("writeSuccess", ret);
            call.resolve();
        } catch (Exception e) {
            call.reject("Write failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        if (serialPort == null) {
            call.reject("Port not open");
            return;
        }

        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "Reading started");
        notifyListeners("readingStarted", ret);
        call.resolve();

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isReading) {
                try {
                    int len = serialPort.read(buffer, 1000);  // 1-second timeout
                    if (len > 0) {
                        String receivedData = new String(buffer, 0, len);
                        JSObject dataEvent = new JSObject();
                        dataEvent.put("data", receivedData);
                        notifyListeners("dataReceived", dataEvent);
                    }
                } catch (Exception e) {
                    if (isReading) {
                        Log.e(TAG, "Read error: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    @PluginMethod
    public void stopReading(PluginCall call) {
        isReading = false;
        JSObject ret = new JSObject();
        ret.put("message", "Reading stopped");
        notifyListeners("readingStopped", ret);
        call.resolve();
    }

    @PluginMethod
    public void close(PluginCall call) {
        if (serialPort != null) {
            try {
                serialPort.close();
                serialPort = null;
                Log.d(TAG, "Connection closed");
            } catch (Exception e) {
                Log.e(TAG, "Close error: " + e.getMessage());
            }
        }
        JSObject ret = new JSObject();
        ret.put("message", "Connection closed");
        notifyListeners("connectionClosed", ret);
        call.resolve();
    }
}