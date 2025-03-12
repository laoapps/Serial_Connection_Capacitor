package com.laoapps.plugins.serialconnectioncapacitor;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class SSPUtils {
    private static final byte STX = 0x7F;
    private static final byte STEX = 0x7E;
    private static final int CRC_SSP_SEED = 0xFFFF;
    private static final int CRC_SSP_POLY = 0x8005;

    // unit_type.json
    public static final Map<Integer, String> unitType = new HashMap<>() {{
        put(0, "Banknote validator");
        put(3, "Smart Hopper");
        put(6, "SMART Payout fitted");
        put(7, "Note Float fitted");
        put(8, "Addon Printer");
        put(11, "Stand Alone Printer");
        put(13, "TEBS");
        put(14, "TEBS with SMART Payout");
        put(15, "TEBS with SMART Ticket");
    }};

    // status_desc.json
    public static final Map<Integer, String> statusDesc = new HashMap<>() {{
        put(176, "JAM_RECOVERY");
        put(177, "ERROR_DURING_PAYOUT");
        put(179, "SMART_EMPTYING");
        put(180, "SMART_EMPTIED");
        put(181, "CHANNEL_DISABLE");
        put(182, "INITIALISING");
        put(183, "COIN_MECH_ERROR");
        put(194, "EMPTYING");
        put(195, "EMPTIED");
        put(196, "COIN_MECH_JAMMED");
        put(197, "COIN_MECH_RETURN_PRESSED");
        put(198, "PAYOUT_OUT_OF_SERVICE");
        put(199, "NOTE_FLOAT_REMOVED");
        put(200, "NOTE_FLOAT_ATTACHED");
        put(201, "NOTE_TRANSFERED_TO_STACKER");
        put(202, "NOTE_PAID_INTO_STACKER_AT_POWER-UP");
        put(203, "NOTE_PAID_INTO_STORE_AT_POWER-UP");
        put(204, "NOTE_STACKING");
        put(205, "NOTE_DISPENSED_AT_POWER-UP");
        put(206, "NOTE_HELD_IN_BEZEL");
        put(207, "DEVICE_FULL");
        put(209, "BAR_CODE_TICKET_ACKNOWLEDGE");
        put(210, "DISPENSED");
        put(213, "JAMMED");
        put(214, "HALTED");
        put(215, "FLOATING");
        put(216, "FLOATED");
        put(217, "TIME_OUT");
        put(218, "DISPENSING");
        put(219, "NOTE_STORED_IN_PAYOUT");
        put(220, "INCOMPLETE_PAYOUT");
        put(221, "INCOMPLETE_FLOAT");
        put(222, "CASHBOX_PAID");
        put(223, "COIN_CREDIT");
        put(224, "NOTE_PATH_OPEN");
        put(225, "NOTE_CLEARED_FROM_FRONT");
        put(226, "NOTE_CLEARED_TO_CASHBOX");
        put(227, "CASHBOX_REMOVED");
        put(228, "CASHBOX_REPLACED");
        put(229, "BAR_CODE_TICKET_VALIDATED");
        put(230, "FRAUD_ATTEMPT");
        put(231, "STACKER_FULL");
        put(232, "DISABLED");
        put(233, "UNSAFE_NOTE_JAM");
        put(234, "SAFE_NOTE_JAM");
        put(235, "NOTE_STACKED");
        put(236, "NOTE_REJECTED");
        put(237, "NOTE_REJECTING");
        put(238, "CREDIT_NOTE");
        put(239, "READ_NOTE");
        put(240, "OK");
        put(241, "SLAVE_RESET");
        put(242, "COMMAND_NOT_KNOWN");
        put(243, "WRONG_NO_PARAMETERS");
        put(244, "PARAMETER_OUT_OF_RANGE");
        put(245, "COMMAND_CANNOT_BE_PROCESSED");
        put(246, "SOFTWARE_ERROR");
        put(248, "FAIL");
        put(250, "KEY_NOT_SET");
    }};

    // commands.json
    public static final Map<String, CommandInfo> commands = new HashMap<>() {{
        put("RESET", new CommandInfo(1, false));
        put("SET_CHANNEL_INHIBITS", new CommandInfo(2, false));
        put("DISPLAY_ON", new CommandInfo(3, false));
        put("DISPLAY_OFF", new CommandInfo(4, false));
        put("SETUP_REQUEST", new CommandInfo(5, false));
        put("HOST_PROTOCOL_VERSION", new CommandInfo(6, false));
        put("POLL", new CommandInfo(7, false));
        put("REJECT_BANKNOTE", new CommandInfo(8, false));
        put("DISABLE", new CommandInfo(9, false));
        put("ENABLE", new CommandInfo(10, false));
        put("GET_SERIAL_NUMBER", new CommandInfo(12, false));
        put("UNIT_DATA", new CommandInfo(13, false));
        put("CHANNEL_VALUE_REQUEST", new CommandInfo(14, false));
        put("CHANNEL_SECURITY_DATA", new CommandInfo(15, false));
        put("CHANNEL_RE_TEACH_DATA", new CommandInfo(16, false));
        put("SYNC", new CommandInfo(17, false));
        put("LAST_REJECT_CODE", new CommandInfo(23, false));
        put("HOLD", new CommandInfo(24, false));
        put("GET_FIRMWARE_VERSION", new CommandInfo(32, false));
        put("GET_DATASET_VERSION", new CommandInfo(33, false));
        put("GET_ALL_LEVELS", new CommandInfo(34, false));
        put("GET_BAR_CODE_READER_CONFIGURATION", new CommandInfo(35, false));
        put("SET_BAR_CODE_CONFIGURATION", new CommandInfo(36, false));
        put("GET_BAR_CODE_INHIBIT_STATUS", new CommandInfo(37, false));
        put("SET_BAR_CODE_INHIBIT_STATUS", new CommandInfo(38, false));
        put("GET_BAR_CODE_DATA", new CommandInfo(39, false));
        put("SET_REFILL_MODE", new CommandInfo(48, false));
        put("PAYOUT_AMOUNT", new CommandInfo(51, true));
        put("SET_DENOMINATION_LEVEL", new CommandInfo(52, false));
        put("GET_DENOMINATION_LEVEL", new CommandInfo(53, false));
        put("COMMUNICATION_PASS_THROUGH", new CommandInfo(55, false));
        put("HALT_PAYOUT", new CommandInfo(56, true));
        put("SET_DENOMINATION_ROUTE", new CommandInfo(59, true));
        put("GET_DENOMINATION_ROUTE", new CommandInfo(60, true));
        put("FLOAT_AMOUNT", new CommandInfo(61, true));
        put("GET_MINIMUM_PAYOUT", new CommandInfo(62, false));
        put("EMPTY_ALL", new CommandInfo(63, true));
        put("SET_COIN_MECH_INHIBITS", new CommandInfo(64, false));
        put("GET_NOTE_POSITIONS", new CommandInfo(65, false));
        put("PAYOUT_NOTE", new CommandInfo(66, false));
        put("STACK_NOTE", new CommandInfo(67, false));
        put("FLOAT_BY_DENOMINATION", new CommandInfo(68, true));
        put("SET_VALUE_REPORTING_TYPE", new CommandInfo(69, false));
        put("PAYOUT_BY_DENOMINATION", new CommandInfo(70, true));
        put("SET_COIN_MECH_GLOBAL_INHIBIT", new CommandInfo(73, false));
        put("SET_GENERATOR", new CommandInfo(74, false));
        put("SET_MODULUS", new CommandInfo(75, false));
        put("REQUEST_KEY_EXCHANGE", new CommandInfo(76, false));
        put("SET_BAUD_RATE", new CommandInfo(77, false));
        put("GET_BUILD_REVISION", new CommandInfo(79, false));
        put("SET_HOPPER_OPTIONS", new CommandInfo(80, false));
        put("GET_HOPPER_OPTIONS", new CommandInfo(81, false));
        put("SMART_EMPTY", new CommandInfo(82, true));
        put("CASHBOX_PAYOUT_OPERATION_DATA", new CommandInfo(83, false));
        put("CONFIGURE_BEZEL", new CommandInfo(84, false));
        put("POLL_WITH_ACK", new CommandInfo(86, true));
        put("EVENT_ACK", new CommandInfo(87, true));
        put("GET_COUNTERS", new CommandInfo(88, false));
        put("RESET_COUNTERS", new CommandInfo(89, false));
        put("COIN_MECH_OPTIONS", new CommandInfo(90, false));
        put("DISABLE_PAYOUT_DEVICE", new CommandInfo(91, false));
        put("ENABLE_PAYOUT_DEVICE", new CommandInfo(92, false));
        put("SET_FIXED_ENCRYPTION_KEY", new CommandInfo(96, true));
        put("RESET_FIXED_ENCRYPTION_KEY", new CommandInfo(97, false));
    }};

    public static class CommandInfo {
        public byte code;
        public boolean encrypted;

        CommandInfo(int code, boolean encrypted) {
            this.code = (byte) code;
            this.encrypted = encrypted;
        }
    }

    // reject_note.json
    public static final Map<Integer, RejectInfo> rejectNote = new HashMap<>() {{
        put(0, new RejectInfo("NOTE_ACCEPTED", "The banknote has been accepted. No reject has occurred."));
        put(1, new RejectInfo("LENGTH_FAIL", "A validation fail: The banknote has been read but its length registers over the max length parameter."));
        put(2, new RejectInfo("AVERAGE_FAIL", "Internal validation failure - banknote not recognised."));
        put(3, new RejectInfo("COASTLINE_FAIL", "Internal validation failure - banknote not recognised."));
        put(4, new RejectInfo("GRAPH_FAIL", "Internal validation failure - banknote not recognised."));
        put(5, new RejectInfo("BURIED_FAIL", "Internal validation failure - banknote not recognised."));
        put(6, new RejectInfo("CHANNEL_INHIBIT", "This banknote has been inhibited for acceptance in the dataset configuration."));
        put(7, new RejectInfo("SECOND_NOTE_DETECTED", "A second banknote was inserted into the validator while the first one was still being transported through the banknote path."));
        put(8, new RejectInfo("REJECT_BY_HOST", "The host system issues a Reject command when this banknote was held in escrow."));
        put(9, new RejectInfo("CROSS_CHANNEL_DETECTED", "This banknote was identified as existing in two or more separate channel definitions in the dataset."));
        put(10, new RejectInfo("REAR_SENSOR_ERROR", "An inconsistency in a position sensor detection was seen."));
        put(11, new RejectInfo("NOTE_TOO_LONG", "The banknote failed dataset length checks."));
        put(12, new RejectInfo("DISABLED_BY_HOST", "The banknote was validated on a channel that has been inhibited for acceptance by the host system."));
        put(13, new RejectInfo("SLOW_MECH", "The internal mechanism was detected as moving too slowly for correct validation."));
        put(14, new RejectInfo("STRIM_ATTEMPT", "The internal mechanism was detected as moving too slowly for correct validation."));
        put(15, new RejectInfo("FRAUD_CHANNEL", "Obsolete response."));
        put(16, new RejectInfo("NO_NOTES_DETECTED", "A banknote detection was initiated but no banknotes were seen at the validation section."));
        put(17, new RejectInfo("PEAK_DETECT_FAIL", "Internal validation fail. Banknote not recognised."));
        put(18, new RejectInfo("TWISTED_NOTE_REJECT", "Internal validation fail. Banknote not recognised."));
        put(19, new RejectInfo("ESCROW_TIME-OUT", "A banknote held in escrow was rejected due to the host not communicating within the timeout period."));
        put(20, new RejectInfo("BAR_CODE_SCAN_FAIL", "Internal validation fail. Banknote not recognised."));
        put(21, new RejectInfo("NO_CAM_ACTIVATE", "A banknote did not reach the internal note path for validation during transport."));
        put(22, new RejectInfo("SLOT_FAIL_1", "Internal validation fail. Banknote not recognised."));
        put(23, new RejectInfo("SLOT_FAIL_2", "Internal validation fail. Banknote not recognised."));
        put(24, new RejectInfo("LENS_OVERSAMPLE", "The banknote was transported faster than the system could sample the note."));
        put(25, new RejectInfo("WIDTH_DETECTION_FAIL", "The banknote failed a measurement test."));
        put(26, new RejectInfo("SHORT_NOTE_DETECT", "The banknote measured length fell outside of the validation parameter for minimum length."));
        put(27, new RejectInfo("PAYOUT_NOTE", "The reject code command was issued after a note was paid out using a note payout device."));
        put(28, new RejectInfo("DOUBLE_NOTE_DETECTED", "More than one banknote was detected as overlaid during note entry."));
        put(29, new RejectInfo("UNABLE_TO_STACK", "The banknote was unable to reach its correct stacking position during transport."));
    }};

    public static class RejectInfo {
        public String name;
        public String description;

        RejectInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    public static byte[] crc16(byte[] source) {
        int crc = CRC_SSP_SEED;
        for (byte b : source) {
            crc ^= (b & 0xFF) << 8;
            for (int j = 0; j < 8; j++) {
                crc = (crc & 0x8000) != 0 ? (crc << 1) ^ CRC_SSP_POLY : crc << 1;
            }
        }
        crc &= 0xFFFF;
        return ByteBuffer.allocate(2).putShort((short) crc).array();
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
        ByteBuffer output = ByteBuffer.allocate(input.length * 2);
        int j = 0;
        for (byte b : input) {
            output.put(j++, b);
            if (b == STX) output.put(j++, STX);
        }
        byte[] result = new byte[j];
        System.arraycopy(output.array(), 0, result, 0, j);
        return result;
    }

    public static byte[] getPacket(String command, byte[] argBytes, byte sequence, byte[] encryptKey, int eCount) throws Exception {
        CommandInfo cmdInfo = commands.get(command);
        if (cmdInfo == null) throw new IllegalArgumentException("Unknown command: " + command);

        byte[] data = ByteBuffer.allocate(1 + argBytes.length).put(cmdInfo.code).put(argBytes).array();

        if (encryptKey != null && (cmdInfo.encrypted || true)) { // Assuming encryptAllCommand is true for simplicity
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

    public static byte[] extractPacketData(byte[] buffer, byte[] encryptKey, int count) throws Exception {
        if (buffer[0] != STX) throw new IllegalArgumentException("Unknown response");
        ByteBuffer bb = ByteBuffer.wrap(buffer, 1, buffer.length - 1);
        byte dataLength = bb.get(1);
        byte[] packetData = new byte[dataLength];
        bb.position(2);
        bb.get(packetData, 0, dataLength);
        byte[] crcData = new byte[dataLength + 2];
        System.arraycopy(buffer, 1, crcData, 0, crcData.length);
        byte[] crcCheck = new byte[2];
        System.arraycopy(buffer, buffer.length - 2, crcCheck, 0, 2);
        if (!java.util.Arrays.equals(crc16(crcData), crcCheck)) {
            throw new IllegalArgumentException("Wrong CRC16");
        }

        if (encryptKey != null && packetData[0] == STEX) {
            byte[] decrypted = decrypt(encryptKey, ByteBuffer.wrap(packetData, 1, packetData.length - 1).array());
            int eLength = decrypted[0] & 0xFF;
            int eCount = ByteBuffer.wrap(decrypted, 1, 4).getInt();
            if (eCount != count + 1) throw new IllegalArgumentException("Encrypted counter mismatch");
            return ByteBuffer.wrap(decrypted, 5, eLength).array();
        }
        return packetData;
    }

    public static Keys generateKeys() {
        BigInteger generator = BigInteger.probablePrime(16, new SecureRandom());
        BigInteger modulus = BigInteger.probablePrime(16, new SecureRandom());
        if (generator.compareTo(modulus) < 0) {
            BigInteger temp = generator;
            generator = modulus;
            modulus = temp;
        }
        BigInteger hostRandom = new BigInteger(16, new SecureRandom()).mod(BigInteger.valueOf(2147483648L));
        BigInteger hostInter = generator.modPow(hostRandom, modulus);
        return new Keys(generator, modulus, hostRandom, hostInter);
    }

    public static class Keys {
        public BigInteger generator;
        public BigInteger modulus;
        public BigInteger hostRandom;
        public BigInteger hostInter;

        Keys(BigInteger generator, BigInteger modulus, BigInteger hostRandom, BigInteger hostInter) {
            this.generator = generator;
            this.modulus = modulus;
            this.hostRandom = hostRandom;
            this.hostInter = hostInter;
        }
    }

    public static byte[] uInt64LE(BigInteger number) {
        byte[] bytes = number.toByteArray();
        ByteBuffer buffer = ByteBuffer.allocate(8);
        if (bytes.length > 8) {
            buffer.put(bytes, bytes.length - 8, 8);
        } else {
            buffer.position(8 - bytes.length);
            buffer.put(bytes);
        }
        return buffer.array();
    }

    public static byte[] uInt32LE(int number) {
        return ByteBuffer.allocate(4).putInt(number).array();
    }

    public static byte[] uInt16LE(int number) {
        return ByteBuffer.allocate(2).putShort((short) number).array();
    }

    public static byte[] readBytesFromBuffer(byte[] buffer, int startIndex, int length) {
        if (buffer == null || startIndex < 0 || startIndex >= buffer.length || length < 0 || startIndex + length > buffer.length) {
            throw new IllegalArgumentException("Invalid buffer parameters");
        }
        byte[] result = new byte[length];
        System.arraycopy(buffer, startIndex, result, 0, length);
        return result;
    }

    public static int randomInt(int min, int max) {
        return min + (int) (Math.random() * (max - min));
    }

    public static BigInteger absBigInt(BigInteger n) {
        return n.compareTo(BigInteger.ZERO) < 0 ? n.negate() : n;
    }
    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}