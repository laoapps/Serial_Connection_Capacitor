package com.laoapps.plugins.serialconnectioncapacitor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.serialport.SerialPort;
import android.util.Log;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SSP {
    private static final String TAG = "SSP";

    // Configuration (matches KiosServer)
    private final byte id = 0x00;
    private final boolean debug = false;
    private final int timeout = 3000; // milliseconds
    private final String fixedKey = "0123456701234567";

    // State
    public byte[] encryptKey = null;
    public int eCount = 0;
    private byte sequence = (byte) 0x80;
    private boolean enabled = false;
    private int protocolVersion = 6;
    private String unitType = null;
    private SerialPort port;
    private SSPParser parser = new SSPParser();

    // Encryption keys
    private BigInteger generator;
    private BigInteger modulus;
    private BigInteger hostRandom;
    private BigInteger hostInter;

    // Event listeners
    private final Map<String, Consumer<JSONObject>> eventListeners = new HashMap<>();

    // Command definitions
    private final Map<String, SSPUtils.CommandInfo> commandList = SSPUtils.commands;

    // Constructor
    public SSP() {
        // No additional initialization beyond defaults
    }

    /**
     * Opens the serial port and sets up the SSP connection.
     *
     * @param portPath The serial port path (e.g., "/dev/ttyS1")
     * @throws Exception If port opening fails
     */
    public void open(String portPath) throws Exception {
        port = new SerialPort(portPath, 9600, 0, 8, 2, "none", 0);
        emitEvent("OPEN", new JSONObject());
    }

    /**
     * Closes the serial port.
     *
     * @throws Exception If closing fails
     */
    public void close() throws Exception {
        if (port != null) {
            port.close();
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
    private void emitEvent(String eventName, JSONObject data) {
        Consumer<JSONObject> listener = eventListeners.get(eventName.toUpperCase());
        if (listener != null) {
            try {
                listener.accept(data);
            } catch (Exception e) {
                Log.e(TAG, "Error in event listener for " + eventName + ": " + e.getMessage());
            }
        }
        if (debug) Log.d(TAG, eventName + ": " + data.toString());
    }

    /**
     * Executes a command and returns a CompletableFuture with the response.
     *
     * @param command The command name
     * @param args Command arguments (optional)
     * @return CompletableFuture with the parsed response
     */
    public CompletableFuture<JSONObject> command(String command, JSONObject args) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] packet = getPacket(command, args);
                port.getOutputStream().write(packet);
                port.getOutputStream().flush();

                byte[] buffer = new byte[1024];
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < timeout) {
                    int available = port.getInputStream().available();
                    if (available > 0) {
                        int len = port.getInputStream().read(buffer, 0, Math.min(available, buffer.length));
                        List<byte[]> packets = parser.parse(buffer);
                        for (byte[] pkt : packets) {
                            byte[] data = SSPUtils.extractPacketData(pkt, encryptKey, eCount);
                            JSONObject result = parseData(data, command);
                            if (debug) Log.d(TAG, "Response for " + command + ": " + result.toString());
                            return result;
                        }
                    }
                    Thread.sleep(10);
                }
                throw new RuntimeException("Timeout waiting for response to " + command);
            } catch (Exception e) {
                Log.e(TAG, "Command " + command + " failed: " + e.getMessage());
                JSONObject error = new JSONObject();
                try {
                    error.put("success", false);
                    error.put("error", e.getMessage());
                } catch (Exception je) {
                    Log.e(TAG, "Error creating error JSON: " + je.getMessage());
                }
                return error;
            }
        });
    }

    public CompletableFuture<JSONObject> command(String command) {
        return command(command, null);
    }

    /**
     * Enables the device and starts polling if not already enabled.
     *
     * @return CompletableFuture with the enable response
     */
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
     * Initializes the SSP connection sequence.
     *
     * @return CompletableFuture indicating completion
     */
    public CompletableFuture<Void> initSSP() {
        return CompletableFuture.runAsync(() -> {
            try {
                command("SYNC").thenAccept(result -> {
                            if (debug) Log.d(TAG, "SYNC: " + result.toString());
                        }).thenCompose(v -> {
                            try {
                                return command("HOST_PROTOCOL_VERSION", new JSONObject().put("version", 6));
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .thenCompose(v -> {
                            try {
                                initEncryption(this.port);
                                return CompletableFuture.completedFuture(null);
                            } catch (Exception e) {
                                throw new RuntimeException("Encryption init failed: " + e.getMessage());
                            }
                        })
                        .thenCompose(v -> command("GET_SERIAL_NUMBER"))
                        .thenAccept(result -> {
                            if (debug) Log.d(TAG, "SERIAL NUMBER: " + result.optJSONObject("info").optString("serial_number"));
                        })
                        .thenCompose(v -> command("RESET_COUNTERS"))
                        .thenAccept(result -> {
                            if (result.optString("status").equals("OK") && debug) {
                                Log.d(TAG, "RESET_COUNTERS: " + result.toString());
                            }
                        })
                        .thenCompose(v -> enable())
                        .thenAccept(result -> {
                            if (result.optString("status").equals("OK") && debug) {
                                Log.d(TAG, "enable request: " + result.optJSONObject("info").toString());
                            }
                        })
                        .thenCompose(v -> command("SETUP_REQUEST"))
                        .thenAccept(result -> {
                            if (result.optString("status").equals("OK") && debug) {
                                Log.d(TAG, "SETUP_REQUEST request: " + result.optJSONObject("info").toString());
                            }
                        })
                        .thenCompose(v -> {
                            JSONObject args = new JSONObject();
                            try {
                                args.put("channels", new JSONArray(new int[]{1, 1, 1, 1, 1, 1, 1}));
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            return command("SET_CHANNEL_INHIBITS", args);
                        })
                        .thenAccept(result -> {
                            if (result.optString("status").equals("OK") && debug) {
                                Log.d(TAG, "SET_CHANNEL_INHIBITS: " + result.optJSONObject("info").toString());
                            }
                        }).get(timeout, TimeUnit.MILLISECONDS); // Block with timeout
            } catch (Exception e) {
                Log.e(TAG, "initSSP failed: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    byte[] getPacket(String command, JSONObject args) throws Exception {
        SSPUtils.CommandInfo cmdInfo = commandList.get(command.toUpperCase());
        if (cmdInfo == null) throw new IllegalArgumentException("Unknown command: " + command);

        if (cmdInfo.encrypted && encryptKey == null) {
            throw new IllegalStateException("Command requires encryption but keys are not set");
        }

        byte seq = getSequence();
        byte[] argBytes = args != null ? argsToByte(command, args) : new byte[0];
        boolean encryptAll = true; // Matches KiosServer config
        byte[] packet = SSPUtils.getPacket(command, argBytes, seq, (cmdInfo.encrypted || encryptAll) ? encryptKey : null, eCount);

        sequence = sequence == 0x00 ? (byte) 0x80 : 0x00;
        if (encryptKey != null && (cmdInfo.encrypted || encryptAll)) eCount++;

        if (debug) Log.d(TAG, "Packet created for " + command + ": " + bytesToHex(packet));
        return packet;
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
                    if (channels.getInt(i) != 0) channelBits |= (1 << i);
                }
                return SSPUtils.uInt16LE(channelBits);
            case "HOST_PROTOCOL_VERSION":
                return new byte[]{(byte) args.getInt("version")};
            default:
                return new byte[0];
        }
    }

    public void initEncryption(SerialPort serialPort) throws Exception {
        SSPUtils.Keys keys = SSPUtils.generateKeys();
        this.generator = keys.generator;
        this.modulus = keys.modulus;
        this.hostRandom = keys.hostRandom;
        this.hostInter = keys.hostInter;
        this.eCount = 0;

        command("SET_GENERATOR", new JSONObject().put("key", generator.toString())).get(timeout, TimeUnit.MILLISECONDS);
        command("SET_MODULUS", new JSONObject().put("key", modulus.toString())).get(timeout, TimeUnit.MILLISECONDS);
        JSONObject keyExchangeResult = command("REQUEST_KEY_EXCHANGE", new JSONObject().put("key", hostInter.toString()))
                .get(timeout, TimeUnit.MILLISECONDS);

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
            if (debug) Log.d(TAG, "Encryption key set: " + bytesToHex(encryptKey));
        } else {
            throw new IllegalStateException("Key exchange failed: " + keyExchangeResult.toString());
        }
    }

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
                        for (byte b : dataSub) keyArray.put(b & 0xFF);
                        info.put("key", keyArray);
                        break;
                    case "SETUP_REQUEST":
                        unitType = SSPUtils.unitType.get(dataSub[0] & 0xFF);
                        String firmwareVersion = new String(dataSub, 1, 4, StandardCharsets.US_ASCII);
                        String countryCode = new String(dataSub, 5, 3, StandardCharsets.US_ASCII);
                        int protoVer = dataSub[8] & 0xFF;
                        info.put("unit_type", unitType);
                        info.put("firmware_version", firmwareVersion);
                        info.put("country_code", countryCode);
                        info.put("protocol_version", protoVer);
                        this.protocolVersion = protoVer;
                        // Add more parsing as needed
                        break;
                    case "GET_SERIAL_NUMBER":
                        info.put("serial_number", ByteBuffer.wrap(dataSub, 0, 4).getInt());
                        break;
                    case "POLL":
                        JSONArray events = new JSONArray();
                        int k = 0;
                        while (k < dataSub.length) {
                            int code = dataSub[k] & 0xFF;
                            String eventName = SSPUtils.statusDesc.getOrDefault(code, "UNKNOWN");
                            JSONObject event = new JSONObject();
                            event.put("code", code);
                            event.put("name", eventName);
                            switch (eventName) {
                                case "READ_NOTE":
                                case "CREDIT_NOTE":
                                case "NOTE_CLEARED_FROM_FRONT":
                                case "NOTE_CLEARED_TO_CASHBOX":
                                    event.put("channel", dataSub[k + 1] & 0xFF);
                                    k += 2;
                                    break;
                                case "FRAUD_ATTEMPT":
                                    if (protocolVersion >= 6 && (unitType.equals("SMART Payout") || unitType.equals("SMART Hopper"))) {
                                        int length = dataSub[k + 1] & 0xFF;
                                        JSONArray values = new JSONArray();
                                        for (int i = 0; i < length; i++) {
                                            JSONObject val = new JSONObject();
                                            val.put("value", ByteBuffer.wrap(dataSub, k + 2 + i * 7, 4).getInt());
                                            val.put("country_code", new String(dataSub, k + 6 + i * 7, 3));
                                            values.put(val);
                                        }
                                        event.put("value", values);
                                        k += 2 + length * 7;
                                    } else {
                                        event.put("channel", dataSub[k + 1] & 0xFF);
                                        k += 2;
                                    }
                                    break;
                                default:
                                    k += 1;
                                    break;
                            }
                            events.put(event);
                            emitEvent(eventName, event); // Emit event to listeners
                        }
                        info.put("events", events);
                        break;
                    case "LAST_REJECT_CODE":
                        int rejectCode = dataSub[0] & 0xFF;
                        SSPUtils.RejectInfo rejectInfo = SSPUtils.rejectNote.getOrDefault(rejectCode, new SSPUtils.RejectInfo("UNKNOWN", ""));
                        info.put("code", rejectCode);
                        info.put("name", rejectInfo.name);
                        info.put("description", rejectInfo.description);
                        break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing SSP data: " + e.getMessage());
        }
        return result;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}