package com.example.serialconnection;

import android.util.Log;

import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.PluginMethod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "SerialConnection")
public class SerialConnectionCapacitorPlugin extends Plugin {

    private static final String TAG = "SerialConnection";
    private SerialPort serialPort;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ExecutorService executorService;

    @PluginMethod
    public void openConnection(PluginCall call) {
        String portName = call.getString("portName");
        int baudRate = call.getInt("baudRate", 9600);

        try {
            serialPort = new SerialPort(portName, baudRate);
            serialPort.open();
            inputStream = serialPort.getInputStream();
            outputStream = serialPort.getOutputStream();
            executorService = Executors.newSingleThreadExecutor();

            Log.d(TAG, "Serial connection opened on port: " + portName);
            call.resolve();
        } catch (IOException e) {
            Log.e(TAG, "Error opening serial connection", e);
            call.reject("Failed to open connection: " + e.getMessage());
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        String data = call.getString("data");

        if (outputStream == null) {
            call.reject("Serial connection is not open.");
            return;
        }

        try {
            outputStream.write(data.getBytes());
            outputStream.flush();
            Log.d(TAG, "Data written to serial port: " + data);
            call.resolve();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to serial port", e);
            call.reject("Failed to write data: " + e.getMessage());
        }
    }

    @PluginMethod
    public void read(PluginCall call) {
        if (inputStream == null) {
            call.reject("Serial connection is not open.");
            return;
        }

        executorService.submit(() -> {
            byte[] buffer = new byte[1024];
            int bytesRead;

            try {
                bytesRead = inputStream.read(buffer);
                if (bytesRead > 0) {
                    String data = new String(buffer, 0, bytesRead);
                    Log.d(TAG, "Data read from serial port: " + data);

                    getBridge().saveCall(call);
                    call.resolve();
                    call.success("data", data);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from serial port", e);
                call.reject("Failed to read data: " + e.getMessage());
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        if (serialPort != null) {
            try {
                serialPort.close();
                Log.d(TAG, "Serial port closed.");
            } catch (IOException e) {
                Log.e(TAG, "Error closing serial port", e);
            }
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
