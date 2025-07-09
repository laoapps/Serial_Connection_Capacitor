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

import androidx.core.util.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

@CapacitorPlugin(name = "SerialCapacitor")
public class SerialConnectionCapacitorPlugin extends Plugin {
    private static final String TAG = "SerialConnCap";
    private UsbSerialPort usbSerialPort;
    private SerialPort serialPort;
    private volatile boolean isReading = false;
    private UsbManager usbManager;
    private BroadcastReceiver usbPermissionReceiver;
    private final Queue<byte[]> commandQueue = new LinkedList<>();
    private final Set<String> sentResponses = new HashSet<>();
    private byte packNoCounter = 0;
    private ESSP essp;
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

    // New ESSP Methods
    @PluginMethod
    public void openSerialEssp(PluginCall call) {
        Log.d(TAG, "openSerialEssp invoked: " + call.getData().toString());
        String portName = call.getString("portName");
        int sspId = call.getInt("sspId", 0x00);
        Boolean debug = call.getBoolean("debug", false);
        int timeout = call.getInt("timeout", 5000); // Increased default
        String fixedKey = call.getString("fixedKey", "0123456701234567");
        int dataBits = call.getInt("dataBits", 8);
        int stopBits = call.getInt("stopBits", 2); // Changed to 1
        String parity = call.getString("parity", "none");
        int bufferSize = call.getInt("bufferSize", 0);
        int flags = call.getInt("flags", 0);

        if (portName == null) {
            call.reject("Port name is required");
            return;
        }

        synchronized (this) {
            if (essp != null) {
                call.reject("ESSP connection already open");
                return;
            }
            if (serialPort != null || usbSerialPort != null) {
                call.reject("Another serial connection is already open; close it first");
                return;
            }

            try {
                essp = new ESSP(sspId, debug, timeout, fixedKey, dataBits, stopBits, parity, bufferSize, flags);
                essp.open(portName);

                JSObject ret = new JSObject();
                ret.put("message", "ESSP serial connection opened");
                ret.put("portName", portName);
                ret.put("sspId", sspId);
                ret.put("debug", debug);
                ret.put("timeout", timeout);
                ret.put("fixedKey", fixedKey);
                ret.put("dataBits", dataBits);
                ret.put("stopBits", stopBits);
                ret.put("parity", parity);
                ret.put("bufferSize", bufferSize);
                ret.put("flags", flags);
                notifyListeners("esspOpened", ret);
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("Permission denied: " + e.getMessage());
            } catch (IOException e) {
                essp = null;
                call.reject("Failed to open ESSP serial connection: " + e.getMessage());
            }
        }
    }

    @PluginMethod
    public void startReadingEssp(PluginCall call) {
        Log.d(TAG, "startReadingEssp invoked: " + call.getData().toString());
        if (essp == null) {
            call.reject("No ESSP connection open. Call openSerialEssp first");
            return;
        }

        isReading = true;
        JSObject ret = new JSObject();
        ret.put("message", "ESSP reading started");
        notifyListeners("readingStarted", ret);
        call.resolve(ret);
    }

    @PluginMethod
    public void writeEssp(PluginCall call) {
        Log.d(TAG, "writeEssp invoked: " + call.getData().toString());
        if (essp == null) {
            call.reject("No ESSP connection open. Call openSerialEssp first");
            return;
        }

        String data = call.getString("data");
        if (data == null) {
            call.reject("Data is required");
            return;
        }

        try {
            JSObject dataObj = new JSObject(data);
            String command = dataObj.getString("command");
            JSObject paramsObj = dataObj.getJSObject("params", new JSObject());

            if (command == null) {
                call.reject("Command is required");
                return;
            }

            Map<String, Object> params = new HashMap<>();
            if (command.equals("SET_CHANNEL_INHIBITS")) {
                assert paramsObj != null;
                JSONArray channelsArray = paramsObj.optJSONArray("channels");
                if (channelsArray != null) {
                    int[] channels = new int[channelsArray.length()];
                    for (int i = 0; i < channelsArray.length(); i++) {
                        channels[i] = channelsArray.optInt(i);
                    }
                    params.put("channels", channels);
                } else {
                    call.reject("Channels array is required for SET_CHANNEL_INHIBITS");
                    return;
                }
            }

            Map<String, Object> result = essp.command(command, params);
            JSObject ret = new JSObject();
            ret.put("message", "Command " + command + " executed");
            ret.put("status", result.get("status"));
            if (result.containsKey("info")) ret.put("info", result.get("info"));
            if (result.containsKey("serial_number")) ret.put("serial_number", result.get("serial_number"));
            if (result.containsKey("code")) ret.put("code", result.get("code"));
            notifyListeners("serialWriteSuccess", ret);
            call.resolve(ret);
        } catch (IOException e) {
            call.reject("Failed to execute ESSP command: " + e.getMessage());
        } catch (Exception e) {
            call.reject("Invalid data format: " + e.getMessage());
        }
    }

    // ESSP Implementation
    private class ESSP {
        private volatile boolean isPolling = false;
        private static final int DEFAULT_TIMEOUT = 5000;
        private static final byte STX = 0x7F;
        private static final byte STEX = 0x7E;
        private static final int AES_BLOCK_SIZE = 16;

        private final byte sspId;
        private final boolean debug;
        private final int timeout;
        private final String fixedKey;
        private final int dataBits;
        private final int stopBits;
        private final String parity;
        private final int bufferSize;
        private final int flags;
        private long encryptKey;
        private boolean isEncrypted = false;
        private int sequenceCount = 0;
        private boolean sequenceFlag = false;
        private final SecureRandom random = new SecureRandom();
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private volatile boolean running = false;

        // Command codes
        private static final byte CMD_SYNC = 0x11;
        private static final byte CMD_HOST_PROTOCOL_VERSION = 0x06;
        private static final byte CMD_SET_GENERATOR = 0x4A;
        private static final byte CMD_SET_MODULUS = 0x4B;
        private static final byte CMD_REQ_KEY_EXCHANGE = 0x4C;
        private static final byte CMD_ENABLE = 0x0A;
        private static final byte CMD_DISABLE = 0x09;
        private static final byte CMD_SETUP_REQUEST = 0x05;
        private static final byte CMD_SET_CHANNEL_INHIBITS = 0x02;
        private static final byte CMD_GET_SERIAL_NUMBER = 0x04;
        private static final byte CMD_RESET_COUNTERS = 0x47;
        private static final byte CMD_POLL = 0x07;
        private static final byte CMD_LAST_REJECT_CODE = 0x17;

        public ESSP(int id, boolean debug, int timeout, String fixedKey, int dataBits, int stopBits, String parity, int bufferSize, int flags) {
            this.sspId = (byte) id;
            this.debug = debug;
            this.timeout = timeout > 0 ? timeout : DEFAULT_TIMEOUT;
            this.fixedKey = fixedKey != null ? fixedKey : "0123456701234567";
            this.dataBits = dataBits;
            this.stopBits = stopBits;
            this.parity = parity != null ? parity : "none";
            this.bufferSize = bufferSize;
            this.flags = flags;
        }

        public void open(String portPath) throws IOException {
            if (serialPort == null) {
                serialPort = new SerialPort(portPath, 9600, flags, dataBits, stopBits, parity, bufferSize);
            }
            running = true;
            startPolling();
            initialize();
        }

        public void close() throws IOException {
            running = false;
            isPolling = false;
            executor.shutdown();
            if (serialPort != null) {
                serialPort.close();
                serialPort = null;
            }
        }

        public Map<String, Object> command(String commandName, Map<String, Object> params) throws IOException {
            byte[] command = buildCommand(commandName, params);
            byte[] response = sendCommand(command);
            return parseResponse(commandName, response);
        }

        private void initialize() {
            executor.execute(() -> {
                try {
                    command("SYNC", null);
                    Map<String, Object> versionParams = new HashMap<>();
                    versionParams.put("version", 6);
                    command("HOST_PROTOCOL_VERSION", versionParams);
                    initEncryption();
                    Map<String, Object> serialResult = command("GET_SERIAL_NUMBER", null);
                    if (debug) Log.d(TAG, "Serial Number: " + serialResult.get("serial_number"));
                    Map<String, Object> resetResult = command("RESET_COUNTERS", null);
                    if (debug && "OK".equals(resetResult.get("status"))) Log.d(TAG, "Counters reset");
                    Map<String, Object> enableResult = command("ENABLE", null);
                    if (debug && "OK".equals(enableResult.get("status"))) Log.d(TAG, "Enabled");
                    Map<String, Object> setupResult = command("SETUP_REQUEST", null);
                    if (debug && "OK".equals(setupResult.get("status"))) Log.d(TAG, "Setup: " + setupResult.get("info"));
                    Map<String, Object> channelParams = new HashMap<>();
                    channelParams.put("channels", new int[]{1, 1, 1, 1, 1, 1, 1});
                    Map<String, Object> channelResult = command("SET_CHANNEL_INHIBITS", channelParams);
                    if (debug && "OK".equals(channelResult.get("status"))) Log.d(TAG, "Channels inhibited");
                } catch (Exception e) {
                    Log.e(TAG, "ESSP Initialization failed", e);
                }
            });
        }

        private void initEncryption() throws IOException {
            long generator = new java.math.BigInteger(64, random).longValue();
            long modulus = new java.math.BigInteger(64, random).longValue();
            long hostInter = new java.math.BigInteger(64, random).longValue();

            Map<String, Object> genParams = new HashMap<>();
            genParams.put("value", generator);
            command("SET_GENERATOR", genParams);

            Map<String, Object> modParams = new HashMap<>();
            modParams.put("value", modulus);
            command("SET_MODULUS", modParams);

            Map<String, Object> keyParams = new HashMap<>();
            keyParams.put("value", hostInter);
            Map<String, Object> keyResult = command("REQ_KEY_EXCHANGE", keyParams);

            long slaveInterKey = (long) keyResult.get("slaveInterKey");
            encryptKey = modularPow(slaveInterKey, hostInter, modulus);
            isEncrypted = true;
            sequenceCount = 0;

            if (debug) Log.d(TAG, "Encryption initialized with key: " + Long.toHexString(encryptKey));
        }

        private byte[] buildCommand(String commandName, Map<String, Object> params) throws IOException {
            ByteBuffer buffer = ByteBuffer.allocate(255);
            buffer.put(STX);
            byte seqId = (byte) ((sequenceFlag ? 0x80 : 0x00) | sspId);
            buffer.put(seqId);

            switch (commandName) {
                case "SYNC":
                    buffer.put((byte) 1);
                    buffer.put(CMD_SYNC);
                    break;
                case "HOST_PROTOCOL_VERSION":
                    buffer.put((byte) 2);
                    buffer.put(CMD_HOST_PROTOCOL_VERSION);
                    buffer.put(((Integer) params.get("version")).byteValue());
                    break;
                case "SET_GENERATOR":
                case "SET_MODULUS":
                case "REQ_KEY_EXCHANGE":
                    buffer.put((byte) 9);
                    buffer.put(getCommandCode(commandName));
                    buffer.putLong((Long) params.get("value"));
                    break;
                case "ENABLE":
                    buffer.put((byte) 1);
                    buffer.put(CMD_ENABLE);
                    break;
                case "DISABLE":
                    buffer.put((byte) 1);
                    buffer.put(CMD_DISABLE);
                    break;
                case "SETUP_REQUEST":
                    buffer.put((byte) 1);
                    buffer.put(CMD_SETUP_REQUEST);
                    break;
                case "SET_CHANNEL_INHIBITS":
                    Object channelsObj = params.get("channels");
                    int[] channels;
                    if (channelsObj instanceof int[]) {
                        channels = (int[]) channelsObj;
                    } else if (channelsObj instanceof List<?>) {
                        List<?> list = (List<?>) channelsObj;
                        channels = new int[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            channels[i] = ((Number) list.get(i)).intValue();
                        }
                    } else {
                        throw new IllegalArgumentException("Channels must be an array or list");
                    }
                    buffer.put((byte) (channels.length + 1));
                    buffer.put(CMD_SET_CHANNEL_INHIBITS);
                    for (int channel : channels) buffer.put((byte) channel);
                    break;
                case "GET_SERIAL_NUMBER":
                    buffer.put((byte) 1);
                    buffer.put(CMD_GET_SERIAL_NUMBER);
                    break;
                case "RESET":
                case "RESET_COUNTERS":  // Alias for RESET
                    buffer.put((byte) 1);
                    buffer.put(CMD_RESET_COUNTERS);
                    break;
                case "POLL":
                    buffer.put((byte) 1);
                    buffer.put(CMD_POLL);
                    break;
                case "LAST_REJECT_CODE":
                    buffer.put((byte) 1);
                    buffer.put(CMD_LAST_REJECT_CODE);
                    break;
            }

            byte[] data = Arrays.copyOf(buffer.array(), buffer.position());
            byte[] crc = calculateCRC(data, 1, data.length - 1); // Start at SEQ/ID, exclude STX
            buffer.put(crc[0]); // LSB
            buffer.put(crc[1]); // MSB

            byte[] packet = Arrays.copyOf(buffer.array(), buffer.position());
            if (isEncrypted) packet = encryptPacket(packet);
            sequenceFlag = !sequenceFlag;
            return packet;
        }

        private byte[] encryptPacket(byte[] packet) throws IOException {
            try {
                ByteBuffer encryptedBuffer = ByteBuffer.allocate(255);
                encryptedBuffer.put(STX);
                encryptedBuffer.put((byte) (sequenceFlag ? 0x80 | sspId : sspId));

                ByteBuffer dataBuffer = ByteBuffer.allocate(255);
                dataBuffer.put(STEX);
                dataBuffer.put((byte) (packet.length - 3));
                dataBuffer.putInt(sequenceCount++);

                byte[] plainData = Arrays.copyOfRange(packet, 2, packet.length - 2);
                dataBuffer.put(plainData);

                int paddingLength = AES_BLOCK_SIZE - ((dataBuffer.position() + 2) % AES_BLOCK_SIZE);
                if (paddingLength == AES_BLOCK_SIZE) paddingLength = 0;
                byte[] padding = new byte[paddingLength];
                random.nextBytes(padding);
                dataBuffer.put(padding);

                byte[] dataToEncrypt = Arrays.copyOf(dataBuffer.array(), dataBuffer.position());
                byte[] crc = calculateCRC(dataToEncrypt, 1, dataToEncrypt.length - 1);
                dataBuffer.put(crc[0]); // LSB
                dataBuffer.put(crc[1]); // MSB

                byte[] encryptedData = encryptAES(Arrays.copyOf(dataBuffer.array(), dataBuffer.position()));
                encryptedBuffer.put((byte) encryptedData.length);
                encryptedBuffer.put(encryptedData);

                byte[] finalPacket = Arrays.copyOf(encryptedBuffer.array(), encryptedBuffer.position());
                crc = calculateCRC(finalPacket, 1, finalPacket.length - 1);
                encryptedBuffer.put(crc[0]); // LSB
                encryptedBuffer.put(crc[1]); // MSB

                return Arrays.copyOf(encryptedBuffer.array(), encryptedBuffer.position());
            } catch (Exception e) {
                throw new IOException("Encryption failed", e);
            }
        }

        private byte[] encryptAES(byte[] data) throws Exception {
            byte[] keyBytes = new byte[16];
            ByteBuffer.wrap(keyBytes).putLong(Long.parseLong(fixedKey, 16)).putLong(encryptKey);
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(data);
        }

        private byte[] sendCommand(byte[] command) throws IOException {
            synchronized (serialPort.getOutputStream()) {
                if (debug) Log.d(TAG, "Sending ESSP: " + bytesToHex(command));
                serialPort.getOutputStream().write(command);
                serialPort.getOutputStream().flush();

                byte[] response = new byte[255];
                long startTime = System.currentTimeMillis();
                int bytesRead = 0;
                int maxWaitTime = timeout;

                while (System.currentTimeMillis() - startTime < maxWaitTime && bytesRead < response.length) {
                    int available = serialPort.getInputStream().available();
                    if (available > 0) {
                        bytesRead += serialPort.getInputStream().read(response, bytesRead, Math.min(available, response.length - bytesRead));
                        if (response[0] == STX && bytesRead > 2 && bytesRead >= response[2] + 5) {
                            break;
                        }
                    } else {
                        try {
                            Thread.sleep(10); // Small delay to reduce CPU usage
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                if (bytesRead == 0) {
                    if (debug) Log.w(TAG, "No ESSP response received after " + maxWaitTime + "ms");
                    throw new IOException("No ESSP response received");
                }
                byte[] result = Arrays.copyOf(response, bytesRead);
                if (debug) Log.d(TAG, "Received ESSP: " + bytesToHex(result));
                return result;
            }
        }

        private Map<String, Object> parseResponse(String commandName, byte[] response) throws IOException {
            Map<String, Object> result = new HashMap<>();
            if (response[0] != STX) {
                result.put("status", "ERROR");
                result.put("error", "Invalid STX");
                return result;
            }

            byte[] data = isEncrypted ? decryptResponse(response) : Arrays.copyOfRange(response, 2, response.length - 2);
            byte[] calculatedCrc = calculateCRC(response, 1, response.length - 3);
            int receivedCrc = ((response[response.length - 1] & 0xFF) << 8) | (response[response.length - 2] & 0xFF);
            int calcCrc = ((calculatedCrc[1] & 0xFF) << 8) | (calculatedCrc[0] & 0xFF);
            if (calcCrc != receivedCrc) {
                result.put("status", "ERROR");
                result.put("error", "CRC mismatch: calculated " + Integer.toHexString(calcCrc) + ", received " + Integer.toHexString(receivedCrc));
                return result;
            }

            switch (commandName) {
                case "SYNC":
                case "ENABLE":
                case "DISABLE":
                case "RESET":
                case "RESET_COUNTERS":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    break;
                case "HOST_PROTOCOL_VERSION":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    break;
                case "SET_GENERATOR":
                case "SET_MODULUS":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    break;
                case "REQ_KEY_EXCHANGE":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    if (data[0] == 0xF0) result.put("slaveInterKey", ByteBuffer.wrap(data, 1, 8).getLong());
                    break;
                case "SETUP_REQUEST":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    if (data[0] == 0xF0) {
                        Map<String, Object> info = new HashMap<>();
                        info.put("firmware", new String(data, 1, 4));
                        info.put("channels", data[5] & 0xFF);
                        result.put("info", info);
                    }
                    break;
                case "SET_CHANNEL_INHIBITS":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    break;
                case "GET_SERIAL_NUMBER":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    if (data[0] == 0xF0) result.put("serial_number", ByteBuffer.wrap(data, 1, 4).getInt());
                    break;
                case "POLL":
                    parsePollResponse(data, result);
                    break;
                case "LAST_REJECT_CODE":
                    result.put("status", data[0] == 0xF0 ? "OK" : "FAIL");
                    if (data[0] == 0xF0) result.put("code", data[1] & 0xFF);
                    break;
            }
            return result;
        }

        private byte[] decryptResponse(byte[] response) throws IOException {
            try {
                byte[] encryptedData = Arrays.copyOfRange(response, 3, response.length - 2);
                byte[] keyBytes = new byte[16];
                ByteBuffer.wrap(keyBytes).putLong(Long.parseLong(fixedKey, 16)).putLong(encryptKey);
                SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");
                Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key);
                byte[] decrypted = cipher.doFinal(encryptedData);

                if (decrypted[0] != STEX) throw new IOException("Invalid encrypted packet");

                // Calculate CRC over decrypted data (excluding STEX and the CRC bytes themselves)
                byte[] calculatedCrc = calculateCRC(decrypted, 1, decrypted.length - 3);
                // Extract received CRC (last 2 bytes, little-endian: LSB, MSB)
                int receivedCrc = ((decrypted[decrypted.length - 1] & 0xFF) << 8) | (decrypted[decrypted.length - 2] & 0xFF);
                int calcCrc = ((calculatedCrc[1] & 0xFF) << 8) | (calculatedCrc[0] & 0xFF); // Convert calculated CRC to int (little-endian)

                if (calcCrc != receivedCrc) {
                    if (debug) Log.e(TAG, "Encrypted CRC mismatch: calculated " + bytesToHex(calculatedCrc) + ", received " +
                            String.format("%02x%02x", decrypted[decrypted.length - 2], decrypted[decrypted.length - 1]));
                    throw new IOException("Encrypted CRC mismatch");
                }

                // Return data excluding STEX, length, count, and CRC (first 5 bytes and last 2 bytes)
                return Arrays.copyOfRange(decrypted, 5, decrypted.length - 2);
            } catch (Exception e) {
                throw new IOException("Decryption failed", e);
            }
        }

        private void startPolling() {
            if (isPolling) return;
            isPolling = true;
            executor.execute(() -> {
                while (running && isPolling) {
                    try {
                        Map<String, Object> result = command("POLL", null);
                        if (result.containsKey("event")) {
                            JSObject eventData = new JSObject();
                            eventData.put("event", result.get("event"));
                            eventData.put("info", result.get("info"));
                            notifyListeners("dataReceived", eventData);
                            if ("NOTE_REJECTED".equals(result.get("event"))) {
                                Map<String, Object> rejectCode = command("LAST_REJECT_CODE", null);
                                JSObject rejectCodeData = new JSObject();
                                rejectCodeData.put("event", "LAST_REJECT_CODE");
                                rejectCodeData.put("code", rejectCode.get("code"));
                                notifyListeners("dataReceived", rejectCodeData);
                            }
                        }
                        Thread.sleep(200);
                    } catch (Exception e) {
                        Log.e(TAG, "ESSP Polling error", e);
                        try {
                            Thread.sleep(1000); // Wait longer before retrying
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            isPolling = false;
                        }
                    }
                }
                isPolling = false;
            });
        }

        private void parsePollResponse(byte[] data, Map<String, Object> result) {
            if (data[0] != 0xF0) {
                result.put("status", "FAIL");
                return;
            }

            result.put("status", "OK");
            for (int i = 1; i < data.length; i++) {
                String event = null;
                switch (data[i]) {
                    case (byte) 0xEF: event = "SLAVE_RESET"; break;
                    case (byte) 0xEE: event = "READ_NOTE"; break;
                    case (byte) 0xED: event = "CREDIT_NOTE"; break;
                    case (byte) 0xEC: event = "NOTE_REJECTING"; break;
                    case (byte) 0xEB: event = "NOTE_REJECTED"; break;
                    case (byte) 0xEA: event = "NOTE_STACKING"; break;
                    case (byte) 0xE9: event = "NOTE_STACKED"; break;
                    case (byte) 0xE8: event = "SAFE_NOTE_JAM"; break;
                    case (byte) 0xE7: event = "UNSAFE_NOTE_JAM"; break;
                    case (byte) 0xE6: event = "DISABLED"; break;
                    case (byte) 0xE5: event = "STACKER_FULL"; break;
                    case (byte) 0xE1: event = "FRAUD_ATTEMPT"; break;
                    case (byte) 0xE2: event = "NOTE_CLEARED_FROM_FRONT"; break;
                    case (byte) 0xE3: event = "NOTE_CLEARED_TO_CASHBOX"; break;
                    case (byte) 0xCC: event = "CASHBOX_REMOVED"; break;
                    case (byte) 0xCD: event = "CASHBOX_REPLACED"; break;
                    case (byte) 0xDB: event = "BAR_CODE_TICKET_VALIDATED"; break;
                    case (byte) 0xDA: event = "BAR_CODE_TICKET_ACKNOWLEDGE"; break;
                    case (byte) 0xD9: event = "NOTE_PATH_OPEN"; break;
                    case (byte) 0xB1: event = "CHANNEL_DISABLE"; break;
                    case (byte) 0xB0: event = "INITIALISING"; break;
                }
                if (event != null) {
                    result.put("event", event);
                    Map<String, Object> info = new HashMap<>();
                    result.put("info", info);
                    break;
                }
            }
        }

        private byte getCommandCode(String commandName) {
            switch (commandName) {
                case "SET_GENERATOR": return CMD_SET_GENERATOR;
                case "SET_MODULUS": return CMD_SET_MODULUS;
                case "REQ_KEY_EXCHANGE": return CMD_REQ_KEY_EXCHANGE;
                default: return 0;
            }
        }

        private byte[] calculateCRC(byte[] data, int start, int length) {
            final int CRC_SSP_SEED = 0xFFFF;
            final int CRC_SSP_POLY = 0x8005;
            int crc = CRC_SSP_SEED;

            for (int i = start; i < start + length; i++) {
                crc ^= (data[i] & 0xFF) << 8; // XOR with high byte
                for (int j = 0; j < 8; j++) {
                    if ((crc & 0x8000) != 0) {
                        crc = (crc << 1) ^ CRC_SSP_POLY;
                    } else {
                        crc <<= 1;
                    }
                }
            }
            crc &= 0xFFFF; // Ensure 16-bit range

            // Return as little-endian byte array (LSB, MSB)
            byte[] crcBytes = new byte[2];
            crcBytes[0] = (byte) (crc & 0xFF);        // LSB
            crcBytes[1] = (byte) ((crc >> 8) & 0xFF); // MSB
            return crcBytes;
        }

        private long modularPow(long base, long exponent, long modulus) {
            long result = 1;
            base %= modulus;
            while (exponent > 0) {
                if ((exponent & 1) == 1) result = (result * base) % modulus;
                base = (base * base) % modulus;
                exponent >>= 1;
            }
            return result;
        }

        private String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        }
    }
}



