package com.laoapps.plugins.serialconnectioncapacitor;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import java.io.IOException;
import java.io.InputStream;

public class SerialInputStream extends InputStream {
    private final UsbDeviceConnection connection;
    private final UsbEndpoint endpoint;
    private final int timeout;

    public SerialInputStream(UsbDeviceConnection connection, UsbEndpoint endpoint) {
        this.connection = connection;
        this.endpoint = endpoint;
        this.timeout = 1000; // Define timeout
    }

    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        int result = connection.bulkTransfer(endpoint, buffer, 1, timeout);
        return result > 0 ? buffer[0] & 0xff : -1;
    }
}