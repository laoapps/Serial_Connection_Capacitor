package com.laoapps.plugins.serialconnectioncapacitor;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class SerialConnectionCapacitor {
    private final Context context;
    private UsbSerialPort port;
    private UsbDeviceConnection connection;
    private final UsbManager usbManager;

    public SerialConnectionCapacitor(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }

    public boolean openConnection(String portPath, int baudRate) {
        try {
            // Find the device by path
            UsbDevice targetDevice = null;
            for (UsbDevice device : usbManager.getDeviceList().values()) {
                if (device.getDeviceName().equals(portPath)) {
                    targetDevice = device;
                    break;
                }
            }

            if (targetDevice == null) {
                return false;
            }

            // Find available drivers
            List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (availableDrivers.isEmpty()) {
                return false;
            }

            // Use the first available driver
            UsbSerialDriver driver = availableDrivers.get(0);
            connection = usbManager.openDevice(driver.getDevice());
            if (connection == null) {
                return false;
            }

            port = driver.getPorts().get(0); // Most devices have just one port
            port.open(connection);
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public java.io.OutputStream getOutputStream() throws IOException {
        if (port == null) {
            throw new IOException("Port not initialized");
        }
        return new java.io.OutputStream() {
            @Override
            public void write(int b) throws IOException {
                port.write(new byte[]{(byte) b}, 1000);
            }

            @Override
            public void write(byte[] b) throws IOException {
                port.write(b, 1000);
            }
        };
    }

    public java.io.InputStream getInputStream() throws IOException {
        if (port == null) {
            throw new IOException("Port not initialized");
        }
        return new java.io.InputStream() {
            @Override
            public int read() throws IOException {
                byte[] buffer = new byte[1];
                int bytesRead = port.read(buffer, 1000);
                return (bytesRead > 0) ? (buffer[0] & 0xff) : -1;
            }

            @Override
            public int read(byte[] b) throws IOException {
                return port.read(b, 1000);
            }
        };
    }

    public void closeConnection() {
        try {
            if (port != null) {
                port.close();
            }
            if (connection != null) {
                connection.close();
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        } finally {
            port = null;
            connection = null;
        }
    }
}