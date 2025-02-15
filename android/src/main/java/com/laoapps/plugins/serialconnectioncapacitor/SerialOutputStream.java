package com.laoapps.plugins.serialconnectioncapacitor;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;

import java.io.IOException;
import java.io.OutputStream;

public class SerialOutputStream extends OutputStream {
    private UsbDeviceConnection connection;
    private UsbEndpoint endpoint;

    public SerialOutputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
        this.connection = connection;
        this.endpoint = endpoint;
    }

    @Override
    public void write(int b) throws IOException {
        byte[] buffer = new byte[]{(byte) b};
        int result = connection.bulkTransfer(endpoint, buffer, buffer.length, 1000);
        if (result < 0) {
            throw new IOException("Error writing to USB device");
        }
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        byte[] tempBuffer = new byte[length];
        System.arraycopy(buffer, offset, tempBuffer, 0, length);
        int result = connection.bulkTransfer(endpoint, tempBuffer, tempBuffer.length, 1000);
        if (result < 0) {
            throw new IOException("Error writing to USB device");
        }
    }
}