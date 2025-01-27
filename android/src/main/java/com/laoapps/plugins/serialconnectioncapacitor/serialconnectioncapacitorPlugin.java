package com.laoapps.plugins.serialconnectioncapacitor;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;

@CapacitorPlugin(name = "SerialConnectionCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {

    private UsbSerialPort serialPort;
    private UsbDeviceConnection connection;

    @PluginMethod
    public void open(PluginCall call) {
        String portPath = call.getString("portPath");
        int baudRate = call.getInt("baudRate", 57600); // Default to 57600 if not provided

        if (portPath == null) {
            call.reject("Port path is required");
            return;
        }

        UsbManager usbManager = (UsbManager) getContext().getSystemService(UsbManager.class);

        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getDeviceName().equals(portPath)) {
                UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
                if (driver != null) {
                    serialPort = driver.getPorts().get(0);
                    connection = usbManager.openDevice(device);
                    if (connection != null) {
                        try {
                            serialPort.open(connection);
                            serialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                            Log.d("SerialPlugin", "Serial port opened at baud rate: " + baudRate);
                            call.resolve();
                            return;
                        } catch (IOException e) {
                            call.reject("Error opening serial port: " + e.getMessage());
                            return;
                        }
                    }
                }
            }
        }

        call.reject("Unable to open port: " + portPath);
    }

    @PluginMethod
    public void write(PluginCall call) {
        String command = call.getString("command");
        if (command == null || command.isEmpty()) {
            call.reject("Command is required");
            return;
        }

        if (serialPort == null) {
            call.reject("Port is not open");
            return;
        }

        try {
            serialPort.write(command.getBytes(), 1000);
            call.resolve();
        } catch (IOException e) {
            call.reject("Error writing to port: " + e.getMessage());
        }
    }

    @PluginMethod
    public void read(PluginCall call) {
        if (serialPort == null) {
            call.reject("Port is not open");
            return;
        }

        try {
            byte[] buffer = new byte[1024];
            int bytesRead = serialPort.read(buffer, 1000);
            if (bytesRead > 0) {
                String data = new String(buffer, 0, bytesRead);
                call.resolve(JSObject.fromJSONObject("{\"data\":\"" + data + "\"}"));
            } else {
                call.resolve(JSObject.fromJSONObject("{\"data\":\"\"}"));
            }
        } catch (IOException e) {
            call.reject("Error reading from port: " + e.getMessage());
        }
    }

    @PluginMethod
    public void close(PluginCall call) {
        try {
            if (serialPort != null) serialPort.close();
            if (connection != null) connection.close();

            serialPort = null;
            connection = null;

            Log.d("SerialPlugin", "Serial port closed");
            call.resolve();
        } catch (IOException e) {
            call.reject("Error closing port: " + e.getMessage());
        }
    }
}
