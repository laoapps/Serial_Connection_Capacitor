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
          try {
            Thread.sleep(2200); // Main loop delay
          } catch (InterruptedException e) {

          }

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


  // VMC

  private long lastVmcCommandEnqueueTime = 0;
  private static final long VMC_STUCK_TIMEOUT_MS = 10000; // 10 seconds - safe & generous
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
//        if (commandQueue.size() > 10) { // or 8–15 depending on your tolerance
//          Log.w(TAG, "VMC queue overflow (" + commandQueue.size() + ") → dropping oldest");
//          commandQueue.poll(); // drop oldest
//        }
        Log.d(TAG, "Queued command for VMC: " + bytesToHex(packet, packet.length));

        // Start timeout timer for ANY command (prevents any stuck packet)
        lastVmcCommandEnqueueTime = System.currentTimeMillis();
        Log.d(TAG, "VMC command enqueued → 10s safety timeout started");
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
          packNoCounter = 0;
          lastVmcCommandEnqueueTime = 0;  // ← reset timer too
          Log.d(TAG, "SYNC received → queue cleared + timeout timer reset");
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
                          byte[] response = commandQueue.peek(); // peek first to check

                          long now = System.currentTimeMillis();
                          if (now - lastVmcCommandEnqueueTime > VMC_STUCK_TIMEOUT_MS) {
                            Log.w(TAG, "VMC command stuck >10s → dropping it (safety timeout). "
                              + "Command: " + bytesToHex(response, response.length)
                              + ", Queue size was: " + commandQueue.size());
                            commandQueue.poll();
                            lastVmcCommandEnqueueTime = 0;
                          } else {
                            // Normal send
                            byte[] toSend = commandQueue.poll(); // now remove it
                            assert toSend != null;
                            Log.d(TAG, "POLL received, sending command: " + bytesToHex(toSend, toSend.length));

                            try {
                              serialPort.getOutputStream().write(toSend);
                              serialPort.getOutputStream().flush();
                              notifyListeners("serialWriteSuccess", new JSObject().put("data", bytesToHex(toSend, toSend.length)));
                              // Success → reset stuck timer
                              lastVmcCommandEnqueueTime = 0;
                              Log.d(TAG, "Command sent successfully → timeout timer reset");
                            } catch (Exception e) {
                              Log.e(TAG, "Failed to send VMC command: " + e.getMessage());
                              // If send fails → you can decide to re-queue or drop
                              // For safety: drop it after failure (prevents re-try loop)
                              // commandQueue.addFirst(toSend); // ← uncomment if you want retry
                            }
                          }
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
  // Add these class variables for ADH814
  private volatile boolean isProcessingADH814 = false;
  private volatile int adh814CurrentStatus = 0; // 0=idle, 1=delivering, 2=delivery end

  @PluginMethod
  public void writeADH814(PluginCall call) {
    Log.d(TAG, "writeADH814 invoked: " + call.getData().toString());
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

      byte[] packet = buildADH814Packet(command, params);

      synchronized (commandQueue) {
        commandQueue.add(packet);
        Log.d(TAG, "Queued ADH814 command: " + bytesToHex(packet, packet.length));
      }

      // Start processing if not already running
      if (!isProcessingADH814) {
        startADH814CommandProcessing();
      }

      JSObject ret = new JSObject();
      ret.put("message", "ADH814 command queued");
      ret.put("data", bytesToHex(packet, packet.length));
      notifyListeners("commandQueued", ret);
      call.resolve(ret);
    } catch (Exception e) {
      call.reject("Failed to parse data or build ADH814 packet: " + e.getMessage());
    }
  }

  private volatile long lastForcedPollTime = 0;
  private long lastRunAttemptTime = 0;
  private void startADH814CommandProcessing() {
    if (isProcessingADH814) {
      return;
    }

    isProcessingADH814 = true;
    new Thread(() -> {
      Log.d(TAG, "ADH814 command processor started");

      while (isProcessingADH814 && isReading) {
        synchronized (this) {
          if (serialPort == null) {
            Log.w(TAG, "Serial port closed, stopping ADH814 processor");
            isProcessingADH814 = false;
            break;
          }

          try {
            synchronized (commandQueue) {
              if (!commandQueue.isEmpty()) {
                byte[] command = commandQueue.peek();
                if (command != null && command.length >= 2) {
                  int commandCode = command[1] & 0xFF;

                  // Check if we can send RUN command based on current status
                  if (commandCode == 0xA5 && adh814CurrentStatus != 0) {
                    Log.d(TAG, "Cannot send RUN command - motor status: " + adh814CurrentStatus);

                    // Safety: if stuck > 20 seconds → drop it
                    if (System.currentTimeMillis() - lastRunAttemptTime > 4000) {
                      Log.w(TAG, "RUN command stuck >5s → dropping it");
                      commandQueue.poll();           // remove the stuck command
                      adh814CurrentStatus = 0;       // optimistic reset
                      lastRunAttemptTime = 0;
                    } else {
                      lastRunAttemptTime = System.currentTimeMillis();
                    }

                    continue;
                  }

                  // Send the command
                  String cmdHex = bytesToHex(command, command.length);
                  Log.d(TAG, "Sending ADH814 command: " + cmdHex);
                  serialPort.getOutputStream().write(command);
                  serialPort.getOutputStream().flush();

                  JSObject writeEvent = new JSObject();
                  writeEvent.put("data", cmdHex);
                  writeEvent.put("command", String.format("%02X", commandCode));
                  notifyListeners("serialWriteSuccess", writeEvent);

                  // Wait for response based on command type
                  if (commandCode == 0xA5) { // RUN command
                    Thread.sleep(2200); // Wait longer for RUN
                  } else {
                    Thread.sleep(300); // Wait for other commands
                  }
                }
              } else {
                // Queue is empty, send periodic POLL
                if (adh814CurrentStatus != 0) {
                  long now = System.currentTimeMillis();
                  if (now - lastForcedPollTime >= 800) {  // poll every ~800 ms max
                    try {
                      byte[] pollCmd = buildADH814Packet("A3", new JSObject());
                      synchronized (commandQueue) {
                        commandQueue.add(pollCmd);  // add to the END of queue
                      }
                      lastForcedPollTime = now;
                      Log.d(TAG, "Forced POLL because status = " + adh814CurrentStatus);
                    } catch (Exception e) {
                      Log.e(TAG, "Failed to queue forced POLL", e);
                    }
                  }
                }
              }
            }
          } catch (IOException e) {
            Log.e(TAG, "Error sending ADH814 command: " + e.getMessage());
          } catch (InterruptedException e) {
            Log.w(TAG, "ADH814 processor interrupted");
            break;
          } catch (Exception e) {
            Log.e(TAG, "Unexpected error in ADH814 processor: " + e.getMessage());
          }
        }

        try {
          Thread.sleep(50); // Main loop delay
        } catch (InterruptedException e) {
          break;
        }
      }

      isProcessingADH814 = false;
      Log.d(TAG, "ADH814 command processor stopped");
    }).start();
  }

  @PluginMethod
  public void startReadingADH814(PluginCall call) {
    Log.d(TAG, "startReadingADH814 invoked: " + call.getData().toString());
    if (serialPort == null) {
      call.reject("No serial connection open");
      return;
    }

    // Clear input buffer
    try {
      int available = serialPort.getInputStream().available();
      if (available > 0) {
        serialPort.getInputStream().skip(available);
        Log.d(TAG, "Cleared " + available + " bytes from input buffer");
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to clear input buffer: " + e.getMessage());
    }

    isReading = true;
    JSObject ret = new JSObject();
    ret.put("message", "ADH814 reading started");
    notifyListeners("readingStarted", ret);
    call.resolve(ret);

    // Start reading thread
    new Thread(() -> {
      Log.d(TAG, "ADH814 reading thread started");
      byte[] buffer = new byte[1024];
      ByteArrayOutputStream packetBuffer = new ByteArrayOutputStream();

      while (isReading) {
        try {
          synchronized (this) {
            if (serialPort == null) {
              Log.w(TAG, "Serial port closed, stopping ADH814 read thread");
              break;
            }

            int available = serialPort.getInputStream().available();
            if (available > 0) {
              int len = serialPort.getInputStream().read(buffer, 0, Math.min(available, buffer.length));
              if (len > 0) {
                Log.d(TAG, "ADH814 received " + len + " bytes: " + bytesToHex(buffer, len));

                // Process received data
                processADH814Data(buffer, len);

                // Notify raw data
                JSObject dataEvent = new JSObject();
                dataEvent.put("data", bytesToHex(buffer, len));
                notifyListeners("dataReceived", dataEvent);
              }
            }
          }

          Thread.sleep(10);
        } catch (Exception e) {
          if (isReading) {
            Log.e(TAG, "ADH814 read error: " + e.getMessage());
          }
        }
      }

      Log.d(TAG, "ADH814 reading thread stopped");
    }).start();
  }

  private void processADH814Data(byte[] data, int length) {
    if (length < 4) {
      return; // Not enough data for a complete packet
    }

    try {
      // Try to parse each possible packet starting position
      for (int i = 0; i <= length - 4; i++) {
        JSObject response = parseADH814Response(data, i, length);
        if (response != null) {
          handleADH814Response(response);
          break; // Process one packet at a time
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Error processing ADH814 data: " + e.getMessage());
    }
  }

  private JSObject parseADH814Response(byte[] buffer, int start, int totalLength) {
    if (totalLength - start < 4) {
      return null;
    }

    int address = buffer[start] & 0xFF;
    int command = buffer[start + 1] & 0xFF;

    // Determine packet length based on command
    int packetLength = getADH814PacketLength(command);
    if (totalLength - start < packetLength) {
      return null; // Not enough data for complete packet
    }

    // Extract complete packet
    byte[] packet = new byte[packetLength];
    System.arraycopy(buffer, start, packet, 0, packetLength);

    // Verify CRC
    byte[] dataBytes = Arrays.copyOfRange(packet, 0, packetLength - 2);
    int receivedCRC = ((packet[packetLength - 1] & 0xFF) << 8) | (packet[packetLength - 2] & 0xFF);
    int calculatedCRC = calculateCRCResponse(dataBytes);

    JSObject response = new JSObject();
    response.put("address", address);
    response.put("command", String.format("%02X", command));
    response.put("data", bytesToHex(packet, packetLength));
    response.put("rawData", bytesToHex(dataBytes, dataBytes.length));

    if (receivedCRC != calculatedCRC) {
      response.put("crcError", true);
      Log.w(TAG, "ADH814 CRC mismatch for command: " + String.format("%02X", command));
    }

    // Parse response data based on command
    parseADH814ResponseData(response, packet, packetLength);

    return response;
  }

  private void parseADH814ResponseData(JSObject response, byte[] packet, int packetLength) {
    int command = Integer.parseInt(response.getString("command"), 16);

    switch (command) {
      case 0xA3: // POLL
        if (packetLength >= 11) {
          JSObject statusDetails = new JSObject();
          statusDetails.put("status", packet[2] & 0xFF); // Status byte
          statusDetails.put("motorNumber", packet[3] & 0xFF);
          statusDetails.put("faultCode", packet[4] & 0x03);
          statusDetails.put("dropSuccess", (packet[4] & 0x04) == 0);
          statusDetails.put("maxCurrent", ((packet[5] & 0xFF) << 8) | (packet[6] & 0xFF));
          statusDetails.put("avgCurrent", ((packet[7] & 0xFF) << 8) | (packet[8] & 0xFF));
          statusDetails.put("runTime", packet[9] & 0xFF);
          statusDetails.put("temperature", (byte) packet[10]); // Signed byte
          response.put("statusDetails", statusDetails);

          // Update current status
          adh814CurrentStatus = packet[2] & 0xFF;
          if (adh814CurrentStatus == 2) {
            Log.i(TAG, "Poll shows delivery ended (status=2) → auto-queuing ACK");
            try {
              byte[] ackCmd = buildADH814Packet("A6", new JSObject());
              synchronized (commandQueue) {
                commandQueue.add(ackCmd);  // add to end
              }
            } catch (Exception e) {
              Log.e(TAG, "Failed to auto-queue ACK", e);
            }
          }
        }
        break;

      case 0xA5: // RUN
      case 0xA4: // TEMP
      case 0x35: // SET_SWAP
        if (packetLength >= 5) {
          response.put("executionStatus", packet[2] & 0xFF);
        }
        break;

      case 0xA1: // ID
        if (packetLength >= 18) {
          String idString = new String(packet, 2, 16).trim();
          response.put("idString", idString);
        }
        break;
    }
  }

  private void handleADH814Response(JSObject response) {
    String command = response.getString("command");
    Log.d(TAG, "ADH814 response - Command: " + command + ", Data: " + response.getString("data"));

    switch (command) {
      case "A3": // POLL response
        handleADH814PollResponse(response);
        break;
      case "A5": // RUN response
        handleADH814RunResponse(response);
        break;
      case "A6": // ACK response
        handleADH814AckResponse(response);
        break;
    }

    // Notify specific response
    notifyListeners("adh814Response", response);

    // Remove the corresponding command from queue
    synchronized (commandQueue) {
      if (!commandQueue.isEmpty()) {
        commandQueue.poll();
        Log.d(TAG, "Removed command from queue after response");
      }
    }
  }

  private void handleADH814PollResponse(JSObject response) {
    if (response.has("statusDetails")) {
      JSObject statusDetails = response.getJSObject("statusDetails");
      int status = statusDetails.getInteger("status", 0);
      int motorNumber = statusDetails.getInteger("motorNumber", 0);
      int temperature = statusDetails.getInteger("temperature", 0);

      Log.d(TAG, "ADH814 Poll - Status: " + status + ", Motor: " + motorNumber + ", Temp: " + temperature);

      // Check for completion and queue ACK if needed
      if (status == 2) { // Delivery complete
        Log.d(TAG, "Motor delivery complete, queuing ACK");
        try {
          byte[] ackCommand = buildADH814Packet("A6", new JSObject());
          synchronized (commandQueue) {
            commandQueue.add(ackCommand);
            Log.d(TAG, "Queued ACK command after delivery complete");
          }
        } catch (Exception e) {
          Log.e(TAG, "Failed to queue ACK command: " + e.getMessage());
        }
      }

      // Notify status update
      JSObject statusEvent = new JSObject();
      statusEvent.put("status", status);
      statusEvent.put("statusDetails", statusDetails);
      notifyListeners("adh814Status", statusEvent);
    }
  }

  private void handleADH814RunResponse(JSObject response) {
    int executionStatus = response.getInteger("executionStatus", 0);
    Log.d(TAG, "ADH814 Run response - Execution Status: " + executionStatus);

    if (executionStatus == 0) {
      Log.d(TAG, "Motor run command accepted");
      adh814CurrentStatus = 1; // Set to delivering
    } else {
      Log.w(TAG, "Motor run command failed with status: " + executionStatus);
    }

    notifyListeners("adh814RunResponse", response);
  }

  private void handleADH814AckResponse(JSObject response) {
    Log.d(TAG, "ADH814 ACK received");
    adh814CurrentStatus = 0; // Reset to idle after ACK
    notifyListeners("adh814Ack", response);
  }

  private int getADH814PacketLength(int command) {
    switch (command) {
      case 0xA1: return 18; // ID
      case 0xA3: return 11; // POLL
      case 0xA5: return 5;  // RUN
      case 0xA6: return 4;  // ACK
      case 0xA4: return 5;  // TEMP
      case 0x35: return 5;  // SET_SWAP
      case 0x21: return 5;  // TWO_WIRE_MODE
      default: return 4;    // Minimum
    }
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
  private volatile boolean needsIdleCheck = false;
  private volatile boolean needsPostA5Poll = false;
  private final Object queueLock = new Object();
  private volatile CountDownLatch responseLatch = null;
  //ADH814




  // M102

  private final Map<String, Integer> expectedResponseLengthsMT102 = new ConcurrentHashMap<>();
  private volatile boolean isProcessingQueueMT102 = false;
  private volatile CountDownLatch responseLatchMT102 = null;

// MT102 Specific Methods Only - Add these to your existing plugin class

  @PluginMethod
  public void writeMT102(PluginCall call) {
    Log.d(TAG, "writeMT102 invoked: " + call.getData().toString());
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

      byte[] packet = buildMT102Packet(command, params);

      synchronized (commandQueue) {
        commandQueue.add(packet);
        Log.d(TAG, "Queued MT102 command: " + bytesToHex(packet, packet.length));
        Log.d(TAG, "Command queue size: " + commandQueue.size());
      }

      JSObject ret = new JSObject();
      ret.put("message", "MT102 command queued");
      ret.put("data", bytesToHex(packet, packet.length));
      ret.put("queueSize", commandQueue.size());
      notifyListeners("commandQueued", ret);
      call.resolve(ret);

    } catch (Exception e) {
      call.reject("Failed to parse data or build MT102 packet: " + e.getMessage());
    }
  }

  @PluginMethod
  public void startReadingMT102(PluginCall call) {
    Log.d(TAG, "startReadingMT102 invoked: " + call.getData().toString());
    if (serialPort == null) {
      call.reject("No serial connection open");
      return;
    }

    // Clear input buffer
    try {
      int available = serialPort.getInputStream().available();
      if (available > 0) {
        serialPort.getInputStream().skip(available);
        Log.d(TAG, "Cleared " + available + " bytes from input buffer");
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to clear input buffer: " + e.getMessage());
    }

    isReading = true;
    JSObject ret = new JSObject();
    ret.put("message", "MT102 reading started");
    notifyListeners("readingStarted", ret);

    if (call != null) {
      call.resolve(ret);
    }

    // Start the MT102 reading thread
    new Thread(() -> {
      Log.d(TAG, "MT102 reading thread started");
      byte[] buffer = new byte[1024];
      ByteArrayOutputStream packetBuffer = new ByteArrayOutputStream();

      while (isReading) {
        synchronized (this) {
          if (serialPort == null) {
            Log.w(TAG, "Serial port closed, stopping MT102 thread");
            break;
          }

          try {
            // Process command queue - send next command if available
            processMT102CommandQueue();

            // Read incoming data
            int available = serialPort.getInputStream().available();
            if (available > 0) {
              int len = serialPort.getInputStream().read(buffer, 0, Math.min(available, buffer.length));
              if (len > 0) {
                Log.d(TAG, "MT102 received " + len + " bytes: " + bytesToHex(buffer, len));
                packetBuffer.write(buffer, 0, len);
                byte[] accumulated = packetBuffer.toByteArray();

                // Process complete packets (20 bytes each for MT102)
                int processedBytes = 0;
                for (int i = 0; i <= accumulated.length - 20; i++) {
                  byte[] packet = new byte[20];
                  System.arraycopy(accumulated, i, packet, 0, 20);

                  JSObject response = parseMT102Response(packet);
                  if (response != null) {
                    Log.d(TAG, "MT102 Response parsed: " + response.toString());
                    handleMT102Response(response);
                    processedBytes = i + 20;
                  }
                }

                // Remove processed data from buffer
                if (processedBytes > 0) {
                  byte[] newAccumulated = new byte[accumulated.length - processedBytes];
                  System.arraycopy(accumulated, processedBytes, newAccumulated, 0, newAccumulated.length);
                  packetBuffer.reset();
                  if (newAccumulated.length > 0) {
                    packetBuffer.write(newAccumulated);
                  }
                }
              }
            }
          } catch (Exception e) {
            if (isReading) {
              Log.e(TAG, "MT102 read error: " + e.getMessage());
              JSObject errorEvent = new JSObject();
              errorEvent.put("error", "Read error: " + e.getMessage());
              notifyListeners("readError", errorEvent);
            }
          }
        }

        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          break;
        }
      }

      Log.d(TAG, "MT102 reading thread stopped");
    }).start();
  }

  // Private helper methods for MT102
  private void processMT102CommandQueue() {
    synchronized (commandQueue) {
      if (commandQueue.isEmpty()) {
        return;
      }

      try {
        byte[] command = commandQueue.peek();
        if (command != null && serialPort != null) {
          String cmdHex = bytesToHex(command, command.length);
          Log.d(TAG, "Sending MT102 command: " + cmdHex);

          serialPort.getOutputStream().write(command);
          serialPort.getOutputStream().flush();

          JSObject writeEvent = new JSObject();
          writeEvent.put("data", cmdHex);
          writeEvent.put("queueSize", commandQueue.size() - 1);
          notifyListeners("serialWriteSuccess", writeEvent);

          Thread.sleep(50);
        }
      } catch (Exception e) {
        Log.e(TAG, "Error sending MT102 command: " + e.getMessage());
        commandQueue.poll();
      }
    }
  }

  private void handleMT102Response(JSObject response) {
    JSObject dataEvent = new JSObject();
    dataEvent.put("data", response.getString("data"));
    dataEvent.put("command", response.getString("command"));
    dataEvent.put("address", response.getString("address"));
    notifyListeners("dataReceived", dataEvent);

    notifyListeners("mt102Response", response);

    synchronized (commandQueue) {
      if (!commandQueue.isEmpty()) {
        byte[] removedCommand = commandQueue.poll();
        Log.d(TAG, "Removed command from queue, remaining: " + commandQueue.size());

        JSObject queueEvent = new JSObject();
        queueEvent.put("queueSize", commandQueue.size());
        queueEvent.put("removedCommand", bytesToHex(removedCommand, removedCommand.length));
        notifyListeners("commandProcessed", queueEvent);
      }
    }
  }

  private byte[] buildMT102Packet(String command, JSObject params) {
    int address = clampToByte(params.getInteger("address", 0x01));
    if (address < 0x01 || address > 0x08) {
      throw new IllegalArgumentException("Address must be 0x01-0x08 for MT102");
    }

    byte[] data;
    byte cmdByte;

    switch (command.toUpperCase()) {
      case "01": // Get Serial Number
        cmdByte = 0x01;
        data = new byte[0];
        break;

      case "03": // Motor Poll
        cmdByte = 0x03;
        data = new byte[0];
        break;

      case "04": // Motor Scan
        cmdByte = 0x04;
        int motorIndex = clampToByte(params.getInteger("motorIndex", 0));
        if (motorIndex < 0 || motorIndex > 99) {
          throw new IllegalArgumentException("Motor index must be 0-99");
        }
        data = new byte[]{ (byte) motorIndex };
        break;

      case "05": // Motor Run
        cmdByte = 0x05;
        motorIndex = clampToByte(params.getInteger("motorIndex", 0));
        if (motorIndex < 0 || motorIndex > 59) {
          throw new IllegalArgumentException("Motor index must be 0-59");
        }
        int motorType = clampToByte(params.getInteger("motorType", 0));
        if (motorType < 0 || motorType > 3) {
          throw new IllegalArgumentException("Motor type must be 0-3");
        }
        int lightCurtainMode = clampToByte(params.getInteger("lightCurtainMode", 0));
        if (lightCurtainMode < 0 || lightCurtainMode > 2) {
          throw new IllegalArgumentException("Light curtain mode must be 0-2");
        }
        int overcurrentThreshold = clampToByte(params.getInteger("overcurrentThreshold", 0));
        int undercurrentThreshold = clampToByte(params.getInteger("undercurrentThreshold", 0));
        int timeout = clampToByte(params.getInteger("timeout", 0));

        data = new byte[6];
        data[0] = (byte) motorIndex;
        data[1] = (byte) motorType;
        data[2] = (byte) lightCurtainMode;
        data[3] = (byte) overcurrentThreshold;
        data[4] = (byte) undercurrentThreshold;
        data[5] = (byte) timeout;
        break;

      case "07": // Read Temperature
        cmdByte = 0x07;
        data = new byte[0];
        break;

      case "08": // Write DO
        cmdByte = 0x08;
        int doIndex = clampToByte(params.getInteger("doIndex", 0));
        if (doIndex < 0 || doIndex > 7) {
          throw new IllegalArgumentException("DO index must be 0-7");
        }
        int operation = clampToByte(params.getInteger("operation", 0));
        if (operation != 0 && operation != 1) {
          throw new IllegalArgumentException("Operation must be 0 (OFF) or 1 (ON)");
        }
        data = new byte[]{ (byte) doIndex, (byte) operation };
        break;

      case "09": // Read DI
        cmdByte = 0x09;
        data = new byte[0];
        break;

      case "FF": // Set Address
        cmdByte = (byte) 0xFF;
        int newAddress = clampToByte(params.getInteger("newAddress", 0x01));
        if (newAddress < 0x01 || newAddress > 0x08) {
          throw new IllegalArgumentException("New address must be 0x01-0x08");
        }
        data = new byte[]{ (byte) newAddress };
        break;

      default:
        throw new IllegalArgumentException("Unsupported MT102 command: " + command);
    }

    // Build 20-byte packet
    byte[] packet = new byte[20];
    packet[0] = (byte) address;
    packet[1] = cmdByte;

    // Pad data to 16 bytes
    byte[] paddedData = new byte[16];
    System.arraycopy(data, 0, paddedData, 0, Math.min(data.length, 16));
    System.arraycopy(paddedData, 0, packet, 2, 16);

    // Calculate CRC16
    byte[] dataForCRC = Arrays.copyOfRange(packet, 0, 18);
    int crc = calculateM102CRC16(dataForCRC);

    packet[18] = (byte) (crc & 0xFF);
    packet[19] = (byte) ((crc >> 8) & 0xFF);

    Log.d(TAG, "Built MT102 packet - Address: " + address +
      ", Command: " + String.format("%02X", cmdByte) +
      ", Data: " + bytesToHex(paddedData, paddedData.length) +
      ", CRC: " + String.format("%04X", crc));

    return packet;
  }

  private JSObject parseMT102Response(byte[] packet) {
    if (packet.length != 20) {
      Log.w(TAG, "Invalid MT102 response length: " + packet.length + " bytes, expected 20");
      return null;
    }

    // Verify CRC
    byte[] dataForCRC = Arrays.copyOfRange(packet, 0, 18);
    int calculatedCRC = calculateM102CRC16(dataForCRC);
    int receivedCRC = ((packet[19] & 0xFF) << 8) | (packet[18] & 0xFF);

    if (receivedCRC != calculatedCRC) {
      Log.w(TAG, "MT102 CRC mismatch: received 0x" + String.format("%04X", receivedCRC) +
        ", calculated 0x" + String.format("%04X", calculatedCRC));
    }

    JSObject response = new JSObject();
    int address = packet[0] & 0xFF;
    int command = packet[1] & 0xFF;
    byte[] data = Arrays.copyOfRange(packet, 2, 18);

    response.put("address", String.format("%02X", address));
    response.put("command", String.format("%02X", command));
    response.put("data", bytesToHex(data, data.length));
    response.put("crc", String.format("%04X", receivedCRC));
    response.put("crcValid", receivedCRC == calculatedCRC);

    // Parse response data based on command
    switch (command) {
      case 0x01: // Get Serial Number
        if (data.length >= 12) {
          String serialNumber = new String(data, 0, 12).trim();
          response.put("serialNumber", serialNumber);
        }
        break;

      case 0x03: // Motor Poll
        if (data.length >= 10) {
          JSObject statusDetails = new JSObject();
          statusDetails.put("executionStatus", data[0] & 0xFF);
          statusDetails.put("runningMotor", data[1] & 0xFF);
          statusDetails.put("executionResult", data[2] & 0xFF);
          statusDetails.put("peakCurrent", ((data[3] & 0xFF) << 8) | (data[4] & 0xFF));
          statusDetails.put("averageCurrent", ((data[5] & 0xFF) << 8) | (data[6] & 0xFF));
          statusDetails.put("runningTime", ((data[7] & 0xFF) << 8) | (data[8] & 0xFF));
          statusDetails.put("lightCurtainState", data[9] & 0xFF);
          response.put("statusDetails", statusDetails);
        }
        break;

      case 0x04: // Motor Scan
        if (data.length >= 1) {
          int result = data[0] & 0xFF;
          response.put("result", String.format("%02X", result));
        }
        break;

      case 0x05: // Motor Run
        if (data.length >= 1) {
          response.put("executionResult", data[0] & 0xFF);
        }
        break;

      case 0x07: // Read Temperature
        if (data.length >= 2) {
          int tempRaw = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
          response.put("temperature", tempRaw / 10.0);
        }
        break;

      case 0x08: // Write DO
        if (data.length >= 2) {
          response.put("doIndex", data[0] & 0xFF);
          response.put("operationResult", String.format("%02X", data[1] & 0xFF));
        }
        break;

      case 0x09: // Read DI
        if (data.length >= 4) {
          JSObject diStatus = new JSObject();
          for (int i = 0; i < 8 && i < data.length; i++) {
            diStatus.put("DI" + i, (data[i] & 0xFF) == 1);
          }
          response.put("diStatus", diStatus);
        }
        break;

      case 0xFF: // Set Address
        if (data.length >= 1) {
          response.put("newAddress", data[0] & 0xFF);
        }
        break;
    }

    return response;
  }

  private int calculateM102CRC16(byte[] data) {
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


  // M102
}
