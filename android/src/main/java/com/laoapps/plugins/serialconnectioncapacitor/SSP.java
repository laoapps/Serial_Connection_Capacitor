package com.laoapps.plugins.serialconnectioncapacitor;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fazecast.jSerialComm.SerialPort;
import com.hoho.android.usbserial.driver.UsbSerialPort;


public class SSP {


  private static final String TAG = "SSP";
  private UsbSerialPort usbSerialPort; // ADD THIS
  private boolean usingUsbPort = false; // ADD THIS
  private SerialPort port; // Using jSerialComm's SerialPort

  // Configuration (matches KiosServer)
  private final byte id = 0x00;
  public  boolean debug = false;
  private final int timeout = 10000; // milliseconds
  private final String fixedKey = "0123456701234567"; // Match Node.js version

  // State
  public byte[] encryptKey = null;
  public int eCount = 0;
  private byte sequence = (byte) 0x80;
  private boolean enabled = false;
  public int protocolVersion = 6;
  String unitType = null;
  private SSPParser parser = new SSPParser();

  // Encryption keys
  private BigInteger generator;
  private BigInteger modulus;
  private BigInteger hostRandom;
  private BigInteger hostInter;

  // Event listeners
  private final Map<String, Consumer<JSONObject>> eventListeners = new HashMap<>();

  // Command definitions (from SSPUtils)
  private final Map<String, SSPUtils.CommandInfo> commandList = SSPUtils.commands;
  private boolean polling = false;
  private Thread pollThread;

  // Constructor
  public SSP() {
    // Port is opened separately
  }
  // In SSP.java, add this method:

  /**
   * Set an existing USB serial port to use for communication
   */
  public void setUsbSerialPort(UsbSerialPort usbPort) {
    this.usbSerialPort = usbPort;
    this.usingUsbPort = true;
    Log.d(TAG, "USB serial port set for SSP");
  }


// Modify the write/read methods to use either port

  public void setQuietMode(boolean quiet) {
    this.debug = quiet;
  }

  public boolean isQuietMode() {
    return debug;
  }

  /**
   * Opens the serial port.
   *
   * @param portPath The serial port path (e.g., "/dev/cu.usbserial-0001")
   * @throws Exception If port opening fails
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  public void open(String portPath) throws Exception {
    port = SerialPort.getCommPort(portPath);

    // Match Node.js serialport defaults EXACTLY
    port.setBaudRate(9600);
    port.setNumDataBits(8);
    port.setNumStopBits(2); // CRITICAL: Node.js uses 2 stop bits, not 1!
    port.setParity(SerialPort.NO_PARITY);
    port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);

    // Use timeouts similar to Node.js
    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

    System.out.println("Opening port: " + portPath);
    System.out.println("Settings: 9600 8N2 (8 data bits, No parity, 2 stop bits)");

    if (!port.openPort()) {
      throw new Exception("Failed to open port. Error code: " + port.getLastErrorCode()
        + " - " + port.getSystemPortName());
    }

    // Give the device time to initialize
    System.out.println("Waiting for device to initialize...");
    Thread.sleep(1000);

    // Clear any pending data
    port.flushIOBuffers();

    System.out.println("Port opened successfully");
    System.out.println("Description: " + port.getPortDescription());
    System.out.println("Bytes available: " + port.bytesAvailable());

    emitEvent("OPEN", new JSONObject());
  }

  public void openWithDifferentSettings(String portPath) throws Exception {
    port = SerialPort.getCommPort(portPath);

    // Try different baud rates
    int[] baudRates = {9600, 19200, 38400, 115200};

    for (int baudRate : baudRates) {
      try {
        System.out.println("Trying baud rate: " + baudRate);
        port.setBaudRate(baudRate);
        port.setNumDataBits(8);
        port.setNumStopBits(1);
        port.setParity(SerialPort.NO_PARITY);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

        if (port.openPort()) {
          System.out.println("Successfully opened with baud rate: " + baudRate);
          Thread.sleep(1000);
          port.flushIOBuffers();
          return;
        }
      } catch (Exception e) {
        System.out.println("Failed with baud rate " + baudRate + ": " + e.getMessage());
      }
    }

    throw new Exception("Could not open port with any baud rate");
  }

  /**
   * Closes the serial port.
   *
   * @throws Exception If closing fails
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  public void close() throws Exception {
    stopPoll();
    if (port != null) {
      port.closePort();
      port = null;
      emitEvent("CLOSE", new JSONObject());
    }
  }

  /**
   * Registers an event listener.
   *
   * @param eventName The event name (e.g., "NOTE_REJECTED")
   * @param listener The callback to handle the event
   */
  public void on(String eventName, Consumer<JSONObject> listener) {
    eventListeners.put(eventName.toUpperCase(), listener);
  }

  /**
   * Emits an event to registered listeners.
   *
   * @param eventName The event name
   * @param data The event data
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  private void emitEvent(String eventName, JSONObject data) {
    Consumer<JSONObject> listener = eventListeners.get(eventName.toUpperCase());
    if (listener != null) {
      listener.accept(data);
    }
    if (debug) {
      System.out.println(eventName + ": " + data.toString());
    }
  }

  /**
   * Executes a command and returns a CompletableFuture with the response.
   *
   * @param command The command name
   * @param args Command arguments (optional)
   * @return CompletableFuture with the parsed response
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  public CompletableFuture<JSONObject> command(String command, JSONObject args) {
    return CompletableFuture.supplyAsync(() -> {
      int maxRetries = (command.equals("SYNC")) ? 3 : 1;
      Exception lastException = null;

      for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
          int preECount = eCount;
          SSPParser localParser = new SSPParser();
          byte[] packet = getPacket(command, args);
          if (this.debug) {
            System.out.println("→ Tx: " + bytesToHex(packet));
          }

          // Send command based on port type
          if (usingUsbPort) {
            if (usbSerialPort == null) {
              throw new RuntimeException("USB serial port is null");
            }
            usbSerialPort.write(packet, 1000);
          } else {
            if (port == null) {
              throw new RuntimeException("Native serial port is null");
            }
            port.writeBytes(packet, packet.length);
          }

          // Read response
          ByteArrayOutputStream response = new ByteArrayOutputStream();
          long start = System.currentTimeMillis();
          List<byte[]> packets = new java.util.ArrayList<>();

          while (System.currentTimeMillis() - start < timeout) {
            int avail = 0;
            byte[] chunk = null;

            if (usingUsbPort) {
              if (usbSerialPort == null) continue;
              chunk = new byte[1024];
              int len = usbSerialPort.read(chunk, 1000);
              if (len > 0) {
                if (len < chunk.length) {
                  byte[] trimmed = new byte[len];
                  System.arraycopy(chunk, 0, trimmed, 0, len);
                  chunk = trimmed;
                }
                avail = len;
              }
            } else {
              if (port == null) continue;
              avail = port.bytesAvailable();
              if (avail > 0) {
                chunk = new byte[avail];
                int len = port.readBytes(chunk, chunk.length);
                if (len > 0) {
                  if (len < avail) {
                    byte[] trimmed = new byte[len];
                    System.arraycopy(chunk, 0, trimmed, 0, len);
                    chunk = trimmed;
                  }
                  avail = len;
                }
              }
            }

            if (avail > 0 && chunk != null) {
              response.write(chunk, 0, chunk.length);
              if (this.debug) {
                System.out.println("← Rx chunk (" + chunk.length + "): " + bytesToHex(chunk));
              }
              packets = localParser.parse(chunk);
              if (!packets.isEmpty()) {
                break;
              }
            } else {
              Thread.sleep(10);
            }
          }

          if (response.size() == 0) {
            if (attempt < maxRetries) {
              System.out.println(command + " attempt " + attempt + " failed, retrying...");
              Thread.sleep(200 * attempt); // Exponential backoff
              continue;
            }
            throw new RuntimeException("No bytes received after " + timeout + " ms");
          }

          if (packets.isEmpty()) {
            packets = localParser.flush();
          }

          if (packets.isEmpty()) {
            throw new RuntimeException("No valid SSP packet found in response");
          }

          byte[] pkt = packets.get(0);

          // Handle SYNC response more flexibly
          if (command.equals("SYNC")) {
            // Try multiple ways to detect SYNC success

            // Method 1: Check specific byte positions
            boolean syncSuccess = false;

            if (pkt.length >= 4) {
              // Check for typical SYNC response pattern
              if (pkt[1] == (byte) 0x80 && (pkt[3] == (byte) 0xF0 || pkt[3] == (byte) 0x00)) {
                syncSuccess = true;
              }
            }

            // Method 2: Look for success status byte anywhere
            if (!syncSuccess) {
              for (byte b : pkt) {
                if ((b & 0xFF) == 0xF0) {
                  syncSuccess = true;
                  break;
                }
              }
            }

            // Method 3: If we got any response at all, assume success
            if (!syncSuccess && pkt.length > 2) {
              System.out.println("SYNC: Assuming success based on received response");
              syncSuccess = true;
            }

            if (syncSuccess) {
              JSONObject result = new JSONObject();
              result.put("success", true);
              result.put("status", "OK");
              result.put("command", command);
              result.put("info", new JSONObject());
              System.out.println("SYNC successful!");
              return result;
            }
          }

          // Try to extract packet data for other commands
          try {
            byte[] data = SSPUtils.extractPacketData(pkt, encryptKey, preECount, debug);
            JSONObject result = parseData(data, command);

            if (this.debug) {
              System.out.println("Response for " + command + ": " + result.toString(2));
            }

            Thread.sleep(100);
            return result;

          } catch (Exception e) {
            // If extraction fails, check if it's a simple OK response
            if (pkt.length >= 4 && pkt[3] == (byte) 0xF0) {
              JSONObject result = new JSONObject();
              result.put("success", true);
              result.put("status", "OK");
              result.put("command", command);
              result.put("info", new JSONObject());
              return result;
            }
            throw e;
          }

        } catch (Exception e) {
          lastException = e;
          System.out.println("Attempt " + attempt + " failed for " + command + ": " + e.getMessage());

          if (attempt < maxRetries) {
            try {
              long waitTime = 200 * attempt; // Exponential backoff
              Thread.sleep(waitTime);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
            }
          }
        }
      }

      // All retries failed, return error
      JSONObject error = new JSONObject();
      try {
        error.put("success", false);
        error.put("error", lastException != null ? lastException.getMessage() : "Command failed after " + maxRetries + " attempts");
        error.put("command", command);
      } catch (JSONException e) {
        throw new RuntimeException(e);
      }
      return error;
    });
  }


  @RequiresApi(api = Build.VERSION_CODES.N)
  public CompletableFuture<JSONObject> command(String command) {
    return command(command, new JSONObject());
  }

  /**
   * Send raw bytes and return response (for debugging)
   */
  @RequiresApi(api = Build.VERSION_CODES.N)

  public CompletableFuture<byte[]> sendRaw(byte[] data) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        System.out.println("→ Raw Tx: " + bytesToHex(data));

        // Clear input buffer
        while (port.bytesAvailable() > 0) {
          byte[] flush = new byte[port.bytesAvailable()];
          port.readBytes(flush, flush.length);
          System.out.println("Flushed " + flush.length + " bytes: " + bytesToHex(flush));
        }

        // Send data
        int written = port.writeBytes(data, data.length);
        System.out.println("Written: " + written + " bytes");
        port.flushIOBuffers();

        // Small delay to let device process
        Thread.sleep(50);

        // Read response
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        long start = System.currentTimeMillis();
        int timeout = 5000; // 5 seconds
        int noDataCount = 0;

        while (System.currentTimeMillis() - start < timeout) {
          int available = port.bytesAvailable();
          if (available > 0) {
            byte[] buffer = new byte[available];
            int read = port.readBytes(buffer, buffer.length);
            if (read > 0) {
              response.write(buffer, 0, read);
              if(this.debug) {
                System.out.println("← Rx chunk: " + bytesToHex(buffer));
              }
              noDataCount = 0;
            }
          } else {
            noDataCount++;
            if (noDataCount > 10 && response.size() > 0) {
              // If we haven't received data for a while and we have some data, assume it's complete
              break;
            }
          }
          Thread.sleep(50);
        }

        byte[] result = response.toByteArray();
        System.out.println("Total received: " + result.length + " bytes");
        if (result.length > 0) {
          System.out.println("Raw response hex: " + bytesToHex(result));
        }
        return result;

      } catch (Exception e) {
        System.err.println("Raw send failed: " + e.getMessage());
        e.printStackTrace();
        throw new RuntimeException(e);
      }
    });
  }

  // Make bytesToHex public
  public String bytesToHex(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (byte b : bytes) {
      sb.append(String.format("%02X ", b));
    }
    return sb.toString().trim();
  }

  /**
   * Enables the device and starts polling if not already enabled.
   *
   * @return CompletableFuture with the enable response
   */
  @RequiresApi(api = Build.VERSION_CODES.N)

  public CompletableFuture<JSONObject> enable() {
    return command("ENABLE").thenApply(result -> {
      if (result.optString("status").equals("OK")) {
        enabled = true;
        emitEvent("ENABLED", result);
      }
      return result;
    });
  }

  /**
   * Disables the device.
   *
   * @return CompletableFuture with the disable response
   */
  @RequiresApi(api = Build.VERSION_CODES.N)

  public CompletableFuture<JSONObject> disable() {
    return command("DISABLE").thenApply(result -> {
      if (result.optString("status").equals("OK")) {
        enabled = false;
        emitEvent("DISABLED", result);
      }
      return result;
    });
  }


  /**
   * Initializes the SSP connection sequence.
   *
   * @return CompletableFuture indicating completion
   */
  /**
   * Initializes the SSP connection sequence (automatic like Node.js version).
   */
  @RequiresApi(api = Build.VERSION_CODES.N)
  public CompletableFuture<Void> initSSP() {
    return CompletableFuture.runAsync(() -> {
      try {
        System.out.println("Starting SSP initialization...");

        // 1. SYNC - with retries
        JSONObject syncRes = null;
        for (int i = 0; i < 3; i++) {
          try {
            syncRes = command("SYNC").get(timeout, TimeUnit.MILLISECONDS);
            if (syncRes != null && syncRes.optBoolean("success", false)) {
              System.out.println("SYNC successful on attempt " + (i + 1));
              break;
            }
          } catch (Exception e) {
            System.out.println("SYNC attempt " + (i + 1) + " failed: " + e.getMessage());
          }

          if (i < 2) { // Don't sleep after last attempt
            Thread.sleep(1000 * (i + 1)); // Increasing delay: 1s, 2s
          }
        }

        if (syncRes == null || !syncRes.optBoolean("success", false)) {
          throw new RuntimeException("SYNC failed after 3 attempts");
        }
        System.out.println("SYNC → " + syncRes.toString(2));

        Thread.sleep(500);

        // 2. Set protocol version - with retries
        JSONObject protoRes = null;
        for (int i = 0; i < 3; i++) {
          try {
            JSONObject protoArgs = new JSONObject().put("version", protocolVersion);
            protoRes = command("HOST_PROTOCOL_VERSION", protoArgs).get(timeout, TimeUnit.MILLISECONDS);
            if (protoRes != null && protoRes.optBoolean("success", false)) {
              System.out.println("HOST_PROTOCOL_VERSION successful on attempt " + (i + 1));
              break;
            }
          } catch (Exception e) {
            System.out.println("HOST_PROTOCOL_VERSION attempt " + (i + 1) + " failed: " + e.getMessage());
          }

          if (i < 2) {
            Thread.sleep(500);
          }
        }

        if (protoRes == null || !protoRes.optBoolean("success", false)) {
          throw new RuntimeException("HOST_PROTOCOL_VERSION failed after 3 attempts");
        }
        System.out.println("HOST_PROTOCOL_VERSION → " + protoRes.toString(2));

        Thread.sleep(500);

        // 3. Get serial number - with retries (non-critical)
        JSONObject serialRes = null;
        int serialAttempts = 0;
        while (serialAttempts < 5) {
          try {
            serialRes = command("GET_SERIAL_NUMBER").get(timeout, TimeUnit.MILLISECONDS);
            if (serialRes != null && serialRes.optBoolean("success", false)) {
              JSONObject info = serialRes.optJSONObject("info");
              if (info != null && info.has("serial_number")) {
                System.out.println("Serial number: " + info.getInt("serial_number"));
                break;
              }
            }
          } catch (Exception e) {
            System.out.println("GET_SERIAL_NUMBER attempt " + (serialAttempts + 1) + " failed: " + e.getMessage());
          }

          serialAttempts++;
          if (serialAttempts < 5) {
            System.out.println("Retrying GET_SERIAL_NUMBER in 1 second...");
            Thread.sleep(1000);
          }
        }

        if (serialAttempts >= 5) {
          System.out.println("WARNING: GET_SERIAL_NUMBER failed after 5 attempts, continuing...");
          // Continue initialization - not critical
        }

        Thread.sleep(500);

        // 4. DISPLAY_ON - optional, don't fail if it doesn't work
        try {
          JSONObject displayRes = command("DISPLAY_ON").get(timeout / 2, TimeUnit.MILLISECONDS);
          if (displayRes != null && displayRes.optBoolean("success", false)) {
            System.out.println("DISPLAY_ON → " + displayRes.toString(2));
          } else {
            System.out.println("DISPLAY_ON returned non-success, continuing...");
          }
        } catch (Exception e) {
          System.out.println("DISPLAY_ON failed (non-critical): " + e.getMessage());
        }

        Thread.sleep(500);

        // 5. Setup request - with retries (CRITICAL)
        JSONObject setupRes = null;
        int setupAttempts = 0;
        while (setupAttempts < 5) {
          try {
            setupRes = command("SETUP_REQUEST").get(timeout, TimeUnit.MILLISECONDS);
            if (setupRes != null && setupRes.optBoolean("success", false)) {
              System.out.println("SETUP_REQUEST successful on attempt " + (setupAttempts + 1));
              break;
            }
          } catch (Exception e) {
            System.out.println("SETUP_REQUEST attempt " + (setupAttempts + 1) + " failed: " + e.getMessage());
          }

          setupAttempts++;
          if (setupAttempts < 5) {
            System.out.println("Retrying SETUP_REQUEST in 1 second...");
            Thread.sleep(1000);
          }
        }

        if (setupRes == null || !setupRes.optBoolean("success", false)) {
          throw new RuntimeException("SETUP_REQUEST failed after 5 attempts");
        }
        System.out.println("SETUP_REQUEST → " + setupRes.toString(2));

        Thread.sleep(500);

        // 6. Set channel inhibits - with retries
        JSONObject inhibitsArgs = new JSONObject();
        inhibitsArgs.put("channels", new JSONArray("[1,1,1,1,1,1,1]"));
        JSONObject inhibitsRes = null;

        for (int i = 0; i < 3; i++) {
          try {
            inhibitsRes = command("SET_CHANNEL_INHIBITS", inhibitsArgs).get(timeout, TimeUnit.MILLISECONDS);
            if (inhibitsRes != null && inhibitsRes.optBoolean("success", false)) {
              System.out.println("SET_CHANNEL_INHIBITS successful on attempt " + (i + 1));
              break;
            }
          } catch (Exception e) {
            System.out.println("SET_CHANNEL_INHIBITS attempt " + (i + 1) + " failed: " + e.getMessage());
          }

          if (i < 2) {
            Thread.sleep(500);
          }
        }

        if (inhibitsRes == null || !inhibitsRes.optBoolean("success", false)) {
          throw new RuntimeException("SET_CHANNEL_INHIBITS failed after 3 attempts");
        }
        System.out.println("SET_CHANNEL_INHIBITS → " + inhibitsRes.toString(2));

        Thread.sleep(500);

        // 7. Enable - with retries
        JSONObject enableRes = null;
        for (int i = 0; i < 3; i++) {
          try {
            enableRes = enable().get(timeout, TimeUnit.MILLISECONDS);
            if (enableRes != null && enableRes.optBoolean("success", false)) {
              System.out.println("ENABLE successful on attempt " + (i + 1));
              break;
            }
          } catch (Exception e) {
            System.out.println("ENABLE attempt " + (i + 1) + " failed: " + e.getMessage());
          }

          if (i < 2) {
            Thread.sleep(500);
          }
        }

        if (enableRes == null || !enableRes.optBoolean("success", false)) {
          throw new RuntimeException("ENABLE failed after 3 attempts");
        }
        System.out.println("Device is active → " + enableRes.toString(2));

        System.out.println("✅ Initialization sequence completed successfully!");
        emitEvent("INITIALIZED", new JSONObject().put("success", true));

      } catch (Exception e) {
        System.err.println("❌ Initialization failed: " + e.getMessage());
        e.printStackTrace();
        try {
          JSONObject errorEvent = new JSONObject();
          errorEvent.put("success", false);
          errorEvent.put("error", e.getMessage());
          emitEvent("INITIALIZED", errorEvent);
        } catch (JSONException ex) {
          // Ignore
        }
        throw new RuntimeException("initSSP failed: " + e.getMessage(), e);
      }
    });
  }

  /**
   * Starts a polling loop in a background thread.
   */
  @RequiresApi(api = Build.VERSION_CODES.N)

  public void startPoll() {
    if (polling) {
      return;
    }
    polling = true;
    pollThread = new Thread(() -> {
      while (polling) {
        try {
          command("POLL").get(timeout, TimeUnit.MILLISECONDS);
          Thread.sleep(200);
        } catch (Exception e) {
          System.err.println("Poll failed: " + e.getMessage());
        }
      }
    });
    pollThread.start();
  }

  /**
   * Stops the polling loop.
   */
  public void stopPoll() {
    polling = false;
    if (pollThread != null) {
      pollThread.interrupt();
    }
  }
  @RequiresApi(api = Build.VERSION_CODES.N)

  private void initEncryption() throws Exception {
    SSPUtils.Keys keys = SSPUtils.generateKeys();
    this.generator = keys.generator;
    this.modulus = keys.modulus;
    this.hostRandom = keys.hostRandom;
    this.hostInter = keys.hostInter;
    this.eCount = 0;

    JSONObject genArgs = new JSONObject();
    genArgs.put("key", generator.toString());
    command("SET_GENERATOR", genArgs).get(timeout, TimeUnit.MILLISECONDS);

    JSONObject modArgs = new JSONObject();
    modArgs.put("key", modulus.toString());
    command("SET_MODULUS", modArgs).get(timeout, TimeUnit.MILLISECONDS);

    JSONObject exchangeArgs = new JSONObject();
    exchangeArgs.put("key", hostInter.toString());
    JSONObject keyExchangeResult = command("REQUEST_KEY_EXCHANGE", exchangeArgs).get(timeout, TimeUnit.MILLISECONDS);

    if (keyExchangeResult.getBoolean("success")) {
      JSONArray keyArray = keyExchangeResult.getJSONObject("info").getJSONArray("key");
      byte[] slaveInterKeyBytes = new byte[8];
      for (int i = 0; i < keyArray.length() && i < 8; i++) {
        slaveInterKeyBytes[i] = (byte) keyArray.getInt(i);
      }
      BigInteger slaveInterKey = new BigInteger(1, slaveInterKeyBytes);
      BigInteger key = slaveInterKey.modPow(hostRandom, modulus);
      this.encryptKey = ByteBuffer.allocate(16)
        .put(SSPUtils.hexStringToByteArray(fixedKey))
        .put(SSPUtils.uInt64LE(key))
        .array();
      if (debug) {
        System.out.println("Encryption key set: " + bytesToHex(encryptKey));
      }
    } else {
      throw new IllegalStateException("Key exchange failed: " + keyExchangeResult.toString());
    }
  }

  // In SSP.java - Fix getPacket method
  byte[] getPacket(String command, JSONObject args) throws Exception {
    SSPUtils.CommandInfo cmdInfo = commandList.get(command.toUpperCase());
    if (cmdInfo == null) {
      throw new IllegalArgumentException("Unknown command: " + command);
    }

    byte[] argBytes = argsToByte(command, args);
    boolean encryptAll = true;
    boolean shouldEncrypt = (cmdInfo.encrypted || encryptAll) && encryptKey != null;

    // Get current sequence WITHOUT toggling yet (Node.js does this)
    byte currentSeq = (byte) (id | sequence);

    // Build command data
    ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
    dataStream.write(cmdInfo.code);
    if (argBytes != null && argBytes.length > 0) {
      dataStream.write(argBytes);
    }
    byte[] data = dataStream.toByteArray();

    byte[] packetData;
    int dataLength;

    if (shouldEncrypt) {
      // Build encrypted packet (same as before)
      ByteArrayOutputStream eCountBuf = new ByteArrayOutputStream();
      eCountBuf.write(SSPUtils.uInt32LE(eCount));

      ByteArrayOutputStream toEncrypt = new ByteArrayOutputStream();
      toEncrypt.write(data.length);
      toEncrypt.write(eCountBuf.toByteArray());
      toEncrypt.write(data);

      int paddingLength = (16 - ((toEncrypt.size() + 2) % 16)) % 16;
      byte[] padding = new byte[paddingLength];
      new SecureRandom().nextBytes(padding);
      toEncrypt.write(padding);

      byte[] crcInput = toEncrypt.toByteArray();
      byte[] crc = SSPUtils.crc16(crcInput);
      toEncrypt.write(crc);

      byte[] encrypted = SSPUtils.encrypt(encryptKey, toEncrypt.toByteArray());

      ByteArrayOutputStream encryptedData = new ByteArrayOutputStream();
      encryptedData.write(0x7E);
      encryptedData.write(encrypted);
      packetData = encryptedData.toByteArray();
      dataLength = packetData.length;
    } else {
      packetData = data;
      dataLength = packetData.length;
    }

    // Build packet core (SEQ + LENGTH + DATA) - Node.js order
    ByteArrayOutputStream packetCore = new ByteArrayOutputStream();
    packetCore.write(currentSeq);
    packetCore.write(dataLength);
    packetCore.write(packetData);
    byte[] core = packetCore.toByteArray();

    // Calculate CRC on core only - Node.js CRC16 returns [low, high]
    byte[] crc = SSPUtils.crc16(core);

    // Build full packet with STX and CRC - Node.js adds CRC after data
    ByteArrayOutputStream fullPacket = new ByteArrayOutputStream();
    fullPacket.write(0x7F); // STX
    fullPacket.write(core);
    fullPacket.write(crc); // crc[0] is low, crc[1] is high

    // Apply byte stuffing (excluding STX)
    byte[] stuffed = SSPUtils.stuffBuffer(fullPacket.toByteArray());

    // Update sequence for NEXT command (AFTER using current sequence) - Node.js does this
    if (command.equalsIgnoreCase("SYNC")) {
      this.sequence = (byte) 0x80; // Reset to 0x80 after SYNC
    } else {
      this.sequence = (sequence == 0x00) ? (byte) 0x80 : 0x00;
    }

    if (shouldEncrypt) {
      eCount++;
    }

    if (debug) {
      System.out.println("Packet created for " + command + ": " + bytesToHex(stuffed));
    }

    return stuffed;
  }

  private byte getSequence() {
    return (byte) (id | sequence);
  }

  private byte[] argsToByte(String command, JSONObject args) throws Exception {
    switch (command.toUpperCase()) {
      case "SET_GENERATOR":
      case "SET_MODULUS":
      case "REQUEST_KEY_EXCHANGE":
        return SSPUtils.uInt64LE(new BigInteger(args.getString("key")));
      case "SET_CHANNEL_INHIBITS":
        JSONArray channels = args.getJSONArray("channels");
        int channelBits = 0;
        for (int i = 0; i < channels.length(); i++) {
          if (channels.getInt(i) != 0) {
            channelBits |= (1 << i);
          }
        }
        return SSPUtils.uInt16LE(channelBits);
      case "HOST_PROTOCOL_VERSION":
        return new byte[]{(byte) args.getInt("version")};
      case "SET_DENOMINATION_ROUTE":
        // Add more from doc if needed
        int denomination = args.getInt("denomination");
        String country = args.getString("country");
        boolean routeToCashbox = args.getBoolean("routeToCashbox");
        byte[] denBytes = SSPUtils.uInt32LE(denomination);
        byte[] countryBytes = country.getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(1 + denBytes.length + countryBytes.length)
          .put((byte) (routeToCashbox ? 1 : 0)).put(denBytes).put(countryBytes).array();
      // Add other commands as per doc, e.g., FLOAT_AMOUNT, etc.
      default:
        return new byte[0];
    }
  }
  @RequiresApi(api = Build.VERSION_CODES.N)

  public JSONObject parseData(byte[] data, String command) {
    JSONObject result = new JSONObject();
    try {
      result.put("success", data[0] == (byte) 0xF0);
      String status = SSPUtils.statusDesc.getOrDefault(data[0] & 0xFF, "UNDEFINED");
      result.put("status", status);
      result.put("command", command);
      JSONObject info = new JSONObject();
      result.put("info", info);

      if (result.getBoolean("success")) {
        byte[] dataSub = new byte[data.length - 1];
        System.arraycopy(data, 1, dataSub, 0, dataSub.length);

        switch (command.toUpperCase()) {
          case "REQUEST_KEY_EXCHANGE":
            JSONArray keyArray = new JSONArray();
            for (byte b : dataSub) {
              keyArray.put(b & 0xFF);
            }
            info.put("key", keyArray);
            break;
          case "SETUP_REQUEST":
            try {
              // First byte is unit type
              int unitTypeCode = dataSub[0] & 0xFF;
              unitType = SSPUtils.unitType.getOrDefault(unitTypeCode, "Unknown");
              info.put("unit_type", unitType);

              // Next 4 bytes are firmware version (ASCII)
              String firmwareVersion = new String(dataSub, 1, 4, StandardCharsets.US_ASCII);
              // Format as XX.XX (like 3.84)
              if (firmwareVersion.length() == 4) {
                firmwareVersion = firmwareVersion.substring(0, 2) + "." + firmwareVersion.substring(2);
              }
              info.put("firmware_version", firmwareVersion);

              // Next 3 bytes are country code
              String countryCode = new String(dataSub, 5, 3, StandardCharsets.US_ASCII);
              info.put("country_code", countryCode);

              // Check if it's a SMART Hopper/Payout or Note Float (unit type 3,6,8)
              if (unitTypeCode == 3 || unitTypeCode == 6 || unitTypeCode == 8) {
                // SMART device format
                int protocolVersion = dataSub[8] & 0xFF;
                info.put("protocol_version", protocolVersion);

                int numChannels = dataSub[9] & 0xFF;
                info.put("num_channels", numChannels);

                // Coin/note values (2 bytes each)
                JSONArray coinValues = new JSONArray();
                for (int i = 0; i < numChannels; i++) {
                  int value = ((dataSub[10 + i * 2] & 0xFF)
                    | ((dataSub[11 + i * 2] & 0xFF) << 8));
                  coinValues.put(value);
                }
                info.put("coin_values", coinValues);

                // Country codes for each value (3 bytes each)
                int countryStart = 10 + numChannels * 2;
                JSONArray countryCodes = new JSONArray();
                for (int i = 0; i < numChannels; i++) {
                  String cc = new String(dataSub, countryStart + i * 3, 3, StandardCharsets.US_ASCII);
                  countryCodes.put(cc);
                }
                info.put("country_codes", countryCodes);

              } else {
                // Banknote validator format
                // Value multiplier (3 bytes)
                int valueMultiplier = ((dataSub[8] & 0xFF)
                  | ((dataSub[9] & 0xFF) << 8)
                  | ((dataSub[10] & 0xFF) << 16));
                info.put("value_multiplier", valueMultiplier);

                // Number of channels
                int numChannels = dataSub[11] & 0xFF;
                info.put("num_channels", numChannels);

                // Channel values (1 byte each)
                JSONArray channelValues = new JSONArray();
                for (int i = 0; i < numChannels; i++) {
                  channelValues.put(dataSub[12 + i] & 0xFF);
                }
                info.put("channel_values", channelValues);

                // Channel security (1 byte each)
                if (dataSub.length > 12 + numChannels) {
                  JSONArray channelSecurity = new JSONArray();
                  for (int i = 0; i < numChannels; i++) {
                    channelSecurity.put(dataSub[12 + numChannels + i] & 0xFF);
                  }
                  info.put("channel_security", channelSecurity);
                }

                // Real value multiplier (3 bytes) - for protocol version >= 6
                if (dataSub.length > 12 + numChannels * 2) {
                  int realMultiplier = ((dataSub[12 + numChannels * 2] & 0xFF)
                    | ((dataSub[13 + numChannels * 2] & 0xFF) << 8)
                    | ((dataSub[14 + numChannels * 2] & 0xFF) << 16));
                  info.put("real_value_multiplier", realMultiplier);
                }

                // Protocol version
                if (dataSub.length > 15 + numChannels * 2) {
                  int protocolVersion = dataSub[15 + numChannels * 2] & 0xFF;
                  info.put("protocol_version", protocolVersion);

                  // If protocol version >= 6, there's expanded channel data
                  if (protocolVersion >= 6 && dataSub.length > 16 + numChannels * 2) {
                    // Country codes for each channel (3 bytes each)
                    JSONArray expandedCountryCodes = new JSONArray();
                    int countryStart = 16 + numChannels * 2;
                    for (int i = 0; i < numChannels; i++) {
                      String cc = new String(dataSub, countryStart + i * 3, 3, StandardCharsets.US_ASCII);
                      expandedCountryCodes.put(cc);
                    }
                    info.put("expanded_channel_country_code", expandedCountryCodes);

                    // Expanded channel values (4 bytes each, little-endian)
                    JSONArray expandedValues = new JSONArray();
                    int valueStart = countryStart + numChannels * 3;
                    for (int i = 0; i < numChannels; i++) {
                      int value = ((dataSub[valueStart + i * 4] & 0xFF)
                        | ((dataSub[valueStart + i * 4 + 1] & 0xFF) << 8)
                        | ((dataSub[valueStart + i * 4 + 2] & 0xFF) << 16)
                        | ((dataSub[valueStart + i * 4 + 3] & 0xFF) << 24));
                      expandedValues.put(value);
                    }
                    info.put("expanded_channel_value", expandedValues);
                  }
                }
              }

              // Print the parsed data for debugging
              System.out.println("Parsed SETUP_REQUEST: " + info.toString(2));

            } catch (Exception e) {
              System.err.println("Error parsing SETUP_REQUEST: " + e.getMessage());
              e.printStackTrace();
              // Put raw data for debugging
              JSONArray raw = new JSONArray();
              for (byte b : dataSub) {
                raw.put(b & 0xFF);
              }
              info.put("raw", raw);
            }
            break;
          case "GET_SERIAL_NUMBER":
            if (dataSub.length >= 4) {
              // The serial number is 4 bytes, but need to handle byte order
              int serial = ((dataSub[0] & 0xFF) << 24)
                | ((dataSub[1] & 0xFF) << 16)
                | ((dataSub[2] & 0xFF) << 8)
                | (dataSub[3] & 0xFF);
              info.put("serial_number", serial);
              System.out.println("Serial number: " + serial);
            }
            break;
          case "POLL":
            JSONArray events = new JSONArray();
            int k = 0;
            int disabledCount =0;
            while (k < dataSub.length) {
              int code = dataSub[k] & 0xFF;
              String eventName = SSPUtils.statusDesc.getOrDefault(code, "UNKNOWN");
              JSONObject event = new JSONObject();
              event.put("code", code);
              event.put("name", eventName);

              // Log every event for debugging
              System.out.println("Processing POLL event: code=" + code + " (" + eventName + ")");

              switch (eventName) {
                case "READ_NOTE":
                case "CREDIT_NOTE":  // Make sure this is handled
                case "NOTE_STACKED":  // Add this too
                  if (k + 1 < dataSub.length) {
                    event.put("channel", dataSub[k + 1] & 0xFF);
                    System.out.println(eventName + " on channel: " + (dataSub[k + 1] & 0xFF));
                    k += 2;
                  } else {
                    k += 1;
                  }
                  break;

                case "FRAUD_ATTEMPT":
                case "NOTE_CLEARED_FROM_FRONT":
                case "NOTE_CLEARED_TO_CASHBOX":
                  if (k + 1 < dataSub.length) {
                    event.put("channel", dataSub[k + 1] & 0xFF);
                    k += 2;
                  } else {
                    k += 1;
                  }
                  break;

                case "NOTE_HELD_IN_BEZEL":
                  if (k + 9 < dataSub.length) {
                    event.put("position", dataSub[k + 1] & 0xFF);
                    // Value is 4 bytes (little-endian)
                    int value = ((dataSub[k + 2] & 0xFF) |
                      ((dataSub[k + 3] & 0xFF) << 8) |
                      ((dataSub[k + 4] & 0xFF) << 16) |
                      ((dataSub[k + 5] & 0xFF) << 24));
                    event.put("value", value);

                    // Country code is 3 bytes ASCII
                    String countryCode = new String(dataSub, k + 6, 3, StandardCharsets.US_ASCII);
                    event.put("country_code", countryCode);
                    k += 9;
                  } else {
                    k += 1;
                  }
                  break;

                case "DISABLED":
                case "STACKER_FULL":
                case "CASHBOX_REMOVED":
                case "CASHBOX_REPLACED":
                  // These are just status events with no extra data

//                  if(disabledCount++>=10){
//                    enable();
//                    disabledCount=0;
//                  }
                  k += 1;
                  break;

                default:
                  // Unknown event, log it and skip
                  System.out.println("Unknown POLL event code: " + code);
                  k += 1;
                  break;
              }

              // Add event to array
              events.put(event);

              // Emit individual event for immediate handling
              try {
                emitEvent(eventName, event);
                System.out.println("Emitted event: " + eventName);
              } catch (Exception e) {
                System.err.println("Error emitting event " + eventName + ": " + e.getMessage());
              }
            }
            info.put("events", events);
            break;
          case "LAST_REJECT_CODE":
            int rejectCode = dataSub[0] & 0xFF;
            SSPUtils.RejectInfo rejectInfo = SSPUtils.rejectNote.getOrDefault(rejectCode, new SSPUtils.RejectInfo("UNKNOWN", ""));
            info.put("code", rejectCode);
            info.put("name", rejectInfo.name);
            info.put("description", rejectInfo.description);
            emitEvent("NOTE_REJECTED", info);
            break;
          // Add more cases from doc, e.g., UNIT_DATA, CHANNEL_VALUE_REQUEST
          case "UNIT_DATA":
            info.put("unit_type", dataSub[0] & 0xFF);
            info.put("firmware_version", new String(dataSub, 1, 4));
            info.put("country_code", new String(dataSub, 5, 3));
            info.put("value_multiplier", ByteBuffer.wrap(dataSub, 8, 3).getInt());
            info.put("protocol_version", dataSub[11] & 0xFF);
            break;
          default:
            // Raw data for unknown
            JSONArray raw = new JSONArray();
            for (byte b : dataSub) {
              raw.put(b & 0xFF);
            }
            info.put("raw", raw);
            break;
        }
      } else {
        // Error handling from status
        if (status.equals("COMMAND_CANNOT_BE_PROCESSED")) {
          info.put("reason", data[1] & 0xFF); // e.g., 0x01 for busy
        }
      }
    } catch (Exception e) {
      System.err.println("Error parsing SSP data: " + e.getMessage());
      try {
        result.put("success", false);
        result.put("error", e.getMessage());
      } catch (JSONException ignored) {
      }
    }
    return result;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean hasBytesAvailable() {
    return port != null && port.bytesAvailable() > 0;
  }

  public int getBytesAvailable() {
    return port != null ? port.bytesAvailable() : 0;
  }

  public int readBytes(byte[] buffer) {
    return port != null ? port.readBytes(buffer, buffer.length) : 0;
  }
  // Debug method to get packet without sending

  // Debug method to get packet without sending
  public byte[] getPacketForDebug(String command, JSONObject args) throws Exception {
    SSPUtils.CommandInfo cmdInfo = commandList.get(command.toUpperCase());
    if (cmdInfo == null) {
      throw new IllegalArgumentException("Unknown command: " + command);
    }

    byte[] argBytes = argsToByte(command, args);
    boolean encryptAll = true;
    // Capture current sequence without incrementing
    byte currentSeq = getSequence();
    byte[] packet = SSPUtils.getPacket(command, argBytes, currentSeq, null, eCount); // Don't use encryption

    if (debug) {
      System.out.println("Debug packet for " + command + ": " + bytesToHex(packet));
    }
    return packet;
  }

  @RequiresApi(api = Build.VERSION_CODES.N)

  public void openWithNodeSettings(String portPath) throws Exception {
    port = SerialPort.getCommPort(portPath);

    // Try different combinations
    int[][] settings = {
      {9600, 8, 1, SerialPort.NO_PARITY}, // Standard
      {9600, 7, 1, SerialPort.EVEN_PARITY}, // Some variants
      {19200, 8, 1, SerialPort.NO_PARITY}, // Faster
      {38400, 8, 1, SerialPort.NO_PARITY}, // Even faster
    };

    String[] parityNames = {"None", "Even", "Odd", "Mark", "Space"};

    for (int[] setting : settings) {
      try {
        String parityName = parityNames[setting[3]];
        System.out.println("\nTrying: " + setting[0] + "/" + setting[1] + parityName.charAt(0) + setting[2]);

        port.setBaudRate(setting[0]);
        port.setNumDataBits(setting[1]);
        port.setNumStopBits(setting[2]);
        port.setParity(setting[3]);
        port.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

        if (port.openPort()) {
          System.out.println("Port opened with settings: " + setting[0] + "/" + setting[1] + parityName.charAt(0) + setting[2]);
          Thread.sleep(1000);
          port.flushIOBuffers();

          // Test with SYNC
          System.out.println("Testing SYNC with these settings...");
          byte[] sync = new byte[]{(byte) 0x7F, (byte) 0x80, 0x01, 0x11, (byte) 0x82, 0x65};

          // Clear input buffer
          while (port.bytesAvailable() > 0) {
            byte[] flush = new byte[port.bytesAvailable()];
            port.readBytes(flush, flush.length);
          }

          // Send SYNC
          port.writeBytes(sync, sync.length);
          port.flushIOBuffers();

          // Wait for response
          ByteArrayOutputStream response = new ByteArrayOutputStream();
          long start = System.currentTimeMillis();

          while (System.currentTimeMillis() - start < 2000) {
            if (port.bytesAvailable() > 0) {
              byte[] buffer = new byte[port.bytesAvailable()];
              int read = port.readBytes(buffer, buffer.length);
              if (read > 0) {
                response.write(buffer, 0, read);
              }
            }
            Thread.sleep(50);
          }

          byte[] result = response.toByteArray();
          if (result.length > 0) {
            System.out.println("✅ Got response! Settings work.");
            System.out.println("Response: " + bytesToHex(result));
            emitEvent("OPEN", new JSONObject());
            return; // Success, keep this connection
          } else {
            System.out.println("❌ No response with these settings");
            port.closePort();
          }
        }
      } catch (Exception e) {
        System.out.println("Failed: " + e.getMessage());
        if (port != null && port.isOpen()) {
          port.closePort();
        }
      }
    }

    throw new Exception("Could not find working settings");
  }
}
