package org.yourcompany.yourproject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SSPParser {
    private static final byte STX = 0x7F;      // Start of packet
    private static final byte ESCAPE = 0x7F;   // Escape byte (same as STX in SSP)
    
    // Parser states
    private enum State {
        WAIT_FOR_STX,      // Waiting for packet start
        IN_PACKET,         // Reading packet data
        ESCAPE_NEXT        // Next byte is escaped
    }
    
    private State state = State.WAIT_FOR_STX;
    private ByteArrayBuilder packetBuffer = new ByteArrayBuilder();
    private int expectedLength = 0;
    private final List<byte[]> completePackets = new ArrayList<>();
    
    /**
     * Parses incoming data and returns any complete SSP packets found.
     * This is a streaming parser - call it repeatedly as data arrives.
     * 
     * @param data New data chunk to parse
     * @return List of complete packets found in this chunk
     */
    public List<byte[]> parse(byte[] data) {
        completePackets.clear();
        
        for (byte b : data) {
            processByte(b & 0xFF); // Convert to unsigned int for easier comparison
        }
        
        return new ArrayList<>(completePackets);
    }
    
    private void processByte(int b) {
        switch (state) {
            case WAIT_FOR_STX:
                if (b == (STX & 0xFF)) {
                    // Found start of packet
                    packetBuffer.reset();
                    packetBuffer.append((byte) b);
                    state = State.IN_PACKET;
                }
                break;
                
            case IN_PACKET:
                if (b == (ESCAPE & 0xFF)) {
                    // Escape byte found - next byte is escaped
                    state = State.ESCAPE_NEXT;
                } else {
                    // Normal byte
                    packetBuffer.append((byte) b);
                    
                    // Check if we have enough bytes to determine packet length
                    if (packetBuffer.size() == 3) {
                        // We have STX + SEQ + LEN
                        expectedLength = (packetBuffer.get(2) & 0xFF) + 5; // LEN + STX + SEQ + LEN + CRC(2)
                    }
                    
                    // Check if packet is complete
                    if (expectedLength > 0 && packetBuffer.size() == expectedLength) {
                        // Complete packet received
                        byte[] packet = packetBuffer.toByteArray();
                        // Verify CRC before accepting
                        if (verifyCRC(packet)) {
                            completePackets.add(packet);
                        }
                        reset();
                    }
                }
                break;
                
            case ESCAPE_NEXT:
                // This byte is escaped (should be the byte that follows an 0x7F)
                // In SSP, when 0x7F appears in data, it's sent as 0x7F 0x7F
                // So we just add the byte (which is the actual data value)
                packetBuffer.append((byte) b);
                
                // Check packet length if we just completed the third byte
                if (packetBuffer.size() == 3) {
                    expectedLength = (packetBuffer.get(2) & 0xFF) + 5;
                }
                
                // Check if packet is complete
                if (expectedLength > 0 && packetBuffer.size() == expectedLength) {
                    byte[] packet = packetBuffer.toByteArray();
                    if (verifyCRC(packet)) {
                        completePackets.add(packet);
                    }
                    reset();
                } else {
                    state = State.IN_PACKET;
                }
                break;
        }
    }
    
    /**
     * Verifies the CRC16 of a complete packet.
     * Packet format: [STX][SEQ][LEN][DATA...][CRC_LOW][CRC_HIGH]
     */
    private boolean verifyCRC(byte[] packet) {
        if (packet.length < 4) return false;
        
        // Extract CRC from packet (last 2 bytes)
        int packetCRC = ((packet[packet.length - 2] & 0xFF) << 8) | 
                        (packet[packet.length - 1] & 0xFF);
        
        // Calculate CRC on packet data (excluding STX and the CRC bytes)
        byte[] crcData = Arrays.copyOfRange(packet, 1, packet.length - 2);
        byte[] calculated = SSPUtils.crc16(crcData);
        int calculatedCRC = ((calculated[0] & 0xFF) << 8) | (calculated[1] & 0xFF);
        
        return packetCRC == calculatedCRC;
    }
    
    /**
     * Resets the parser state.
     */
    private void reset() {
        state = State.WAIT_FOR_STX;
        packetBuffer.reset();
        expectedLength = 0;
    }
    
    /**
     * Flushes any pending data and returns any complete packet that might be in the buffer.
     * Useful at end of stream.
     */
    public List<byte[]> flush() {
        completePackets.clear();
        
        // If we have a complete packet in the buffer, try to use it
        if (packetBuffer.size() > 0 && expectedLength > 0 && packetBuffer.size() == expectedLength) {
            byte[] packet = packetBuffer.toByteArray();
            if (verifyCRC(packet)) {
                completePackets.add(packet);
            }
        }
        
        reset();
        return new ArrayList<>(completePackets);
    }
    
    /**
     * Helper class for building byte arrays efficiently.
     */
    private static class ByteArrayBuilder {
        private byte[] buffer = new byte[256];
        private int length = 0;
        
        public void append(byte b) {
            ensureCapacity(length + 1);
            buffer[length++] = b;
        }
        
        private void ensureCapacity(int minCapacity) {
            if (minCapacity > buffer.length) {
                int newCapacity = Math.max(buffer.length * 2, minCapacity);
                buffer = Arrays.copyOf(buffer, newCapacity);
            }
        }
        
        public byte get(int index) {
            if (index < 0 || index >= length) {
                throw new IndexOutOfBoundsException("Index: " + index + ", Length: " + length);
            }
            return buffer[index];
        }
        
        public int size() {
            return length;
        }
        
        public byte[] toByteArray() {
            return Arrays.copyOf(buffer, length);
        }
        
        public void reset() {
            length = 0;
        }
    }
}