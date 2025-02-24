package com.laoapps.plugins.serialconnectioncapacitor;

import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.IOException;
import android.serialport.SerialPort;
@CapacitorPlugin(name = "SerialConnectionCapacitor2")
public class SerialConnectionCapacitorPlugin2 extends Plugin {
    private static final String TAG = "SerialConnectionCapacitor";
    private SerialPort serialPort;
    private boolean isReading = false;

    @Override
    public void load() {
        try {
            System.loadLibrary("msc");
            System.loadLibrary("serial_port");
            Log.d(TAG, "Native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native libraries: " + e.getMessage());
        }
    }

    @PluginMethod
    public void listPorts(PluginCall call) {
        JSObject ret = new JSObject();
        JSObject ports = new JSObject();

        String[] possiblePorts = {"/dev/ttyS0", "/dev/ttyS1", "/dev/ttyUSB0", "/dev/ttyUSB1"};
        int index = 0;
        for (String portName : possiblePorts) {
            File portFile = new File(portName);
            if (portFile.exists() && portFile.canRead()) {
                ports.put(portName, index++);
            }
        }

        ret.put("ports", ports);
        call.resolve(ret);
    }

    @PluginMethod
    public void open(PluginCall call) {
        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        if (portName == null) {
            call.reject("Port path is required");
            return;
        }

        if (serialPort != null) {
            call.reject("Connection already open");
            return;
        }

        try {
            serialPort = createSerialPort(portName, baudRate);
            Log.d(TAG, "Connection opened successfully on " + portName);
            JSObject ret = new JSObject();
            ret.put("message", "Connection opened successfully");
            notifyListeners("connectionOpened", ret);
            call.resolve();
        } catch (IOException | SecurityException e) {
            Log.e(TAG, "Failed to open connection: " + e.getMessage());
            JSObject error = new JSObject();
            error.put("error", "Failed to open connection: " + e.getMessage());
            notifyListeners("connectionError", error);
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
            serialPort.getOutputStream().write(bytes);
            serialPort.getOutputStream().flush();
            Log.d(TAG, "Data written to serial port: " + data);
            JSObject ret = new JSObject();
            ret.put("message", "Data written successfully");
            notifyListeners("nativeWriteSuccess", ret);
            call.resolve();
        } catch (IOException e) {
            Log.e(TAG, "Write error: " + e.getMessage());
            JSObject error = new JSObject();
            error.put("error", "Write error: " + e.getMessage());
            notifyListeners("writeError", error);
            call.reject("Write error: " + e.getMessage());
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
            while (isReading && serialPort != null) {
                try {
                    int bytesRead = serialPort.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        String data = new String(buffer, 0, bytesRead);
                        JSObject dataEvent = new JSObject();
                        dataEvent.put("data", data);
                        notifyListeners("dataReceived", dataEvent);
                    }
                } catch (IOException e) {
                    if (isReading) {
                        JSObject error = new JSObject();
                        error.put("error", "Read error: " + e.getMessage());
                        notifyListeners("readError", error);
                    }
                    isReading = false;
                }
            }
        }).start();
    }

    @PluginMethod
    public void stopReading(PluginCall call) {
        isReading = false;
        if (serialPort != null && serialPort.getInputStream() != null) {
            try {
                serialPort.getInputStream().close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing input stream: " + e.getMessage());
            }
        }
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
            } catch (IOException e) {
                Log.e(TAG, "Failed to close connection: " + e.getMessage());
                call.reject("Failed to close connection: " + e.getMessage());
                return;
            }
        }
        JSObject ret = new JSObject();
        ret.put("message", "Connection closed");
        notifyListeners("connectionClosed", ret);
        call.resolve();
    }
    protected SerialPort createSerialPort(String portName, int baudRate) throws IOException {
        return new SerialPort(new File(portName), baudRate, 0);
    }
}