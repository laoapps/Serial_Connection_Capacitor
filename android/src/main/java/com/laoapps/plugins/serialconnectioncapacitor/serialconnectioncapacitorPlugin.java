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
import java.util.HashMap;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.LinkedList;
import java.util.HashSet;

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
          Log.d(TAG, "Packet for 06: " + bytesToHex(packet, packet.length));
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




  private byte[] buildPacket(String command, JSObject params) {
    byte[] stx = {(byte) 0xFA, (byte) 0xFB};
    byte cmdByte = (byte) Integer.parseInt(command.length() > 2 ? command.substring(2) : command, 16);
    byte packNo = getNextPackNo();

    byte[] text;
    Log.d(TAG, "Input command " + command);
    switch (command) {
      case "01":
        text = new byte[]{(byte) clampToByte(params.getInteger("slot", 1))};
        break;
      case "31":
        synchronized (commandQueue) {
          commandQueue.clear();
          packNoCounter = 0; // Reset to align with Node.js sync
          Log.d(TAG, "Queue cleared and PackNO reset on sync command");
        }
        text = new byte[0];
        break;
      case "06":
        int slot = clampToByte(params.getInteger("slot", 1));
        byte[] buff = new byte[5]; // 5 bytes for text: packNo + data + checksum placeholder
        buff[0] = packNo;         // Series (e.g., 0x01)
        buff[1] = (byte) 0x01;    // Enable drop sensor
        buff[2] = (byte) 0x00;    // Disable elevator
        buff[3] = (byte) (slot / 256); // Slot high byte: 0x00
        buff[4] = (byte) (slot % 256); // Slot low byte: 0x01
        text = buff;
        break;
      case "11":
        text = new byte[]{(byte) clampToByte(params.getInteger("slot", 1))};
        break;
      case "16":
        text = new byte[]{(byte) clampToByte(params.getInteger("ms", 10))};
        break;
      case "25":
        text = new byte[]{0, 0, 0, (byte) clampToByte(params.getInteger("amount", 0))};
        break;
      case "27":
        int mode = clampToByte(params.getInteger("mode", 1));
        String amountHex = params.getString("amount", "00000000");
        byte[] amount = hexStringToByteArray(amountHex);
        text = new byte[]{(byte) mode, amount[0], amount[1], amount[2], amount[3]};
        break;
      case "28":
        text = new byte[]{0, (byte) 0xFF, (byte) 0xFF};
        break;
      case "51":
        text = new byte[0];
        break;
      case "61":
        text = new byte[]{1};
        break;
      case "7001":
        text = new byte[]{0x01, 0};
        break;
      case "7017":
        text = new byte[]{0x17, 0};
        break;
      case "ENABLE": // Map to 7018
      case "7018":
        if (params.getBoolean("enable", false)) {
          text = new byte[]{0x18, 1, (byte) clampToByte(params.getInteger("value", 200))};
        } else {
          text = new byte[]{0x18, 0};
        }
        break;
      case "7019":
        text = new byte[]{0x19, 0};
        break;
      case "7020":
        text = new byte[]{0x20, 0};
        break;
      case "7023":
        text = new byte[]{0x23, 0};
        break;
      case "7028":
        if (params.has("enable")) {
          text = new byte[]{0x28, 1, params.getBoolean("enable", false) ? (byte) 0xFF : 0};
        } else {
          text = new byte[]{0x28, 0};
        }
        break;
      case "7037":
        text = new byte[]{
          0x37, 1, 0,
          (byte) clampToByte(params.getInteger("lowTemp", 5)),
          (byte) clampToByte(params.getInteger("highTemp", 10)),
          5, 0, 0, 1, 10, 0
        };
        break;
      default:
        text = new byte[0];
        Log.w(TAG, "Unsupported command: " + command + ", params: " + params.toString());
    }

    byte length = (byte) (text.length); // Length is just the text length now, including packNo
    byte[] data = new byte[stx.length + 2 + text.length + 1]; // STX + cmd + length + text + XOR
    System.arraycopy(stx, 0, data, 0, stx.length);
    data[2] = cmdByte;
    data[3] = length;
    System.arraycopy(text, 0, data, 4, text.length); // Text includes packNo
//    data[4]=(byte)0x00;
    data[data.length - 1] = calculateXOR(data, data.length - 1); // Overwrite last byte with checksum

    Log.d(TAG, "Built packet: " + bytesToHex(data, data.length));
    return data;
  }
//   yours
//  fa fb 06 05 0d 01 00 00 0e
//   nodejs code
//  command =['fa', 'fb', '06', '05',getNextNo(),'01','00','00','01']
//   out put
//  fa fb 06 05 01 01 00 00 01 03
//  fa fb 06 05 00 01 00 00 01 02
  private byte getNextPackNo() {
    synchronized (this) {
      packNoCounter = (byte) ((packNoCounter + 1) % 256); // Match Node.js 0-255 cycle
      return packNoCounter == 0 ? (byte) 1 : packNoCounter; // Start at 01, not 00
    }
  }

    private int clampToByte(Integer value) {
        if (value == null) return 0;
        return Math.min(Math.max(value, 0), 255); // Clamp to 0-255
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
                          byte[] response = commandQueue.poll(); // Peek, don’t poll yet
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
                          byte[] ackedCmd = commandQueue.peek(); // Dequeue on ACK
                          Log.d(TAG, "ACK received, dequeued command: " + bytesToHex(ackedCmd, ackedCmd.length));
                          JSObject ackEvent = new JSObject();
                          ackEvent.put("data", bytesToHex(ackedCmd, ackedCmd.length));
                          notifyListeners("commandAcknowledged", ackEvent);
                        }
                      }
                    } else { // Response
                      if (!sentResponses.contains(packetHex)) {
                        Log.d(TAG, "Sending unique response to client: " + packetHex);
                        JSObject dataEvent = new JSObject();
                        dataEvent.put("data", packetHex);
                        notifyListeners("dataReceived", dataEvent);
                        sentResponses.add(packetHex);
                      }
                      byte[] ack = hexStringToByteArray("fafb420043");
                      Log.d(TAG, "Sending ACK for response: fafb420043");
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
            Thread.sleep(10); // Reduce to 10ms for faster polling
          } catch (Exception e) {
            if (isReading) Log.e(TAG, "VMC read error: " + e.getMessage());
          }
        }
      }
    }).start();
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
}
