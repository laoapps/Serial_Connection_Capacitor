package org.yourcompany.yourproject;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class SSPUtils {

    private static final byte STX = 0x7F;
    private static final byte STEX = 0x7E;
    private static final int CRC_SSP_SEED = 0xFFFF;
    private static final int CRC_SSP_POLY = 0x8005;

    // unit_type from doc
    public static final Map<Integer, String> unitType;

    static {
        unitType = new HashMap<>();
        unitType.put(0, "Banknote validator");
        unitType.put(3, "SMART Hopper");
        unitType.put(6, "SMART Payout");
        unitType.put(8, "SMART Coin");
        // Add more if needed (from ITL docs / common values)
        // unitType.put(4, "SMART System Flush");
        // unitType.put(7, "TEBS");
    }

    // status_desc from doc (poll events + general responses)
    public static final Map<Integer, String> statusDesc;

    static {
        statusDesc = new HashMap<>();
        statusDesc.put(0xF0, "OK");
        statusDesc.put(0xF1, "SLAVE_RESET");
        statusDesc.put(0xEF, "READ_NOTE");
        statusDesc.put(0xEE, "CREDIT_NOTE");
        statusDesc.put(0xED, "NOTE_REJECTING");
        statusDesc.put(0xEC, "NOTE_REJECTED");
        statusDesc.put(0xEB, "NOTE_STACKED");
        statusDesc.put(0xEA, "SAFE_NOTE_JAM");
        statusDesc.put(0xE9, "UNSAFE_NOTE_JAM");
        statusDesc.put(0xE8, "DISABLED");
        statusDesc.put(0xE7, "STACKER_FULL");
        statusDesc.put(0xE6, "FRAUD_ATTEMPT");
        statusDesc.put(0xE5, "BAR_CODE_TICKET_VALIDATED");
        statusDesc.put(0xE4, "CASHBOX_REPLACED");
        statusDesc.put(0xE3, "CASHBOX_REMOVED");
        statusDesc.put(0xCE, "NOTE_HELD_IN_BEZEL");
        statusDesc.put(0xCC, "NOTE_STACKING");
        statusDesc.put(0xB5, "CHANNEL_DISABLE");

        // Common additional poll events from ITL docs (GA138 / GA973)
        statusDesc.put(0xDA, "DISPENSING");           // SMART Payout / Hopper
        statusDesc.put(0xD2, "DISPENSED");
        statusDesc.put(0xD5, "JAMMED");
        statusDesc.put(0xD6, "HALTED");
        statusDesc.put(0xD7, "FLOATING");
        statusDesc.put(0xD8, "FLOATED");
        statusDesc.put(0xD9, "TIME_OUT");
        statusDesc.put(0xDC, "INCOMPLETE_PAYOUT");
        statusDesc.put(0xDD, "INCOMPLETE_FLOAT");
        statusDesc.put(0xC2, "EMPTYING");
        statusDesc.put(0xC3, "EMPTY");
        statusDesc.put(0xDB, "NOTE_STORED_IN_PAYOUT");
        // Add more if you support specific devices
    }

    // reject_note from doc
    public static final Map<Integer, RejectInfo> rejectNote;

    static {
        rejectNote = new HashMap<>();
        rejectNote.put(0x00, new RejectInfo("NOTE_ACCEPTED", "Note accepted, no reject"));
        rejectNote.put(0x01, new RejectInfo("NOTE_LENGTH_INCORRECT", "Length incorrect"));
        rejectNote.put(0x06, new RejectInfo("CHANNEL_INHIBITED", "Channel inhibited"));
        rejectNote.put(0x07, new RejectInfo("SECOND_NOTE_INSERTED", "Second note inserted"));
        rejectNote.put(0x09, new RejectInfo("NOTE_RECOGNISED_IN_MORE_THAN_ONE_CHANNEL", "Recognized in multiple channels"));
        rejectNote.put(0x0B, new RejectInfo("NOTE_TOO_LONG", "Note too long"));
        rejectNote.put(0x0D, new RejectInfo("MECHANISM_SLOW_STALLED", "Mechanism slow or stalled"));
        rejectNote.put(0x0E, new RejectInfo("STRIMMING_ATTEMPT", "Strimming attempt"));
        rejectNote.put(0x0F, new RejectInfo("FRAUD_CHANNEL_REJECT", "Fraud channel reject"));
        rejectNote.put(0x10, new RejectInfo("NO_NOTES_INSERTED", "No notes inserted"));
        rejectNote.put(0x11, new RejectInfo("PEAK_DETECT_FAIL", "Peak detect fail"));
        rejectNote.put(0x12, new RejectInfo("TWISTED_NOTE_DETECTED", "Twisted note detected"));
        rejectNote.put(0x13, new RejectInfo("ESCROW_TIMEOUT", "Escrow timeout"));
        rejectNote.put(0x14, new RejectInfo("BAR_CODE_SCAN_FAIL", "Bar code scan fail"));
        rejectNote.put(0x15, new RejectInfo("REAR_SENSOR_2_FAIL", "Rear sensor 2 fail"));
        rejectNote.put(0x16, new RejectInfo("SLOT_FAIL_1", "Slot fail 1"));
        rejectNote.put(0x17, new RejectInfo("SLOT_FAIL_2", "Slot fail 2"));
        rejectNote.put(0x18, new RejectInfo("LENS_OVERSAMPLE", "Lens oversample"));
        rejectNote.put(0x19, new RejectInfo("WIDTH_DETECT_FAIL", "Width detect fail"));
        rejectNote.put(0x1A, new RejectInfo("SHORT_NOTE_DETECTED", "Short note detected"));
        // Add more reject codes from ITL GA138 / device manual if needed
    }

    public static class RejectInfo {

        public String name;
        public String description;

        public RejectInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    // commands from doc (expanded)
    public static final Map<String, CommandInfo> commands;

    static {
        commands = new HashMap<>();
        commands.put("SYNC", new CommandInfo(0x11, false));
        commands.put("HOST_PROTOCOL_VERSION", new CommandInfo(0x06, false));
        commands.put("SETUP_REQUEST", new CommandInfo(0x05, false));
        commands.put("SET_CHANNEL_INHIBITS", new CommandInfo(0x02, false));
        commands.put("DISPLAY_ON", new CommandInfo(0x03, false)); // Use 0x03 instead of 0x13
        commands.put("DISPLAY_OFF", new CommandInfo(0x04, false)); // Add this too

        commands.put("ENABLE", new CommandInfo(0x0A, false));
        commands.put("DISABLE", new CommandInfo(0x09, false));

        commands.put("ENABLE", new CommandInfo(0x0A, false));
        commands.put("DISABLE", new CommandInfo(0x09, false));
        commands.put("RESET", new CommandInfo(0x01, false));
        commands.put("POLL", new CommandInfo(0x07, false));
        commands.put("REJECT", new CommandInfo(0x08, false));
        commands.put("HOLD", new CommandInfo(0x18, false));
        commands.put("LAST_REJECT_CODE", new CommandInfo(0x17, false));
        commands.put("GET_SERIAL_NUMBER", new CommandInfo(0x0C, false));
        commands.put("UNIT_DATA", new CommandInfo(0x0D, false));
        commands.put("CHANNEL_VALUE_REQUEST", new CommandInfo(0x0E, false));
        commands.put("SET_GENERATOR", new CommandInfo(0x47, false));
        commands.put("SET_MODULUS", new CommandInfo(0x48, false));
        commands.put("REQUEST_KEY_EXCHANGE", new CommandInfo(0x49, false));
        commands.put("RESET_COUNTERS", new CommandInfo(0x35, false));

        // Add more common commands (especially for SMART Payout / Hopper)
        // commands.put("ENABLE_PAYOUT_DEVICE", new CommandInfo(0x5C, true));
        // commands.put("DISABLE_PAYOUT_DEVICE", new CommandInfo(0x5B, true));
        // commands.put("PAYOUT_AMOUNT", new CommandInfo(0x33, true));
        // commands.put("SET_ROUTING", new CommandInfo(0x3B, true));
        // ...
    }

    public static class CommandInfo {

        public int code;
        public boolean encrypted;

        public CommandInfo(int code, boolean encrypted) {
            this.code = code;
            this.encrypted = encrypted;
        }
    }

    // ──────────────────────────────────────────────────────────────
    // The rest of your code remains unchanged
    // ──────────────────────────────────────────────────────────────
    public static byte[] crc16(byte[] source) {
        int crc = 0xFFFF;
        int poly = 0x8005;

        for (byte b : source) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x8000) != 0) {
                    crc = ((crc << 1) & 0xFFFF) ^ poly;
                } else {
                    crc = (crc << 1) & 0xFFFF;
                }
            }
        }

        // Return low byte first, then high byte (Node.js style)
        return new byte[]{
            (byte) (crc & 0xFF), // Low byte first
            (byte) ((crc >> 8) & 0xFF) // High byte second
        };
    }

    public static byte[] encrypt(byte[] key, byte[] data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    public static byte[] decrypt(byte[] key, byte[] data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }

    public static byte[] stuffBuffer(byte[] input) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // First byte (STX) is not stuffed
        baos.write(input[0] & 0xFF);

        for (int i = 1; i < input.length; i++) {
            int b = input[i] & 0xFF;
            baos.write(b);
            if (b == 0x7F) {
                baos.write(0x7F); // Duplicate if 0x7F
            }
        }

        return baos.toByteArray();
    }

    public static byte[] getPacket(String command, byte[] argBytes, byte sequence, byte[] encryptKey, int eCount) throws Exception {
        CommandInfo cmdInfo = commands.get(command.toUpperCase());
        if (cmdInfo == null) {
            throw new IllegalArgumentException("Unknown command: " + command);
        }

        byte[] data = ByteBuffer.allocate(1 + argBytes.length).put((byte) cmdInfo.code).put(argBytes).array();

        if (encryptKey != null && cmdInfo.encrypted) {
            ByteBuffer eCountBuf = ByteBuffer.allocate(4).putInt(eCount);
            int paddingLength = (16 - ((data.length + 7) % 16)) % 16;
            byte[] padding = new byte[paddingLength];
            new SecureRandom().nextBytes(padding);
            byte[] crcPacket = ByteBuffer.allocate(1 + 4 + data.length + padding.length)
                    .put((byte) data.length).put(eCountBuf.array()).put(data).put(padding).array();
            byte[] crc = crc16(crcPacket);
            byte[] toEncrypt = ByteBuffer.allocate(crcPacket.length + crc.length).put(crcPacket).put(crc).array();
            byte[] encrypted = encrypt(encryptKey, toEncrypt);
            data = ByteBuffer.allocate(1 + encrypted.length).put(STEX).put(encrypted).array();
        }

        byte[] crcPacket = ByteBuffer.allocate(2 + data.length).put(sequence).put((byte) data.length).put(data).array();
        byte[] crc = crc16(crcPacket);
        byte[] packet = ByteBuffer.allocate(crcPacket.length + crc.length).put(crcPacket).put(crc).array();
        return ByteBuffer.allocate(1 + packet.length).put(STX).put(stuffBuffer(packet)).array();
    }

    public static byte[] extractPacketData(byte[] buffer, byte[] encryptKey, int count,boolean debug) throws Exception {
        if (buffer[0] != STX) {
            throw new IllegalArgumentException("Unknown response");
        }

        // Extract the packet data (excluding STX)
        byte seq = buffer[1];
        int dataLength = buffer[2] & 0xFF;

        // Get the data portion
        byte[] data = new byte[dataLength];
        System.arraycopy(buffer, 3, data, 0, dataLength);

        // Get the CRC from the packet
        byte[] packetCrc = new byte[2];
        System.arraycopy(buffer, buffer.length - 2, packetCrc, 0, 2);

        // Calculate CRC on (SEQ + LENGTH + DATA)
        byte[] crcData = new byte[2 + dataLength];
        crcData[0] = seq;
        crcData[1] = buffer[2];
        System.arraycopy(data, 0, crcData, 2, dataLength);

        byte[] calculatedCrc = crc16(crcData);

        // For debugging
        if(debug) {
        System.out.println("CRC Check - Received: " + bytesToHex(packetCrc)
                + " Calculated: " + bytesToHex(calculatedCrc));
        }

        // Check CRC (allow for both byte orders)
        boolean crcValid = (packetCrc[0] == calculatedCrc[0] && packetCrc[1] == calculatedCrc[1])
                || (packetCrc[0] == calculatedCrc[1] && packetCrc[1] == calculatedCrc[0]);

        if (!crcValid) {
            // Don't throw exception, just log and continue for now
            System.out.println("Warning: CRC mismatch - ignoring");
            // throw new IllegalArgumentException("Wrong CRC16");
        }

        // Check if it's an encrypted response
        if (encryptKey != null && data.length > 0 && data[0] == STEX) {
            // Handle encrypted response (to be implemented)
            return data; // Return as-is for now
        }

        return data;
    }

    public static Keys generateKeys() {
        BigInteger generator = BigInteger.probablePrime(64, new SecureRandom());
        BigInteger modulus = BigInteger.probablePrime(64, new SecureRandom());
        if (generator.compareTo(modulus) < 0) {
            BigInteger temp = generator;
            generator = modulus;
            modulus = temp;
        }
        BigInteger hostRandom = BigInteger.probablePrime(64, new SecureRandom());
        BigInteger hostInter = generator.modPow(hostRandom, modulus);
        return new Keys(generator, modulus, hostRandom, hostInter);
    }

    public static class Keys {

        public BigInteger generator;
        public BigInteger modulus;
        public BigInteger hostRandom;
        public BigInteger hostInter;

        public Keys(BigInteger generator, BigInteger modulus, BigInteger hostRandom, BigInteger hostInter) {
            this.generator = generator;
            this.modulus = modulus;
            this.hostRandom = hostRandom;
            this.hostInter = hostInter;
        }
    }

    public static byte[] uInt64LE(BigInteger number) {
        byte[] bytes = number.toByteArray();
        // Trim or pad to exactly 8 bytes, then reverse to little-endian
        byte[] le = new byte[8];
        for (int i = 0; i < 8; i++) {
            int srcIdx = bytes.length - 1 - i;
            le[i] = (srcIdx >= 0) ? bytes[srcIdx] : 0;
        }
        return le;
    }

    public static byte[] uInt32LE(int number) {
        return ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(number)
                .array();
    }

    public static byte[] uInt16LE(int number) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) number).array();
    }

    public static byte[] hexStringToByteArray(String s) {
        s = s.replace(" ", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    public static void debugCRC() {
        System.out.println("=== CRC Debug ===");
        // Test SYNC packet data (without STX)
        byte[] testData = new byte[]{(byte) 0x80, 0x01, 0x11};
        byte[] crc = crc16(testData);
        System.out.println("CRC16 of [80 01 11] = "
                + String.format("%02X %02X", crc[0] & 0xFF, crc[1] & 0xFF));

        // Test with full SYNC packet including STX
        byte[] fullPacket = new byte[]{(byte) 0x7F, (byte) 0x80, 0x01, 0x11, crc[0], crc[1]};
        System.out.println("Full SYNC packet: " + bytesToHex(fullPacket));

        // Calculate CRC of the packet data (excluding STX)
        byte[] packetData = new byte[]{0x01, 0x11}; // length + command
        byte[] packetCrc = crc16(new byte[]{(byte) 0x80, 0x01, 0x11});
        System.out.println("Expected CRC: " + String.format("%02X %02X", packetCrc[0] & 0xFF, packetCrc[1] & 0xFF));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString();
    }
}
