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
import java.util.concurrent.Semaphore;

import android.serialport.SerialPort; // Updated version


import androidx.annotation.RequiresApi;

import org.json.JSONException;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


@CapacitorPlugin(name = "SerialCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private static final String TAG = "SerialConnCap";



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
                    usbSerialPort.write(bytes, 2000);
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
                        Thread.sleep(50);
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
                            Thread.sleep(50);
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
        if (bytes == null || length <= 0) {
            Log.w(TAG, "Invalid bytesToHex input: bytes=" + (bytes == null ? "null" : "empty") + ", length=" + length);
            return "";
        }
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
























    // ADH814

    private byte[] buildADH814Packet(String command, JSObject params) throws JSONException, InterruptedException {
        int address = clampToByte(params.getInteger("address", 1));
        if (address < 0x01 || address > 0x04) {
            throw new IllegalArgumentException("Address must be 0x01-0x04");
        }
        byte[] data;
        byte cmdByte;

        switch (command.toUpperCase()) {
            case "A1": // ID
                cmdByte = (byte) 0xA1;
                data = new byte[]{};
                break;
            case "A3": // POLL
                cmdByte = (byte) 0xA3;
                data = new byte[]{};
                break;
            case "A4": // TEMP
                cmdByte = (byte) 0xA4;
                int mode = clampToByte(params.getInteger("mode", 0x01));
                if (mode < 0x00 || mode > 0x02) {
                    throw new IllegalArgumentException("Mode must be 0x00-0x02");
                }
                int tempValue = params.getInteger("tempValue", 7);
                if (tempValue < -127 || tempValue > 127) {
                    throw new IllegalArgumentException("Temperature value must be -127 to 127");
                }
                data = new byte[]{(byte) mode, (byte) ((tempValue >> 8) & 0xFF), (byte) (tempValue & 0xFF)};
                break;
            case "A5": // RUN
                cmdByte = (byte) 0xA5;
                int motorNumber = clampToByte(params.getInteger("motorNumber", 0));
                if (motorNumber < 0x00 || motorNumber > 0xFE) {
                    throw new IllegalArgumentException("Motor number must be 0x00-0xFE");
                }
                data = new byte[]{(byte) motorNumber};
                break;
            case "A6": // ACK
                cmdByte = (byte) 0xA6;
                data = new byte[]{};
                break;
            case "B5": // RUN2
                cmdByte = (byte) 0xB5;
                int motorNumber1 = clampToByte(params.getInteger("motorNumber1", 0));
                int motorNumber2 = clampToByte(params.getInteger("motorNumber2", motorNumber1));
                if (motorNumber1 < 0x00 || motorNumber1 > 0xFE || motorNumber2 < 0x00 || motorNumber2 > 0xFE) {
                    throw new IllegalArgumentException("Motor numbers must be 0x00-0xFE");
                }
                data = new byte[]{(byte) motorNumber1, (byte) motorNumber2};
                break;
            case "21": // switchToTwoWireMode
                cmdByte = (byte) 0x21;
                data = new byte[]{(byte) 0x10, (byte) 0x00};
                break;
            case "35": // setSwap
                cmdByte = (byte) 0x35;
                data = new byte[]{(byte) 0x01};
                break;
            default:
                throw new IllegalArgumentException("Unsupported command: " + command);
        }

        byte[] payload = new byte[2 + data.length];
        payload[0] = (byte) address;
        payload[1] = cmdByte;
        System.arraycopy(data, 0, payload, 2, data.length);
        int crc = calculateCRCRequest(payload);
        byte[] packet = new byte[payload.length + 2];
        System.arraycopy(payload, 0, packet, 0, payload.length);
        packet[payload.length] = (byte) (crc & 0xFF); // Low byte
        packet[payload.length + 1] = (byte) ((crc >> 8) & 0xFF); // High byte
        return packet;
    }

    private int getExpectedResponseLength(String command) {
        switch (command.toUpperCase()) {
            case "A1": return 18; // ID
            case "A3": return 11; // POLL
            case "A4": return 5;  // TEMP
            case "A5": return 5;  // RUN
            case "A6": return 4;  // ACK
            case "B5": return 5;  // RUN2
            case "21": return 5;  // switchToTwoWireMode
            case "35": return 5;  // setSwap
            default: return 4;    // Minimum length
        }
    }

    private JSObject parseADH814Response(byte[] buffer, int expectedLength) {
        JSObject response = new JSObject();
        if (buffer.length < 4) {
            Log.w(TAG, "Response too short: " + buffer.length + " bytes, expected at least 4");
            response.put("error", "Response too short: " + buffer.length + " bytes");
            response.put("data", bytesToHex(buffer, buffer.length));
            return response;
        }

        int receivedAddress = buffer[0] & 0xFF;
        int receivedCommand = buffer[1] & 0xFF;
        byte[] data = Arrays.copyOfRange(buffer, 2, buffer.length - 2);
        int receivedCRC = ((buffer[buffer.length - 2] & 0xFF) << 8) | (buffer[buffer.length - 1] & 0xFF);
        byte[] crcData = Arrays.copyOfRange(buffer, 0, buffer.length - 2);
        int calculatedCRC = calculateCRCResponse(crcData);

        if (receivedCRC != calculatedCRC) {
            Log.w(TAG, "CRC mismatch: received 0x" + String.format("%04x", receivedCRC) +
                    ", calculated 0x" + String.format("%04x", calculatedCRC));
            response.put("warning", response.has("warning") ?
                    response.getString("warning") + "; CRC mismatch" : "CRC mismatch");
        }

        if (receivedAddress != 0x00 && (receivedCommand != 0xA1 || receivedAddress != 0x01)) {
            Log.w(TAG, "Unexpected address: got 0x" + String.format("%02x", receivedAddress) +
                    ", expected 0x00 or 0x01 for A1");
            response.put("warning", response.has("warning") ?
                    response.getString("warning") + "; Unexpected address" : "Unexpected address");
        }

        response.put("address", receivedAddress);
        response.put("command", String.format("%02X", receivedCommand));
        response.put("data", bytesToHex(data, data.length));

        if (receivedCommand == 0xA1) { // ID
            String idString = new String(data, 0, Math.min(data.length, 16)).trim();
            response.put("idString", idString);
        } else if (receivedCommand == 0xA3) { // POLL
            JSObject statusDetails = new JSObject();
            statusDetails.put("status", data.length > 0 ? (data[0] & 0xFF) : 0);
            statusDetails.put("motorNumber", data.length > 1 ? (data[1] & 0xFF) : 0);
            statusDetails.put("faultCode", data.length > 2 ? (data[2] & 0x03) : 0);
            statusDetails.put("dropSuccess", data.length > 2 ? ((data[2] & 0x04) == 0) : true);
            statusDetails.put("maxCurrent", data.length > 4 ? ((data[3] & 0xFF) << 8) | (data[4] & 0xFF) : 0);
            statusDetails.put("avgCurrent", data.length > 6 ? ((data[5] & 0xFF) << 8) | (data[6] & 0xFF) : 0);
            statusDetails.put("runTime", data.length > 7 ? (data[7] & 0xFF) : 0);
            int temperature = data.length > 8 ? data[8] : 0;
            statusDetails.put("temperature", temperature);
            if (temperature == -40 || temperature == 120) {
                Log.w(TAG, "Temperature sensor issue: " + (temperature == -40 ? "Disconnected" : "Shorted"));
                response.put("warning", response.has("warning") ?
                        response.getString("warning") + "; Temperature sensor issue" : "Temperature sensor issue");
            }
            response.put("statusDetails", statusDetails);
        } else if (receivedCommand == 0xA5 || receivedCommand == 0xB5 || receivedCommand == 0x21 || receivedCommand == 0x35) { // RUN, RUN2, switchToTwoWireMode, setSwap
            response.put("executionStatus", data.length > 0 ? (data[0] & 0xFF) : 0);
            if (receivedCommand == 0x35) {
                response.put("swapStatus", (data.length > 0 && data[0] == 0x01) ? "Swap set successfully" : "Swap set failed");
            }
        }

        response.put("crc", String.format("%04X", receivedCRC));
        return response;
    }

    private void startProcessingQueueADH814() {

        isProcessingQueue = true;
        new Thread(() -> {
            while (isProcessingQueue && isReading) {
                synchronized (this) {
                    if (serialPort == null) {
                        Log.w(TAG, "Serial port closed, stopping queue processing");
                        isProcessingQueue = false;
                        break;
                    }
                    try {
                        synchronized (commandQueue) {
                            if (!commandQueue.isEmpty()) {
                                byte[] command = commandQueue.peek();
                                String commandHex = bytesToHex(command, command.length);
                                Log.d(TAG, "Sending queued command: " + commandHex);
                                serialPort.getOutputStream().write(command);
                                serialPort.getOutputStream().flush();
                                notifyListeners("serialWriteSuccess", new JSObject().put("data", commandHex));
                                Thread.sleep(1000); // Wait 1s for response
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Queue processing error: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(TAG, "Unexpected error in queue processing: " + e.getMessage());
                    }
                }
                try {
                    Thread.sleep(50); // 50ms loop interval
                } catch (InterruptedException e) {
                    Log.w(TAG, "Queue processing interrupted: " + e.getMessage());
                    isProcessingQueue = false;
                    break;
                }
            }
            isProcessingQueue = false;
        }).start();
    }

    @PluginMethod
    public void writeADH814(PluginCall call) {
        Log.d(TAG, "write invoked: " + call.getData().toString());
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

            byte[] packet;
            int retries = params.getInteger("retries", 1); // Default to 1 for non-setSwap commands
            if (command.equalsIgnoreCase("35")) { // setSwap with retries
                for (int attempt = 1; attempt <= retries; attempt++) {
                    try {
                        packet = buildADH814Packet(command, params);
                        synchronized (commandQueue) {
                            commandQueue.add(packet);
                            expectedResponseLengths.put(bytesToHex(packet, packet.length), getExpectedResponseLength(command));
                            Log.d(TAG, "Queued ADH814 command (attempt " + attempt + "): " + bytesToHex(packet, packet.length));
                        }
                        JSObject ret = new JSObject();
                        ret.put("message", "ADH814 command queued (attempt " + attempt + ")");
                        ret.put("data", bytesToHex(packet, packet.length));
                        notifyListeners("commandQueued", ret);
                        if (!isProcessingQueue) {
                            startProcessingQueueADH814();
                        }
                        call.resolve(ret);
                        return;
                    } catch (Exception e) {
                        Log.w(TAG, "setSwap attempt " + attempt + " failed: " + e.getMessage());
                        if (attempt == retries) {
                            call.reject("Failed to queue setSwap after " + retries + " attempts: " + e.getMessage());
                            return;
                        }
                        Thread.sleep(500);
                    }
                }
            } else {
                packet = buildADH814Packet(command, params);
                synchronized (commandQueue) {
                    commandQueue.add(packet);
                    expectedResponseLengths.put(bytesToHex(packet, packet.length), getExpectedResponseLength(command));
                    Log.d(TAG, "Queued ADH814 command: " + bytesToHex(packet, packet.length));
                }
                JSObject ret = new JSObject();
                ret.put("message", "ADH814 command queued");
                ret.put("data", bytesToHex(packet, packet.length));
                notifyListeners("commandQueued", ret);
                if (!isProcessingQueue) {
                    startProcessingQueueADH814();
                }

                call.resolve(ret);
            }
        } catch (Exception e) {
            call.reject("Failed to parse data or build packet: " + e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @PluginMethod
    public void startReadingADH814(PluginCall call) {
        Log.d(TAG, "startReading invoked: " + call.getData().toString());
        if (serialPort == null) {
            call.reject("No serial connection open");
            return;
        }

        try {
            serialPort.getInputStream().skip(serialPort.getInputStream().available()); // Clear input buffer
        } catch (IOException e) {
            Log.w(TAG, "Failed to clear input buffer: " + e.getMessage());
        }

        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "ADH814 reading started");
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
//            Log.d(TAG, "Bytes available: " + available);
                        if (available > 0) {
                            int len = serialPort.getInputStream().read(buffer, 0, Math.min(available, buffer.length));
                            if (len > 0) {
                                Log.d(TAG, "Read " + len + " bytes: " + bytesToHex(buffer, len));
                                packetBuffer.write(buffer, 0, len);
                                byte[] accumulated = packetBuffer.toByteArray();
                                int start = 0;

                                while (start < accumulated.length) {
                                    if (accumulated.length - start < 4) {
                                        Log.d(TAG, "Partial packet, waiting for more data: " + bytesToHex(accumulated, accumulated.length));
                                        break; // Minimum frame length
                                    }

                                    // Find valid start byte (0x00 or 0x01 for A1)
                                    int validStart = start;
                                    while (validStart < accumulated.length &&
                                            accumulated[validStart] != 0x00 &&
                                            !(accumulated[validStart] == 0x01 && validStart + 1 < accumulated.length && accumulated[validStart + 1] == (byte) 0xA1)) {
                                        Log.w(TAG, "Skipping invalid byte at position " + validStart + ": " + String.format("%02x", accumulated[validStart]));
                                        validStart++;
                                    }

                                    if (validStart >= accumulated.length) {
                                        Log.d(TAG, "No valid start byte found, discarding buffer: " + bytesToHex(accumulated, accumulated.length));
                                        packetBuffer.reset();
                                        break;
                                    }

                                    start = validStart;
                                    int expectedLength = commandQueue.isEmpty() ? 4 :
                                            expectedResponseLengths.getOrDefault(bytesToHex(commandQueue.peek(), commandQueue.peek().length), 4);

                                    if (start + expectedLength > accumulated.length) {
                                        Log.d(TAG, "Incomplete packet, need " + expectedLength + " bytes, have " + (accumulated.length - start));
                                        break;
                                    }

                                    byte[] packet = new byte[expectedLength];
                                    System.arraycopy(accumulated, start, packet, 0, expectedLength);
                                    String packetHex = bytesToHex(packet, packet.length);

                                    JSObject response = parseADH814Response(packet, expectedLength);
                                    Log.d(TAG, "Response received: " + packetHex);
                                    notifyListeners("dataReceived", response);

                                    synchronized (commandQueue) {
                                        if (!commandQueue.isEmpty()) {
                                            byte[] sentCommand = commandQueue.peek();
                                            int sentCommandCode = sentCommand[1] & 0xFF;
                                            int receivedCommandCode = packet[1] & 0xFF;
                                            Log.d(TAG, "sentCommandCode: " + sentCommandCode+" receivedCommandCode "+receivedCommandCode );

                                            if (sentCommandCode == receivedCommandCode) {
                                                commandQueue.poll();
                                                expectedResponseLengths.remove(bytesToHex(sentCommand, sentCommand.length));

                                                if (receivedCommandCode == 0xA3 && response.has("statusDetails") &&
                                                        response.getJSObject("statusDetails").getInteger("status") == 2
                                                        ||
                                                        receivedCommandCode!=0xA6
                                                ) {
                                                    byte[] ackPacket = buildADH814Packet("A6", new JSObject().put("address", sentCommand[0] & 0xFF));
                                                    commandQueue.add(ackPacket);
                                                    expectedResponseLengths.put(bytesToHex(ackPacket, ackPacket.length), 4);
                                                    Log.d(TAG, "Queued ACK command: " + bytesToHex(ackPacket, ackPacket.length));
                                                }
                                            }
                                        }
                                    }
                                    start += expectedLength;
                                }

                                int remaining = accumulated.length - start;
                                if (remaining > 0) {
                                    byte[] remainder = new byte[remaining];
                                    System.arraycopy(accumulated, start, remainder, 0, remaining);
                                    packetBuffer.reset();
                                    packetBuffer.write(remainder);
                                    Log.d(TAG, "Stored " + remaining + " remaining bytes: " + bytesToHex(remainder, remaining));
                                } else {
                                    packetBuffer.reset();
                                }
                            }
                        } else {
                            Thread.sleep(50); // 50ms sleep as per requirement
                        }
                    } catch (Exception e) {
                        if (isReading) Log.e(TAG, "ADH814 read error: " + e.getMessage());
                    }
                }
            }
        }).start();
    }
    // CRC calculations as provided
    public static int calculateCRCResponse(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return ((crc & 0xFF) << 8) | ((crc >> 8) & 0xFF);
    }

    public static int calculateCRCRequest(byte[] data) {
        int crc = 0xFFFF;
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc;
    }
    private final Map<String, Integer> expectedResponseLengths = new ConcurrentHashMap<>();
    private volatile boolean isProcessingQueue = false;
    //ADH814
}
