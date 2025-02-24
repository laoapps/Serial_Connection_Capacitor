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
import android.serialport.SerialPort;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@CapacitorPlugin(name = "SerialConnectionCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private static final String TAG = "SerialConnectionCapacitor";
    private UsbSerialPort usbSerialPort;
    private final HashMap<String, SerialPort> nativeSerialPorts = new HashMap<>();  // Synchronized access
    private volatile boolean isReading = false;  // Thread-safe flag
    private UsbManager usbManager;

    @Override
    public void load() {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
    }

    @PluginMethod
    public void listPorts(PluginCall call) {
        Log.d(TAG, "Native listPorts invoked: " + call.getData().toString());
        JSObject ret = new JSObject();
        JSObject ports = new JSObject();

        // List USB serial ports
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        int index = 0;
        for (UsbSerialDriver driver : availableDrivers) {
            UsbDevice device = driver.getDevice();
            String portName = device.getDeviceName();
            ports.put(portName, index++);
        }

        // List native serial ports
        try {
            File devDir = new File("/dev");
            File[] serialFiles = devDir.listFiles((dir, name) -> name.startsWith("ttyS"));
            if (serialFiles != null) {
                for (File file : serialFiles) {
                    String portName = file.getAbsolutePath();
                    if (!file.canRead() || !file.canWrite()) {
                        try {
                            Process process = Runtime.getRuntime().exec("/system/xbin/su");
                            DataOutputStream os = new DataOutputStream(process.getOutputStream());
                            os.writeBytes("chmod 666 " + portName + "\n");
                            os.writeBytes("exit\n");
                            os.flush();
                            process.waitFor();
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to chmod " + portName + ": " + e.getMessage());
                        }
                    }
                    if (file.canRead() || file.canWrite()) {
                        ports.put(portName, index++);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to list native serial ports: " + e.getMessage());
        }

        ret.put("ports", ports);
        Log.d(TAG, "Ports listed: " + ret.toString());
        notifyListeners("portsListed", ret);
        call.resolve(ret);
    }

    @PluginMethod
    public void openNativeSerial(PluginCall call) {
        Log.d(TAG, "Native openNativeSerial invoked: " + call.getData().toString());

        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        if (portName == null) {
            call.reject("Port name is required");
            return;
        }

        synchronized (nativeSerialPorts) {
            if (nativeSerialPorts.containsKey(portName)) {
                call.reject("Native serial connection already open for " + portName);
                return;
            }

            if (usbSerialPort != null) {
                call.reject("USB serial connection is already open; close it first");
                return;
            }

            try {
                SerialPort serialPort = new SerialPort(portName, baudRate);
                nativeSerialPorts.put(portName, serialPort);
                Log.d(TAG, "Native serial opened successfully on " + portName);
                JSObject ret = new JSObject();
                ret.put("message", "Native serial connection opened for " + portName);
                notifyListeners("nativeSerialOpened", ret);
                call.resolve();
            } catch (SecurityException e) {
                call.reject("Permission denied; ensure device is rooted and su is available: " + e.getMessage());
            } catch (IOException e) {
                call.reject("Failed to open native serial connection: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void openUsbSerial(PluginCall call) {
        Log.d(TAG, "Native openUsbSerial invoked: " + call.getData().toString());

        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        if (portName == null) {
            call.reject("Port name is required");
            return;
        }

        if (portName.startsWith("/dev/ttyS")) {
            call.reject("Use openNativeSerial for native serial ports (e.g., /dev/ttyS*); openUsbSerial is for USB devices");
            return;
        }

        synchronized (nativeSerialPorts) {
            if (usbSerialPort != null) {
                call.reject("USB serial connection already open");
                return;
            }

            if (!nativeSerialPorts.isEmpty()) {
                call.reject("Native serial connection(s) are already open; close them first");
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

            usbSerialPort = driver.getPorts().get(0);
            try {
                usbSerialPort.open(usbManager.openDevice(device));
                usbSerialPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                usbSerialPort.setDTR(true);
                usbSerialPort.setRTS(true);

                Log.d(TAG, "USB serial opened successfully on " + portName);
                JSObject ret = new JSObject();
                ret.put("message", "USB serial connection opened");
                notifyListeners("usbSerialOpened", ret);
                call.resolve();
            } catch (Exception e) {
                usbSerialPort = null;
                call.reject("Failed to open USB serial connection: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        Log.d(TAG, "Native write invoked: " + call.getData().toString());

        String data = call.getString("data");
        String portName = call.getString("portName");
        if (data == null) {
            call.reject("Invalid data");
            return;
        }

        byte[] bytes = data.getBytes();
        JSObject ret = new JSObject();

        synchronized (nativeSerialPorts) {
            if (portName != null && nativeSerialPorts.containsKey(portName)) {
                SerialPort serialPort = nativeSerialPorts.get(portName);
                if (serialPort == null) {
                    call.reject("Native serial port " + portName + " is closed");
                    return;
                }
                try {
                    serialPort.getOutputStream().write(bytes);
                    serialPort.getOutputStream().flush();
                    Log.d(TAG, "Data written to native serial " + portName + ": " + data);
                    ret.put("message", "Data written successfully to native serial " + portName);
                    notifyListeners("nativeWriteSuccess", ret);
                    call.resolve();
                } catch (IOException e) {
                    call.reject("Failed to write to native serial " + portName + ": " + e.getMessage());
                }
            } else if (usbSerialPort != null) {
                try {
                    usbSerialPort.write(bytes, 5000);
                    Log.d(TAG, "Data written to USB serial: " + data);
                    ret.put("message", "Data written successfully to USB serial");
                    notifyListeners("usbWriteSuccess", ret);
                    call.resolve();
                } catch (Exception e) {
                    call.reject("Write to USB serial failed: " + e.getMessage());
                }
            } else {
                call.reject("No serial connection open for " + (portName != null ? portName : "any port"));
            }
        }
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        Log.d(TAG, "Native startReading invoked: " + call.getData().toString());

        String portName = call.getString("portName");
        if (usbSerialPort == null && nativeSerialPorts.isEmpty()) {
            call.reject("No serial connection open");
            return;
        }
    
        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "Reading started");
        notifyListeners("readingStarted", ret);
        call.resolve();
    
        if (usbSerialPort != null && (portName == null || portName.equals(usbSerialPort.getDriver().getDevice().getDeviceName()))) {
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    try {
                        if (usbSerialPort != null) {
                            int len = usbSerialPort.read(buffer, 1000);
                            if (len > 0) {
                                String receivedData = new String(buffer, 0, len);
                                Log.d(TAG, "Received from USB serial: " + receivedData);
                                JSObject dataEvent = new JSObject();
                                dataEvent.put("data", receivedData);
                                notifyListeners("dataReceived", dataEvent);
                            }
                        } else {
                            break;
                        }
                    } catch (Exception e) {
                        if (isReading) {
                            Log.e(TAG, "USB read error: " + e.getMessage());
                        }
                    }
                }
            }).start();
        }
    
        if (portName != null && nativeSerialPorts.containsKey(portName)) {
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    synchronized (nativeSerialPorts) {
                        SerialPort serialPort = nativeSerialPorts.get(portName);
                        if (serialPort == null) {
                            Log.w(TAG, "Native port " + portName + " closed, stopping read thread");
                            break;
                        }
                        try {
                            int available = serialPort.getInputStream().available();
                            if (available > 0) {
                                int len = serialPort.getInputStream().read(buffer, 0, Math.min(available, buffer.length));
                                if (len > 0) {
                                    String receivedData = new String(buffer, 0, len);
                                    Log.d(TAG, "Received from native serial " + portName + ": " + receivedData);
                                    JSObject dataEvent = new JSObject();
                                    dataEvent.put("data", receivedData);
                                    notifyListeners("dataReceived", dataEvent);
                                }
                            } else {
                                Thread.sleep(10);
                            }
                        } catch (Exception e) {
                            if (isReading) {
                                Log.e(TAG, "Native read error on " + portName + ": " + e.getMessage());
                            }
                        }
                    }
                }
            }).start();
        } else if (portName != null && !nativeSerialPorts.containsKey(portName)) {
            call.reject("No native serial connection open for " + portName);
        }
    }

    @PluginMethod
    public void stopReading(PluginCall call) {
        Log.d(TAG, "Native stopReading invoked: " + call.getData().toString());

        isReading = false;
        JSObject ret = new JSObject();
        ret.put("message", "Reading stopped");
        notifyListeners("readingStopped", ret);
        call.resolve();
    }

    @PluginMethod
    public void close(PluginCall call) {
        Log.d(TAG, "Native close invoked: " + call.getData().toString());

        String portName = call.getString("portName");
        JSObject ret = new JSObject();

        synchronized (nativeSerialPorts) {
            if (portName != null && nativeSerialPorts.containsKey(portName)) {
                try {
                    SerialPort serialPort = nativeSerialPorts.remove(portName);
                    if (serialPort != null) {
                        serialPort.close();
                        Log.d(TAG, "Native serial closed for " + portName);
                        ret.put("message", "Native serial connection closed for " + portName);
                    }
                } catch (IOException e) {
                    call.reject("Failed to close native serial " + portName + ": " + e.getMessage());
                    return;
                }
            } else if (portName == null && !nativeSerialPorts.isEmpty()) {
                for (String key : nativeSerialPorts.keySet()) {
                    try {
                        SerialPort serialPort = nativeSerialPorts.remove(key);
                        if (serialPort != null) {
                            serialPort.close();
                            Log.d(TAG, "Native serial closed for " + key);
                        }
                    } catch (IOException e) {
                        call.reject("Failed to close native serial " + key + ": " + e.getMessage());
                        return;
                    }
                }
                ret.put("message", "All native serial connections closed");
            }

            if (usbSerialPort != null && (portName == null || portName.equals(usbSerialPort.getDriver().getDevice().getDeviceName()))) {
                try {
                    usbSerialPort.close();
                    usbSerialPort = null;
                    Log.d(TAG, "USB serial closed");
                    ret.put("message", "USB serial connection closed");
                } catch (Exception e) {
                    call.reject("Failed to close USB serial: " + e.getMessage());
                    return;
                }
            }

            if (ret.length() == 0) {
                ret.put("message", "No connection to close for " + (portName != null ? portName : "any port"));
            }
            notifyListeners("connectionClosed", ret);
            call.resolve();
        }
    }
}