package com.laoapps.plugins.serialconnectioncapacitor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class SSPParser {
    private static final byte SSP_STX = 0x7F; // Start of packet marker

    // Instance variables to maintain parsing state
    private int counter = 0;         // Tracks the number of bytes processed in the current packet
    private int checkStuff = 0;      // Flag to check for stuffed bytes (0 or 1)
    private int packetLength = 0;    // Expected length of the current packet
    private ByteBuffer buffer;       // Buffer to accumulate packet bytes
    private final List<byte[]> packets = new ArrayList<>(); // List to store completed packets

    // Constructor
    public SSPParser() {
        reset(); // Initialize state
    }

    /**
     * Parses a chunk of bytes and returns a list of complete SSP packets.
     * Incomplete packets are retained in the buffer for the next call.
     *
     * @param chunk The byte array to parse
     * @return List of complete SSP packets found in the chunk
     */
    public List<byte[]> parse(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return new ArrayList<>(); // Return empty list for null or empty input
        }

        packets.clear(); // Clear previous packets

        for (byte b : chunk) {
            if (b == SSP_STX && counter == 0) {
                // Start of a new packet
                buffer = ByteBuffer.allocate(1).put(b);
                counter = 1;
            } else if (b == SSP_STX && counter == 1) {
                // Reset if we see STX immediately after another STX (stuffed byte reset)
                reset();
            } else {
                if (checkStuff == 1) {
                    // Handling the byte after an STX (checking for stuffing)
                    if (b != SSP_STX) {
                        // Not a stuffed byte, start a new packet with STX and current byte
                        buffer = ByteBuffer.allocate(2).put(SSP_STX).put(b);
                        counter = 2;
                    } else {
                        // Stuffed byte (STX followed by STX), append it
                        buffer = append(buffer, b);
                        counter++;
                    }
                    checkStuff = 0; // Reset stuffing check
                } else {
                    // Normal byte processing
                    if (b == SSP_STX) {
                        // Potential stuffing or new packet start, set flag to check next byte
                        checkStuff = 1;
                    } else {
                        // Append the byte to the current packet
                        buffer = append(buffer, b);
                        counter++;

                        // Determine packet length when we have 3 bytes (STX, SEQ, LEN)
                        if (counter == 3) {
                            packetLength = (buffer.get(2) & 0xFF) + 5; // LEN + STX + SEQ + LEN + CRC (2 bytes)
                        }
                    }
                }

                // Check if we've collected a complete packet
                if (packetLength > 0 && buffer.capacity() == packetLength) {
                    packets.add(buffer.array());
                    reset(); // Prepare for the next packet
                }
            }
        }

        return new ArrayList<>(packets); // Return a copy of the packets list
    }

    /**
     * Resets the parser state to start processing a new packet.
     */
    private void reset() {
        counter = 0;
        checkStuff = 0;
        packetLength = 0;
        buffer = ByteBuffer.allocate(0); // Clear buffer
    }

    /**
     * Appends a byte to the current buffer, resizing as necessary.
     *
     * @param original The current buffer
     * @param b The byte to append
     * @return A new ByteBuffer with the appended byte
     */
    private ByteBuffer append(ByteBuffer original, byte b) {
        ByteBuffer newBuffer = ByteBuffer.allocate(original.capacity() + 1);
        newBuffer.put(original.array()).put(b);
        return newBuffer;
    }

    /**
     * Flushes any remaining data in the buffer as a packet, if valid.
     *
     * @return List containing the flushed packet, if any
     */
    public List<byte[]> flush() {
        packets.clear();
        if (buffer.capacity() > 0 && packetLength > 0 && buffer.capacity() == packetLength) {
            packets.add(buffer.array());
        }
        reset();
        return new ArrayList<>(packets);
    }

    // Utility method for debugging or testing
    public int getCounter() {
        return counter;
    }

    public int getPacketLength() {
        return packetLength;
    }

    public byte[] getCurrentBuffer() {
        return buffer.array();
    }
}