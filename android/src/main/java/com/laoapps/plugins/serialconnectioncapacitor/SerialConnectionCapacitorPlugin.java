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



import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;


@CapacitorPlugin(name = "SerialCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {

    // ADH814
    private static final String TAG = "SerialConnCap";
    private final Map<Integer, PluginCall> adh814ResponseListeners = new ConcurrentHashMap<>();
    private volatile boolean isPolling = false;
    private Thread pollingThread;
    private static final int RESPONSE_TIMEOUT_MS = 2000;
    private static final int[] CRC_TABLE = {
            0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
            0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
            0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
            0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
            0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
            0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
            0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
            0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
            0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
            0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
            0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
            0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
            0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
            0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
            0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
            0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
            0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
            0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
            0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
            0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
            0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
            0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
            0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
            0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
            0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
            0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
            0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
            0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
            0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
            0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
            0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
            0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040
    };
    // ADH814


    private UsbSerialPort usbSerialPort;
    private SerialPort serialPort;
    private volatile boolean isReading = false;
    private UsbManager usbManager;
    private BroadcastReceiver usbPermissionReceiver;
    private final Queue<byte[]> commandQueue = new LinkedList<>();
    private byte packNoCounter = 0;
    static {
        System.loadLibrary("serial_port");
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
        // New parameters with defaults
        int dataBits = call.getInt("dataBits", 8); // Default 8
        int stopBits = call.getInt("stopBits", 1); // Default 1
        String parity = call.getString("parity", "none"); // Default "none"
        int bufferSize = call.getInt("bufferSize", 0); // Default 0 (no buffering)
        int flags = call.getInt("flags", 0); // Default 0

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
                serialPort = new SerialPort(portName, baudRate, flags, dataBits, stopBits, parity, bufferSize);
                Log.d(TAG, "Serial opened successfully on " + portName + " with baudRate=" + baudRate +
                        ", dataBits=" + dataBits + ", stopBits=" + stopBits + ", parity=" + parity +
                        ", bufferSize=" + bufferSize);
                JSObject ret = new JSObject();
                ret.put("message", "Serial connection opened for " + portName);
                ret.put("portName", portName);
                ret.put("baudRate", baudRate);
                ret.put("dataBits", dataBits);
                ret.put("stopBits", stopBits);
                ret.put("parity", parity);
                ret.put("bufferSize", bufferSize);
                notifyListeners("serialOpened", ret);
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("Permission denied: " + e.getMessage());
            } catch (IOException e) {
                call.reject("Failed to open serial connection: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                call.reject("Invalid parameter: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void openUsbSerial(PluginCall call) {
        Log.d(TAG, "openUsbSerial invoked: " + call.getData().toString());
        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);
        int dataBits = call.getInt("dataBits", 8); // Default 8
        int stopBits = call.getInt("stopBits", 1); // Default 1
        String parity = call.getString("parity", "none"); // Default "none"

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
                int parityValue = parityToUsbSerialParity(parity);
                usbSerialPort.setParameters(baudRate, dataBits, stopBits, parityValue);
                usbSerialPort.setDTR(true);
                usbSerialPort.setRTS(true);
                Log.d(TAG, "USB serial opened successfully on " + portName + " with baudRate=" + baudRate +
                        ", dataBits=" + dataBits + ", stopBits=" + stopBits + ", parity=" + parity);
                JSObject ret = new JSObject();
                ret.put("message", "USB serial connection opened");
                ret.put("portName", portName);
                ret.put("baudRate", baudRate);
                ret.put("dataBits", dataBits);
                ret.put("stopBits", stopBits);
                ret.put("parity", parity);
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
                    ret.put("bytes", bytesToHex(bytes, bytes.length));
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
                    ret.put("bytes", bytesToHex(bytes, bytes.length));
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

    @PluginMethod
    public void writeVMC(PluginCall call) {
        Log.d(TAG, "writeVMC invoked: " + call.getData().toString());
        String data = call.getString("data");
        if (data == null) {
            call.reject("Data required");
            return;
        }

        try {
            JSObject jsonData = new JSObject(data);
            String command = jsonData.getString("command");
            JSObject params = jsonData.getJSObject("params", new JSObject());
            if (command == null) {
                call.reject("Command name required in data");
                return;
            }

            byte[] packet = buildPacket(command, params);
            Log.d(TAG, "Packet for " + command + ": " + bytesToHex(packet, packet.length));
            synchronized (commandQueue) {
                commandQueue.add(packet);
                Log.d(TAG, "Queued command for VMC: " + bytesToHex(packet, packet.length));
            }

            JSObject ret = new JSObject();
            ret.put("message", "Command queued for VMC");
            ret.put("data", bytesToHex(packet, packet.length));
            notifyListeners("commandQueued", ret);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to parse data or build packet: " + e.getMessage());
        }
    }

    // Helper method to convert parity string to UsbSerialPort parity constant
    private int parityToUsbSerialParity(String parity) {
        switch (parity.toLowerCase()) {
            case "none": return UsbSerialPort.PARITY_NONE;
            case "odd": return UsbSerialPort.PARITY_ODD;
            case "even": return UsbSerialPort.PARITY_EVEN;
            default: throw new IllegalArgumentException("Invalid parity: " + parity);
        }
    }

    private byte[] buildPacket(String command, JSObject params) {
        byte[] stx = {(byte) 0xFA, (byte) 0xFB};
        byte cmdByte = (byte) Integer.parseInt(command.length() > 2 ? command.substring(2) : command, 16);
        byte packNo = getNextPackNo();

        byte[] text;
        Log.d(TAG, "Input command " + command);
        switch (command) {
            case "01": // Slot test
                text = new byte[]{(byte) clampToByte(params.getInteger("slot", 1))};
                break;
            case "31": // Sync
                cmdByte = (byte) 0x31;
                synchronized (commandQueue) {
                    commandQueue.clear();
                    packNoCounter = 0; // Reset PackNO
                    Log.d(TAG, "Queue cleared and PackNO reset on sync command");
                }
                text = new byte[]{packNo};
                break;
            case "06": // Dispense
                cmdByte = (byte) 0x06;
                int slot = clampToByte(params.getInteger("slot", 1));
                int elevator = clampToByte(params.getInteger("elevator", 0));
                int dropSensor = clampToByte(params.getInteger("dropSensor", 1));
                text = new byte[5];
                text[0] = packNo;
                text[1] = (byte) dropSensor;
                text[2] = (byte) elevator;
                text[3] = (byte) 0x00;
                text[4] = (byte) slot;
                break;
            case "11": // Slot status
                cmdByte = (byte) 0x11;
                text = new byte[]{packNo, (byte) clampToByte(params.getInteger("slot", 1))};
                break;
            case "12": // Set selection price
                cmdByte = (byte) 0x12;
                int selectionNumber = clampToByte(params.getInteger("selectionNumber", 0));
                int price = params.getInteger("price", 1);
                text = new byte[7];
                text[0] = packNo;
                text[1] = (byte) (selectionNumber & 0xFF);
                text[2] = (byte) ((selectionNumber >> 8) & 0xFF);
                text[3] = (byte) (price & 0xFF);
                text[4] = (byte) ((price >> 8) & 0xFF);
                text[5] = (byte) ((price >> 16) & 0xFF);
                text[6] = (byte) ((price >> 24) & 0xFF);
                break;
            case "16": // Poll interval
                cmdByte = (byte) 0x16;
                text = new byte[]{packNo, (byte) clampToByte(params.getInteger("ms", 10))};
                break;
            case "25": // Coin report
                cmdByte = (byte) 0x25;
                text = new byte[]{packNo, 0, 0, 0, (byte) clampToByte(params.getInteger("amount", 0))};
                break;
            case "51": // Machine status
                cmdByte = (byte) 0x51;
                text = new byte[]{packNo};
                break;
            case "61": // Read counters
                cmdByte = (byte) 0x61;
                text = new byte[]{packNo};
                break;
            case "7001": // Coin system setting (read)
                cmdByte = (byte) 0x70;
                text = new byte[]{packNo, 0x01, 0x00, 0x00};
                break;
            case "7017": // Unionpay/POS
                cmdByte = (byte) 0x70;
                boolean read1 = Boolean.TRUE.equals(params.getBoolean("read", true));
                text = read1 ? new byte[]{packNo, 0x17, 0x00} :
                        new byte[]{packNo, 0x17, 0x01, (byte) clampToByte(params.getInteger("enable", 0))};
                break;
            case "7018": // Bill value accepted
                cmdByte = (byte) 0x70;
                boolean read = Boolean.TRUE.equals(params.getBoolean("read", true));
                text = read ? new byte[]{packNo, 0x18, 0x00} :
                        new byte[]{packNo, 0x18, (byte) 0x01, (byte) clampToByte(params.getInteger("value", 200))};
                break;
            case "7019": // Bill accepting mode
                cmdByte = (byte) 0x70;
                boolean read2 = Boolean.TRUE.equals(params.getBoolean("read", true));
                text = read2 ? new byte[]{packNo, 0x19, 0x00} :
                        new byte[]{packNo, 0x19, 0x01, (byte) clampToByte(params.getInteger("value", 1))};
                break;
            case "7020": // Bill low-change
                cmdByte = (byte) 0x70;
                boolean read3 = Boolean.TRUE.equals(params.getBoolean("read", true));
                text = read3 ? new byte[]{packNo, 0x20, 0x00} :
                        new byte[]{packNo, 0x20, 0x01, (byte) clampToByte(params.getInteger("enable", 100))};
                break;
            case "7023": // Credit mode
                cmdByte = (byte) 0x70;
                byte mode3 = (byte) clampToByte(params.getInteger("mode", 0));
                text = mode3 == 0x00 ? new byte[]{packNo, 0x23, mode3} :
                        new byte[]{packNo, 0x23, 0x01, mode3};
                break;
            case "7028": // Temp mode
                cmdByte = (byte) 0x70;
                text = new byte[]{packNo, 0x28, 0x01, 0x00, 0x02,
                        (byte) clampToByte(params.getInteger("lowTemp", 5))};
                break;
            case "7016": // Light control
                cmdByte = (byte) 0x70;
                text = new byte[]{packNo, 0x16, 0x01,
                        (byte) clampToByte(params.getInteger("start", 15)),
                        (byte) clampToByte(params.getInteger("end", 10))};
                break;
            case "7037": // Temp controller
                cmdByte = (byte) 0x70;
                text = new byte[]{packNo, 0x37, 0x01, 0x00,
                        (byte) clampToByte(params.getInteger("lowTemp", 5)),
                        (byte) clampToByte(params.getInteger("highTemp", 10)),
                        0x05, 0x00, 0x00, 0x01, 0x0A, 0x00};
                break;
            case "27": // Report money
                cmdByte = (byte) 0x27;
                int mode = clampToByte(params.getInteger("mode", 8));
                String amountHex = params.getString("amount", "00000000");
                byte[] amount = hexStringToByteArray(amountHex);
                text = new byte[]{packNo, (byte) mode, amount[0], amount[1], amount[2], amount[3]};
                break;
            case "28": // Enable bill acceptor
                cmdByte = (byte) 0x28;
                int mode4 = clampToByte(params.getInteger("mode", 0));
                byte value4 = (byte) Integer.parseInt(params.getString("value", "ffff"), 16);
                text = new byte[]{packNo, (byte) mode4, value4};
                break;
            default:
                text = new byte[0];
                Log.w(TAG, "Unsupported command: " + command + ", params: " + params.toString());
        }

        byte length = (byte) text.length;
        byte[] data = new byte[stx.length + 2 + text.length + 1];
        System.arraycopy(stx, 0, data, 0, stx.length);
        data[2] = cmdByte;
        data[3] = length;
        System.arraycopy(text, 0, data, 4, text.length);
        data[data.length - 1] = calculateXOR(data, data.length - 1);

        Log.d(TAG, "Built packet: " + bytesToHex(data, data.length));
        return data;
    }

    private byte getNextPackNo() {
        synchronized (this) {
            packNoCounter = (byte) ((packNoCounter + 1) % 256);
            return packNoCounter == 0 ? (byte) 1 : packNoCounter;
        }
    }

    private int clampToByte(Integer value) {
        if (value == null) return 0;
        return Math.min(Math.max(value, 0), 255);
    }

    @PluginMethod
    public void startReadingVMC(PluginCall call) {
        Log.d(TAG, "startReadingVMC invoked: " + call.getData().toString());
        if (serialPort == null) {
            call.reject("No serial connection open");
            return;
        }

        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "VMC reading started");
        notifyListeners("readingStarted", ret);
        call.resolve(ret);

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream packetBuffer = new ByteArrayOutputStream();

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
                                packetBuffer.write(buffer, 0, len);
                                byte[] accumulated = packetBuffer.toByteArray();
                                int start = 0;

                                while (start <= accumulated.length - 5) {
                                    if ((accumulated[start] & 0xFF) == 0xFA && (accumulated[start + 1] & 0xFF) == 0xFB) {
                                        int packetLength = (accumulated[start + 3] & 0xFF) + 5;
                                        if (start + packetLength > accumulated.length) break;

                                        byte[] packet = new byte[packetLength];
                                        System.arraycopy(accumulated, start, packet, 0, packetLength);
                                        String packetHex = bytesToHex(packet, packetLength);

                                        byte calculatedXor = calculateXOR(packet, packetLength - 1);
                                        if (calculatedXor != packet[packetLength - 1]) {
                                            Log.w(TAG, "Invalid checksum: " + packetHex);
                                            start++;
                                            continue;
                                        }

                                        if (packetHex.equals("fafb410040")) { // POLL
                                            synchronized (commandQueue) {
                                                if (!commandQueue.isEmpty()) {
                                                    byte[] response = commandQueue.poll();
                                                    assert response != null;
                                                    Log.d(TAG, "POLL received, sending command: " + bytesToHex(response, response.length));
                                                    serialPort.getOutputStream().write(response);
                                                    serialPort.getOutputStream().flush();
                                                    notifyListeners("serialWriteSuccess", new JSObject().put("data", bytesToHex(response, response.length)));
                                                } else {
                                                    byte[] ack = hexStringToByteArray("fafb420043");
                                                    Log.d(TAG, "POLL received, sending ACK: fafb420043");
                                                    serialPort.getOutputStream().write(ack);
                                                    serialPort.getOutputStream().flush();
                                                    notifyListeners("serialWriteSuccess", new JSObject().put("data", "fafb420043"));
                                                }
                                            }
                                        } else if (packetHex.equals("fafb420043") || packetHex.equals("fafb420143")) { // ACK
                                            synchronized (commandQueue) {
                                                if (!commandQueue.isEmpty()) {
                                                    byte[] ack = hexStringToByteArray("fafb420043");
                                                    Log.d(TAG, "ACK received, dequeued command: " + bytesToHex(ack, ack.length));
                                                    JSObject ackEvent = new JSObject();
                                                    ackEvent.put("data", bytesToHex(ack, ack.length));
                                                    notifyListeners("commandAcknowledged", ackEvent);
                                                }
                                            }
                                        } else { // Responses all data with ack
                                            Log.d(TAG, "Response received: " + packetHex);
                                            JSObject dataEvent = new JSObject();
                                            dataEvent.put("data", packetHex);
                                            notifyListeners("dataReceived", dataEvent);

                                            byte[] ack = hexStringToByteArray("fafb420043");
                                            Log.d(TAG, "Sending ACK: fafb420043");
                                            serialPort.getOutputStream().write(ack);
                                            serialPort.getOutputStream().flush();
                                        }

                                        start += packetLength;
                                    } else {
                                        start++;
                                    }
                                }

                                int remaining = accumulated.length - start;
                                if (remaining > 0) {
                                    byte[] remainder = new byte[remaining];
                                    System.arraycopy(accumulated, start, remainder, 0, remaining);
                                    packetBuffer.reset();
                                    packetBuffer.write(remainder);
                                } else {
                                    packetBuffer.reset();
                                }
                            }
                        }
                        Thread.sleep(10);
                    } catch (Exception e) {
                        if (isReading) Log.e(TAG, "VMC read error: " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    @PluginMethod
    public void startReading(PluginCall call) {
        if (serialPort == null) {
            call.reject("No serial connection open");
            return;
        }

        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "Reading started");
        notifyListeners("readingStarted", ret);
        call.resolve(ret);

        new Thread(() -> {
            byte[] buffer = new byte[1024];
            String lastSentData = null;
            long lastSentTime = 0;
            long debounceInterval = 100;

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
                                long currentTime = System.currentTimeMillis();

                                if (!receivedData.equals(lastSentData) && (currentTime - lastSentTime >= debounceInterval)) {
                                    JSObject dataEvent = new JSObject();
                                    dataEvent.put("data", receivedData);
                                    notifyListeners("dataReceived", dataEvent);
                                    lastSentData = receivedData;
                                    lastSentTime = currentTime;
                                }
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
            if (serialPort != null && (portName == null || portName.equals(serialPort.getDevicePath()))) {
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

    private byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private byte calculateXOR(byte[] data, int length) {
        byte xor = 0;
        for (int i = 0; i < length; i++) {
            xor ^= data[i];
        }
        return xor;
    }


    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    //ADH814
    @PluginMethod
    public void querySwap(PluginCall call) {
        int address = call.getInt("address", 1);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        byte[] request = createADH814Request(address, 0x34, new byte[0]);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void setSwap(PluginCall call) {
        int address = call.getInt("address", 1);
        int swapEnabled = call.getInt("swapEnabled", 1); // 0x01 for enabled, 0x00 for disabled
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        if (swapEnabled < 0 || swapEnabled > 1) {
            call.reject("swapEnabled must be 0x00 or 0x01");
            return;
        }
        byte[] data = new byte[]{(byte) swapEnabled};
        byte[] request = createADH814Request(address, 0x35, data);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void switchToTwoWireMode(PluginCall call) {
        int address = call.getInt("address", 1);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        byte[] data = new byte[]{0x10, 0x00}; // As per original TypeScript code
        byte[] request = createADH814Request(address, 0x21, data);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void startMotor(PluginCall call) {
        int address = call.getInt("address", 1);
        int motorNumber = call.getInt("motorNumber", 0);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        if (motorNumber < 0 || motorNumber > 0xFE) {
            call.reject("Motor number must be 0x00-0xFE");
            return;
        }
        byte[] data = new byte[]{(byte) motorNumber};
        byte[] request = createADH814Request(address, 0xA5, data);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void startMotorCombined(PluginCall call) {
        int address = call.getInt("address", 1);
        int motorNumber1 = call.getInt("motorNumber1", 0);
        int motorNumber2 = call.getInt("motorNumber2", 0);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        if (motorNumber1 < 0 || motorNumber1 > 0xFE || motorNumber2 < 0 || motorNumber2 > 0xFE) {
            call.reject("Motor numbers must be 0x00-0xFE");
            return;
        }
        byte[] data = new byte[]{(byte) motorNumber1, (byte) motorNumber2};
        byte[] request = createADH814Request(address, 0xB5, data);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void requestID(PluginCall call) {
        int address = call.getInt("address", 1);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        byte[] request = createADH814Request(address, 0xA1, new byte[0]);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void scanDoorFeedback(PluginCall call) {
        int address = call.getInt("address", 1);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        byte[] request = createADH814Request(address, 0xA2, new byte[0]);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void pollStatus(PluginCall call) {
        int address = call.getInt("address", 1);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        byte[] request = createADH814Request(address, 0xA3, new byte[0]);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void setTemperature(PluginCall call) {
        int address = call.getInt("address", 1);
        int mode = call.getInt("mode", 0);
        int tempValue = call.getInt("tempValue", 0);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        if (mode < 0 || mode > 2) {
            call.reject("Mode must be 0x00-0x02");
            return;
        }
        if (tempValue < -127 || tempValue > 127) {
            call.reject("Temp value must be -127 to 127");
            return;
        }
        byte[] data = new byte[]{(byte) mode, (byte) ((tempValue >> 8) & 0xFF), (byte) (tempValue & 0xFF)};
        byte[] request = createADH814Request(address, 0xA4, data);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void acknowledgeResult(PluginCall call) {
        int address = call.getInt("address", 1);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        byte[] request = createADH814Request(address, 0xA6, new byte[0]);
        sendADH814Request(request, call);
    }

    @PluginMethod
    public void startPolling(PluginCall call) {
        int address = call.getInt("address", 1);
        int interval = call.getInt("interval", 300);
        if (address < 1 || address > 4) {
            call.reject("Address must be 0x01-0x04");
            return;
        }
        if (interval < 100) {
            call.reject("Polling interval must be at least 100ms");
            return;
        }

        if (isPolling) {
            stopPolling(null);
        }

        isPolling = true;
        pollingThread = new Thread(() -> {
            while (isPolling && (serialPort != null || usbSerialPort != null)) {
                try {
                    byte[] request = createADH814Request(address, 0xA3, new byte[0]);
                    sendADH814Request(request, null); // Send without PluginCall to emit via adh814Response
                    Thread.sleep(interval);
                } catch (Exception e) {
                    Log.e(TAG, "Polling error: " + e.getMessage());
                }
            }
        });
        pollingThread.start();

        JSObject ret = new JSObject();
        ret.put("message", "Polling started with interval " + interval + "ms");
        notifyListeners("readingStarted", ret);
        call.resolve(ret);
    }

    @PluginMethod
    public void stopPolling(PluginCall call) {
        isPolling = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
            pollingThread = null;
        }
        JSObject ret = new JSObject();
        ret.put("message", "Polling stopped");
        notifyListeners("readingStopped", ret);
        if (call != null) call.resolve(ret);
    }

    private void sendADH814Request(byte[] request, PluginCall call) {
        String data = bytesToHex(request, request.length);
        int command = request[1] & 0xFF;
        synchronized (this) {
            if (call != null) {
                adh814ResponseListeners.put(command, call);
                new Thread(() -> {
                    try {
                        Thread.sleep(RESPONSE_TIMEOUT_MS);
                        if (adh814ResponseListeners.remove(command, call)) {
                            call.reject("Response timeout for command 0x" + Integer.toHexString(command));
                        }
                    } catch (InterruptedException ignored) {}
                }).start();
            }

            if (serialPort != null) {
                try {
                    serialPort.getOutputStream().write(request);
                    serialPort.getOutputStream().flush();
                    Log.d(TAG, "ADH814 data written to serial: " + data);
                    if (call != null) {
                        JSObject ret = new JSObject();
                        ret.put("message", "Data written successfully to serial");
                        ret.put("data", data);
                        notifyListeners("serialWriteSuccess", ret);
                    }
                } catch (IOException e) {
                    if (call != null) {
                        adh814ResponseListeners.remove(command);
                        call.reject("Failed to write to serial: " + e.getMessage());
                    }
                }
            } else if (usbSerialPort != null) {
                try {
                    usbSerialPort.write(request, 5000);
                    Log.d(TAG, "ADH814 data written to USB serial: " + data);
                    if (call != null) {
                        JSObject ret = new JSObject();
                        ret.put("message", "Data written successfully to USB serial");
                        ret.put("data", data);
                        notifyListeners("usbWriteSuccess", ret);
                    }
                } catch (Exception e) {
                    if (call != null) {
                        adh814ResponseListeners.remove(command);
                        call.reject("Failed to write to USB serial: " + e.getMessage());
                    }
                }
            } else {
                if (call != null) {
                    adh814ResponseListeners.remove(command);
                    call.reject("No serial connection open");
                }
            }
        }
    }

    private byte[] createADH814Request(int address, int command, byte[] data) {
        if (address < 0x01 || address > 0x04) {
            throw new IllegalArgumentException("Address must be 0x01-0x04");
        }
        byte[] payload = new byte[2 + data.length];
        payload[0] = (byte) address;
        payload[1] = (byte) command;
        System.arraycopy(data, 0, payload, 2, data.length);

        int crc = calculateCRC(payload);
        byte[] request = new byte[payload.length + 2];
        System.arraycopy(payload, 0, request, 0, payload.length);
        request[payload.length] = (byte) (crc & 0xFF); // Low byte
        request[payload.length + 1] = (byte) ((crc >> 8) & 0xFF); // High byte
        return request;
    }

    private int calculateCRC(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            int index = (crc >> 8) ^ (b & 0xFF);
            crc = ((crc ^ CRC_TABLE[index]) & 0x00FF) | ((CRC_TABLE[index] >> 8) << 8);
        }
        return (crc >> 8) | (crc << 8);
    }

    private void processADH814Response(byte[] buffer) {
        try {
            if (buffer.length < 4) {
                Log.w(TAG, "Invalid ADH814 response length: " + buffer.length);
                return;
            }
            int address = buffer[0] & 0xFF;
            int command = buffer[1] & 0xFF;
            byte[] data = Arrays.copyOfRange(buffer, 2, buffer.length - 2);
            int receivedCRC = ((buffer[buffer.length - 1] & 0xFF) << 8) | (buffer[buffer.length - 2] & 0xFF);

            byte[] payload = Arrays.copyOfRange(buffer, 0, buffer.length - 2);
            int calculatedCRC = calculateCRC(payload);
            if (receivedCRC != calculatedCRC) {
                Log.w(TAG, "ADH814 CRC validation failed: received 0x" + Integer.toHexString(receivedCRC) +
                        ", calculated 0x" + Integer.toHexString(calculatedCRC));
                return;
            }

            if (!Arrays.asList(0xA1, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xB5, 0x34, 0x35, 0x21).contains(command)) {
                Log.w(TAG, "Invalid ADH814 command: 0x" + Integer.toHexString(command));
                return;
            }

            // Validate address: 0xA1 expects 0x01-0x04, others expect 0x00
            if (command == 0xA1 && (address < 0x01 || address > 0x04)) {
                Log.w(TAG, "Invalid address for ID command 0xA1: expected 0x01-0x04, got 0x" + Integer.toHexString(address));
                return;
            } else if (command != 0xA1 && address != 0x00) {
                Log.w(TAG, "Invalid address for command 0x" + Integer.toHexString(command) +
                        ": expected 0x00, got 0x" + Integer.toHexString(address));
                return;
            }

            JSObject response = new JSObject();
            response.put("address", address);
            response.put("command", command);
            response.put("data", bytesToHex(data, data.length));

            switch (command) {
                case 0xA1: // ID
                    if (data.length >= 16) {
                        StringBuilder firmware = new StringBuilder();
                        for (int i = 0; i < 16; i++) {
                            firmware.append((char) (data[i] & 0xFF));
                        }
                        response.put("firmware", firmware.toString().trim());
                    }
                    break;
                case 0xA2: // SCAN
                    if (data.length >= 18) {
                        JSObject doorFeedback = new JSObject();
                        for (int i = 0; i < 18; i++) {
                            doorFeedback.put("byte" + i, data[i] & 0xFF);
                        }
                        response.put("doorFeedback", doorFeedback);
                    }
                    break;
                case 0xA3: // POLL
                    if (data.length >= 9) {
                        int status = data[0] & 0xFF;
                        int executionResult = data[2] & 0xFF;
                        response.put("status", status);
                        response.put("motorNumber", data[1] & 0xFF);
                        response.put("executionResult", executionResult);
                        response.put("dropSuccess", (executionResult & 0x04) == 0);
                        response.put("faultCode", executionResult & 0x03);
                        response.put("maxCurrent", (data[3] << 8) | (data[4] & 0xFF));
                        response.put("avgCurrent", (data[5] << 8) | (data[6] & 0xFF));
                        response.put("runTime", data[7] & 0xFF);
                        response.put("temperature", data[8] > 127 ? (data[8] - 256) : data[8]);
                        if (data[8] == -40) {
                            response.put("message", "Temperature sensor disconnected");
                        } else if (data[8] == 120) {
                            response.put("message", "Temperature sensor shorted");
                        }
                    }
                    break;
                case 0xA4: // TEMP
                    if (data.length >= 3) {
                        response.put("mode", data[0] & 0xFF);
                        response.put("tempValue", (data[1] << 8) | (data[2] & 0xFF));
                    }
                    break;
                case 0xA5: // RUN (shippingcontrol)
                case 0xB5: // RUN2 (shippingcontrol combined)
                    if (data.length >= 1) {
                        int executionStatus = data[0] & 0xFF;
                        response.put("executionStatus", executionStatus);
                        if (executionStatus != 0) {
                            response.put("error", true);
                            response.put("message", getExecutionErrorMessage(executionStatus));
                        }
                    }
                    break;
                case 0xA6: // ACK
                    response.put("message", "Result acknowledged");
                    break;
                case 0x34: // querySwap
                    if (data.length >= 1) {
                        response.put("swapEnabled", data[0] & 0xFF); // 0x01 enabled, 0x00 disabled
                    }
                    break;
                case 0x35: // setSwap
                    if (data.length >= 1) {
                        response.put("swapEnabled", data[0] & 0xFF); // Confirm setting
                    }
                    break;
                case 0x21: // switchToTwoWireMode
                    if (data.length >= 2) {
                        response.put("mode", data[0] & 0xFF);
                        response.put("status", data[1] & 0xFF);
                    }
                    break;
            }

            PluginCall call = adh814ResponseListeners.remove(command);
            if (call != null) {
                call.resolve(response);
            }
            notifyListeners("adh814Response", response);
        } catch (Exception e) {
            Log.e(TAG, "Error processing ADH814 response: " + e.getMessage());
            JSObject errorResponse = new JSObject();
            errorResponse.put("error", true);
            errorResponse.put("message", e.getMessage());
            notifyListeners("adh814Response", errorResponse);
        }
    }

    private String getExecutionErrorMessage(int status) {
        switch (status) {
            case 1: return "Invalid motor index";
            case 2: return "Another motor is running";
            case 3: return "Previous motor result not cleared";
            default: return "Unknown error";
        }
    }

    //ADH814
}