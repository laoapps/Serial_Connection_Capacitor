package com.laoapps.plugins.serialconnectioncapacitor;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.app.PendingIntent;
import android.os.Build;
import android.util.Log;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.driver.Ch34xSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;

import android.serialport.SerialPort; // Updated version

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@CapacitorPlugin(name = "SerialCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private static final String TAG = "SerialConnCap";
    private UsbSerialPort usbSerialPort;
    private SerialPort serialPort; // Unified SerialPort
    private volatile boolean isReading = false;
    private UsbManager usbManager;
    private BroadcastReceiver usbPermissionReceiver;

    static {
        System.loadLibrary("serial_port"); // Single library for both
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void load() {
        usbManager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        IntentFilter filter = new IntentFilter("com.laoapps.plugins.USB_PERMISSION");
        usbPermissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.laoapps.plugins.USB_PERMISSION".equals(action)) {
                    synchronized (this) {
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            Log.d(TAG, "USB permission granted: " + (device != null ? device.getDeviceName() : "null"));
                        } else {
                            Log.w(TAG, "USB permission denied: " + (device != null ? device.getDeviceName() : "null"));
                        }
                    }
                }
            }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getContext().registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            getContext().registerReceiver(usbPermissionReceiver, filter);
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (usbPermissionReceiver != null) {
            getContext().unregisterReceiver(usbPermissionReceiver);
            usbPermissionReceiver = null;
        }
    }

    @PluginMethod
    public void listPorts(PluginCall call) {
        Log.d(TAG, "listPorts invoked: " + call.getData().toString());
        JSObject ret = new JSObject();
        JSObject ports = new JSObject();
        int index = 0;

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Log.d(TAG, "USB devices detected: " + deviceList.size());
        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, "USB device: " + device.getDeviceName());
        }

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        ProbeTable customTable = new ProbeTable();
        customTable.addProduct(0x058F, 0x636F, Ch34xSerialDriver.class);
        customTable.addProduct(0xA69C, 0x8801, Ch34xSerialDriver.class);
        UsbSerialProber customProber = new UsbSerialProber(customTable);
        availableDrivers.addAll(customProber.findAllDrivers(usbManager));

        for (UsbSerialDriver driver : availableDrivers) {
            UsbDevice device = driver.getDevice();
            String portName = device.getDeviceName();
            if (!usbManager.hasPermission(device)) {
                Log.d(TAG, "Requesting USB permission for: " + portName);
                usbManager.requestPermission(device, PendingIntent.getBroadcast(getContext(), 0, new Intent("com.laoapps.plugins.USB_PERMISSION"), PendingIntent.FLAG_UPDATE_CURRENT));
                continue;
            }
            ports.put(portName, index++);
            Log.d(TAG, "USB port added: " + portName);
        }

        try {
            File devDir = new File("/dev");
            File[] serialFiles = devDir.listFiles((dir, name) -> name.startsWith("ttyS"));
            if (serialFiles != null) {
                Log.d(TAG, "Native serial files found: " + serialFiles.length);
                for (File file : serialFiles) {
                    String portName = file.getAbsolutePath();
                    ports.put(portName, index++);
                    Log.d(TAG, "Native port added: " + portName);
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
    public void openSerial(PluginCall call) {
        Log.d(TAG, "openSerial invoked: " + call.getData().toString());
        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        if (portName == null) {
            call.reject("Port name is required");
            return;
        }

        synchronized (this) {
            if (serialPort != null) {
                call.reject("A serial connection is already open; close it first");
                return;
            }
            if (usbSerialPort != null) {
                call.reject("USB serial connection is already open; close it first");
                return;
            }

            try {
                serialPort = new SerialPort(portName, baudRate);
                Log.d(TAG, "Serial opened successfully on " + portName);
                JSObject ret = new JSObject();
                ret.put("message", "Serial connection opened for " + portName);
                notifyListeners("serialOpened", ret);
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("Permission denied: " + e.getMessage());
            } catch (IOException e) {
                call.reject("Failed to open serial connection: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void openUsbSerial(PluginCall call) {
        Log.d(TAG, "openUsbSerial invoked: " + call.getData().toString());
        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        if (portName == null) {
            call.reject("Port name is required");
            return;
        }

        if (portName.startsWith("/dev/ttyS")) {
            call.reject("Use openSerial for native serial ports (e.g., /dev/ttyS*); openUsbSerial is for USB devices");
            return;
        }

        synchronized (this) {
            if (usbSerialPort != null) {
                call.reject("USB serial connection already open");
                return;
            }
            if (serialPort != null) {
                call.reject("Serial connection is already open; close it first");
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
                call.reject("Device not found: " + portName);
                return;
            }

            if (!usbManager.hasPermission(device)) {
                Log.d(TAG, "Requesting USB permission for: " + portName);
                usbManager.requestPermission(device, PendingIntent.getBroadcast(getContext(), 0, new Intent("com.laoapps.plugins.USB_PERMISSION"), PendingIntent.FLAG_UPDATE_CURRENT));
                call.reject("USB permission pending for: " + portName);
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
                call.reject("No compatible driver found for: " + portName);
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
                call.reject("Failed to open USB serial: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        Log.d(TAG, "write invoked: " + call.getData().toString());
        String data = call.getString("data");
        if (data == null || data.length() % 2 != 0) {
            call.reject("Invalid hex data: must be even-length string");
            return;
        }

        byte[] bytes = hexStringToByteArray(data);
        JSObject ret = new JSObject();

        synchronized (this) {
            if (serialPort != null) {
                try {
                    serialPort.getOutputStream().write(bytes);
                    serialPort.getOutputStream().flush();
                    Log.d(TAG, "Data written to serial: " + data);
                    ret.put("message", "Data written successfully to serial");
                    ret.put("data", data);
                    ret.put("bytes", bytes);
                    notifyListeners("serialWriteSuccess", ret);
                    call.resolve(ret);
                } catch (IOException e) {
                    call.reject("Failed to write to serial: " + e.getMessage());
                }
            } else if (usbSerialPort != null) {
                try {
                    usbSerialPort.write(bytes, 5000);
                    Log.d(TAG, "Data written to USB serial: " + data);
                    ret.put("message", "Data written successfully to USB serial");
                    ret.put("data", data);
                    ret.put("bytes", bytes);
                    notifyListeners("usbWriteSuccess", ret);
                    call.resolve(ret);
                } catch (Exception e) {
                    call.reject("Failed to write to USB serial: " + e.getMessage());
                }
            } else {
                call.reject("No serial connection open");
            }
        }
    }

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        Log.d(TAG, "startReading invoked: " + call.getData().toString());
        String portName = call.getString("portName");

        if (usbSerialPort == null && serialPort == null) {
            call.reject("No serial connection open");
            return;
        }

        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "Reading started");
        notifyListeners("readingStarted", ret);
        call.resolve(ret);

        if (usbSerialPort != null && (portName == null || portName.equals(usbSerialPort.getDriver().getDevice().getDeviceName()))) {
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    try {
                        if (usbSerialPort != null) {
                            int len = usbSerialPort.read(buffer, 1000);
                            if (len > 0) {
                                String receivedData = bytesToHex(buffer, len);
                                Log.d(TAG, "Received from USB serial: " + receivedData);
                                JSObject dataEvent = new JSObject();
                                dataEvent.put("data", receivedData);
                                notifyListeners("dataReceived", dataEvent);
                            }
                        } else {
                            break;
                        }
                    } catch (Exception e) {
                        if (isReading) Log.e(TAG, "USB read error: " + e.getMessage());
                    }
                }
            }).start();
        }

        if (serialPort != null && (portName == null || portName.equals(serialPort.gettDdevicePath()))) {
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isReading) {
                    synchronized (this) {
                        if (serialPort == null) {
                            Log.w(TAG, "Serial port closed, stopping read thread");
                            break;
                        }
                        try {
                            int available = serialPort.getInputStream().available();
                            if (available > 0) {
                                int len = serialPort.getInputStream().read(buffer, 0, Math.min(available, buffer.length));
                                if (len > 0) {
                                    String receivedData = bytesToHex(buffer, len);
                                    Log.d(TAG, "Received from serial: " + receivedData);
                                    JSObject dataEvent = new JSObject();
                                    dataEvent.put("data", receivedData);
                                    notifyListeners("dataReceived", dataEvent);
                                }
                            } else {
                                Thread.sleep(10);
                            }
                        } catch (Exception e) {
                            if (isReading) Log.e(TAG, "Serial read error: " + e.getMessage());
                        }
                    }
                }
            }).start();
        }
    }

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    @PluginMethod
    public void stopReading(PluginCall call) {
        Log.d(TAG, "stopReading invoked: " + call.getData().toString());
        isReading = false;
        JSObject ret = new JSObject();
        ret.put("message", "Reading stopped");
        notifyListeners("readingStopped", ret);
        call.resolve(ret);
    }

    @PluginMethod
    public void close(PluginCall call) {
        Log.d(TAG, "close invoked: " + call.getData().toString());
        String portName = call.getString("portName");
        JSObject ret = new JSObject();

        synchronized (this) {
            if (serialPort != null && (portName == null || portName.equals(serialPort.gettDdevicePath()))) {
                try {
                    serialPort.close();
                    serialPort = null;
                    Log.d(TAG, "Serial closed");
                    ret.put("message", "Serial connection closed");
                } catch (IOException e) {
                    call.reject("Failed to close serial: " + e.getMessage());
                    return;
                }
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
            call.resolve(ret);
        }
    }
}