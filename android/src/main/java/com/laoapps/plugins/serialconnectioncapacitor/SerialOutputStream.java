package com.laoapps.plugins.serialconnectioncapacitor;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import java.io.IOException;
import java.io.OutputStream;

public class SerialOutputStream extends OutputStream {
    private final UsbDeviceConnection connection;
    private final UsbEndpoint endpoint;
    private final int timeout;

    public SerialOutputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
        this.connection = connection;
        this.endpoint = endpoint;
        this.timeout = 1000; // Define timeout
    }

    @Override
    public void write(int b) throws IOException {
        byte[] buffer = new byte[] { (byte) b };
        connection.bulkTransfer(endpoint, buffer, 1, timeout);
    }
}