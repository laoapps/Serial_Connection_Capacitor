package com.laoapps.plugins.serialconnectioncapacitor;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

import java.io.IOException;
import java.io.InputStream;

public class SerialInputStream extends InputStream {
    private UsbDeviceConnection connection;
    private UsbEndpoint endpoint;

    public SerialInputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
        this.connection = connection;
        this.endpoint = endpoint;
    }

    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        int result = connection.bulkTransfer(endpoint, buffer, buffer.length, 1000);
        if (result >= 0) {
            return buffer[0] & 0xFF;
        } else {
            throw new IOException("Error reading from USB device");
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        byte[] tempBuffer = new byte[length];
        int result = connection.bulkTransfer(endpoint, tempBuffer, tempBuffer.length, 1000);
        if (result >= 0) {
            System.arraycopy(tempBuffer, 0, buffer, offset, result);
            return result;
        } else {
            throw new IOException("Error reading from USB device");
        }
    }
}