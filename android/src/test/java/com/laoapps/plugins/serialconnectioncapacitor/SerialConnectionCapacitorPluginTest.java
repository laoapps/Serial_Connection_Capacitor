package com.laoapps.plugins.serialconnectioncapacitor;

import android.serialport.SerialPort;
import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24)
public class SerialConnectionCapacitorPluginTest {
    @Mock
    private Bridge bridge;

    @Mock
    private SerialPort serialPort;

    @Mock
    private InputStream inputStream;

    @Mock
    private OutputStream outputStream;

    private SerialConnectionCapacitorPlugin plugin;
    private PluginCall call;
    
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        
        // Create mocks
        call = mock(PluginCall.class);
        bridge = mock(Bridge.class);
        serialPort = mock(SerialPort.class);
        inputStream = mock(InputStream.class);
        outputStream = mock(OutputStream.class);
    
        // Create plugin spy
        plugin = spy(new SerialConnectionCapacitorPlugin());
        
        // Setup default behaviors
        when(serialPort.getInputStream()).thenReturn(inputStream);
        when(serialPort.getOutputStream()).thenReturn(outputStream);
        
        // Setup default call behaviors
        doNothing().when(call).resolve();
        doNothing().when(call).resolve(any(JSObject.class));
        doNothing().when(call).reject(anyString());
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    
public void testOpen_Success() throws Exception {
    // Setup
    setPrivateField(plugin, "serialPort", null);
    String portPath = "/dev/ttyS0";
    int baudRate = 9600;

    // Mock call parameters
    when(call.getString("portPath")).thenReturn(portPath);
    when(call.getInt("baudRate", 9600)).thenReturn(baudRate);

    // Important: Add debug logging
    System.out.println("Setting up test with portPath: " + portPath + " and baudRate: " + baudRate);

    // Mock createSerialPort method
    doReturn(serialPort).when(plugin).createSerialPort(eq(portPath), eq(baudRate));

    // Execute
    plugin.open(call);

    // Verify with detailed logging
    try {
        verify(plugin, times(1)).createSerialPort(eq(portPath), eq(baudRate));
        System.out.println("createSerialPort verification passed");
    } catch (Throwable t) {
        System.out.println("createSerialPort verification failed: " + t.getMessage());
        throw t;
    }

    // Verify other expectations
    verify(call).resolve();
    
    // Verify the serialPort field was set
    SerialPort actualSerialPort = (SerialPort) getPrivateField(plugin, "serialPort");
    assertEquals("SerialPort instance not properly set", serialPort, actualSerialPort);
}

    // Helper method to get private field value
    private Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    @Test
    public void testOpen_PortPathNull() {
        when(call.getString("portPath")).thenReturn(null);
        plugin.open(call);
        verify(call).reject("Port path is required");
    }

    @Test
    public void testOpen_AlreadyOpen() throws Exception {
        setPrivateField(plugin, "serialPort", serialPort);
        when(call.getString("portPath")).thenReturn("/dev/ttyS0");

        plugin.open(call);

        verify(call).reject("Connection already open");
    }

    @Test
    public void testOpen_ThrowsException() throws Exception {
        // Setup
        setPrivateField(plugin, "serialPort", null);
        String portPath = "/dev/ttyS0";
        int baudRate = 9600;

        when(call.getString("portPath")).thenReturn(portPath);
        when(call.getInt("baudRate", 9600)).thenReturn(baudRate);
        
        doThrow(new IOException("Failed to open port"))
            .when(plugin).createSerialPort(portPath, baudRate);

        // Execute
        plugin.open(call);

        // Verify
        verify(call).reject(anyString());
    }

    @Test
    public void testWrite_Success() throws Exception {
        // Setup
        setPrivateField(plugin, "serialPort", serialPort);
        String testData = "Hello";
        when(call.getString("data")).thenReturn(testData);
        when(serialPort.getOutputStream()).thenReturn(outputStream);

        // Execute
        plugin.write(call);

        // Verify
        verify(outputStream).write(testData.getBytes());
        verify(outputStream).flush();
        verify(call).resolve();
    }

    @Test
    public void testWrite_PortNotOpen() throws Exception {
        setPrivateField(plugin, "serialPort", null);
        when(call.getString("data")).thenReturn("Hello");
        
        plugin.write(call);
        
        verify(call).reject("Port not open");
    }

    @Test
    public void testClose_Success() throws Exception {
        setPrivateField(plugin, "serialPort", serialPort);
        doNothing().when(serialPort).close();

        plugin.close(call);

        verify(serialPort).close();
        verify(call).resolve();
    }
}