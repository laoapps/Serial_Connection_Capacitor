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
import java.io.DataOutputStream;
import java.io.DataInputStream;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@CapacitorPlugin(name = "SerialConnectionCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private static final String TAG = "SerialConnectionCapacitor";
    private UsbSerialPort usbSerialPort;  // For mik3y/usb-serial-for-android
    private SerialPort nativeSerialPort;  // For libserial_port.so
    private boolean isReading = false;
    private UsbManager usbManager;

    @Override
    public void load() {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
    }


    @PluginMethod
    public void listPorts(PluginCall call) {
        JSObject ret = new JSObject();
        JSObject usbPorts = new JSObject();
        JSObject nativePorts = new JSObject();

        // List USB serial ports
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        int usbIndex = 0;
        for (UsbSerialDriver driver : availableDrivers) {
            UsbDevice device = driver.getDevice();
            String portName = device.getDeviceName();
            usbPorts.put(portName, usbIndex++);
        }

        // List native serial ports
        try {
            File devDir = new File("/dev");
            File[] serialFiles = devDir.listFiles((dir, name) -> name.startsWith("ttyS"));
            if (serialFiles != null) {
                int nativeIndex = 0;
                for (File file : serialFiles) {
                    String portName = file.getAbsolutePath();
                    // Attempt to make accessible if not already
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
                    // Re-check accessibility
                    if (file.canRead() || file.canWrite()) {
                        nativePorts.put(portName, nativeIndex++);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to list native serial ports: " + e.getMessage());
        }

        ret.put("usbPorts", usbPorts);
        ret.put("nativePorts", nativePorts);
        Log.d(TAG, "Ports listed: " + ret.toString());
        call.resolve(ret);
    }

    @PluginMethod
    public void openNativeSerial(PluginCall call) {
        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        if (portName == null) {
            call.reject("Port name is required");
            return;
        }

        if (nativeSerialPort != null) {
            call.reject("Native serial connection already open");
            return;
        }

        if (usbSerialPort != null) {
            call.reject("USB serial connection is already open; close it first");
            return;
        }

        try {
            nativeSerialPort = new SerialPort(portName, baudRate);
            Log.d(TAG, "Native serial opened successfully on " + portName);
            JSObject ret = new JSObject();
            ret.put("message", "Native serial connection opened");
            notifyListeners("nativeSerialOpened", ret);
            call.resolve(ret);
        } catch (SecurityException e) {
            call.reject("Permission denied; ensure device is rooted and su is available: " + e.getMessage());
        } catch (IOException e) {
            call.reject("Failed to open native serial connection: " + e.getMessage());
        }
    }

    @PluginMethod
public void openUsbSerial(PluginCall call) {
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

    if (usbSerialPort != null) {
        call.reject("USB serial connection already open");
        return;
    }

    if (nativeSerialPort != null) {
        call.reject("Native serial connection is already open; close it first");
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
        call.resolve(ret);
    } catch (Exception e) {
        usbSerialPort = null;
        call.reject("Failed to open USB serial connection: " + e.getMessage());
    }
}

    @PluginMethod
    public void write(PluginCall call) {
        String data = call.getString("data");
        if (data == null) {
            call.reject("Invalid data");
            return;
        }

        byte[] bytes = data.getBytes();
        JSObject ret = new JSObject();

        if (nativeSerialPort != null) {
            try {
                nativeSerialPort.getOutputStream().write(bytes);
                nativeSerialPort.getOutputStream().flush();
                Log.d(TAG, "Data written to native serial: " + data);
                ret.put("message", "Data written successfully to native serial");
                notifyListeners("nativeWriteSuccess", ret);
                call.resolve(ret);
            } catch (IOException e) {
                call.reject("Failed to write to native serial: " + e.getMessage());
            }
        } else if (usbSerialPort != null) {
            try {
                usbSerialPort.write(bytes, 5000);
                Log.d(TAG, "Data written to USB serial: " + data);
                ret.put("message", "Data written successfully to USB serial");
                notifyListeners("usbWriteSuccess", ret);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("Write to USB serial failed: " + e.getMessage());
            }
        } else {
            call.reject("No serial connection open");
        }
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        if (usbSerialPort == null && nativeSerialPort == null) {
            call.reject("No serial connection open");
            return;
        }

        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "Reading started");
        notifyListeners("readingStarted", ret);
        call.resolve(ret);

        if (usbSerialPort != null) {
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    try {
                        int len = usbSerialPort.read(buffer, 1000);
                        if (len > 0) {
                            String receivedData = new String(buffer, 0, len);
                            JSObject dataEvent = new JSObject();
                            dataEvent.put("data", receivedData);
                            notifyListeners("dataReceived", dataEvent);
                        }
                    } catch (Exception e) {
                        if (isReading) {
                            Log.e(TAG, "USB read error: " + e.getMessage());
                        }
                    }
                }
            }).start();
        }

        if (nativeSerialPort != null) {
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    try {
                        int available = nativeSerialPort.getInputStream().available();
                        if (available > 0) {
                            int len = nativeSerialPort.getInputStream().read(buffer, 0, Math.min(available, buffer.length));
                            if (len > 0) {
                                String receivedData = new String(buffer, 0, len);
                                JSObject dataEvent = new JSObject();
                                dataEvent.put("data", receivedData);
                                notifyListeners("dataReceived", dataEvent);
                            }
                        } else {
                            Thread.sleep(10);  // Avoid busy-waiting
                        }
                    } catch (Exception e) {
                        if (isReading) {
                            Log.e(TAG, "Native read error: " + e.getMessage());
                        }
                    }
                }
            }).start();
        }
    }

    @PluginMethod
    public void stopReading(PluginCall call) {
        isReading = false;
        JSObject ret = new JSObject();
        ret.put("message", "Reading stopped");
        notifyListeners("readingStopped", ret);
        call.resolve(ret);
    }

    @PluginMethod
    public void close(PluginCall call) {
        JSObject ret = new JSObject();
        if (usbSerialPort != null) {
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
        if (nativeSerialPort != null) {
            try {
                nativeSerialPort.close();
                nativeSerialPort = null;
                Log.d(TAG, "Native serial closed");
                ret.put("message", "Native serial connection closed");
            } catch (IOException e) {
                call.reject("Failed to close native serial: " + e.getMessage());
                return;
            }
        }
        if (ret.length() == 0) {
            ret.put("message", "No connection to close");
        }
        notifyListeners("connectionClosed", ret);
        call.resolve(ret);
    }
}