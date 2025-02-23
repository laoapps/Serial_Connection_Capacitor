package com.laoapps.plugins.serialconnectioncapacitor;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 24)
public class SerialConnectionCapacitorPluginTest {
    @Mock
    private UsbManager usbManager;

    @Mock
    private UsbDevice usbDevice;

    @Mock
    private UsbSerialDevice serialPort;

    @Mock
    private PluginCall call;

    @Mock
    private Context mockContext;

    private SerialConnectionCapacitorPlugin plugin;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Create plugin instance
        plugin = spy(new SerialConnectionCapacitorPlugin());
    
        // Mock context and USB manager
        when(plugin.getContext()).thenReturn(mockContext);
        when(mockContext.getSystemService(Context.USB_SERVICE)).thenReturn(usbManager);
    
        // Call load() after mocks are set up
        plugin.load();
    
        // Reset plugin state
        plugin.serialPort = null;
        plugin.isReading = false;
    
        // Default mock behaviors
        when(usbManager.openDevice(usbDevice)).thenReturn(null);
        when(UsbSerialDevice.createUsbSerialDevice(usbDevice, null)).thenReturn(serialPort);
        when(serialPort.open()).thenReturn(true);
        doNothing().when(serialPort).setBaudRate(anyInt());
        doNothing().when(serialPort).setDataBits(anyInt());
        doNothing().when(serialPort).setStopBits(anyInt());
        doNothing().when(serialPort).setParity(anyInt());
        doNothing().when(serialPort).setFlowControl(anyInt());
        doNothing().when(serialPort).write(any(byte[].class));
        doNothing().when(serialPort).close();
        doNothing().when(call).resolve();
        doNothing().when(call).resolve(any(JSObject.class));
        doNothing().when(call).reject(anyString());
    }

    @Test
    public void testListPorts() {
        // Setup
    HashMap<String, UsbDevice> deviceList = new HashMap<>();
    when(usbDevice.getDeviceName()).thenReturn("/dev/bus/usb/001/001");
    deviceList.put("device1", usbDevice);
    when(usbManager.getDeviceList()).thenReturn(deviceList);

    // Execute
    plugin.listPorts(call);

    // Verify
    ArgumentCaptor<JSObject> captor = ArgumentCaptor.forClass(JSObject.class);
    verify(call).resolve(captor.capture());
    JSObject result = captor.getValue();
    JSObject ports = result.getJSObject("ports");
    assertEquals(0, (int) ports.getInteger("/dev/bus/usb/001/001"));
    }

    @Test
    public void testOpen_Success() {
        when(call.getString("portName")).thenReturn("/dev/bus/usb/001/001");
        when(call.getInt("baudRate", 9600)).thenReturn(115200);
        HashMap<String, UsbDevice> deviceList = new HashMap<>();
        when(usbDevice.getDeviceName()).thenReturn("/dev/bus/usb/001/001");
        deviceList.put("device1", usbDevice);
        when(usbManager.getDeviceList()).thenReturn(deviceList);

        plugin.open(call);

        verify(serialPort).setBaudRate(115200);
        verify(call).resolve();
        assertNotNull(plugin.serialPort);
    }

    @Test
    public void testOpen_PortNameNull() {
        when(call.getString("portName")).thenReturn(null);

        plugin.open(call);

        verify(call).reject("Port name is required");
        assertNull(plugin.serialPort);
    }

    @Test
    public void testOpen_DeviceNotFound() {
        when(call.getString("portName")).thenReturn("/dev/bus/usb/001/001");
        when(usbManager.getDeviceList()).thenReturn(new HashMap<>());

        plugin.open(call);

        verify(call).reject("Device not found");
        assertNull(plugin.serialPort);
    }

    @Test
    public void testOpen_AlreadyOpen() {
        plugin.serialPort = serialPort;
        when(call.getString("portName")).thenReturn("/dev/bus/usb/001/001");

        plugin.open(call);

        verify(call).reject("Connection already open");
    }

    @Test
    public void testWrite_Success() {
        plugin.serialPort = serialPort;
        when(call.getString("data")).thenReturn("test data");

        plugin.write(call);

        verify(serialPort).write("test data".getBytes());
        verify(call).resolve();
    }

    @Test
    public void testWrite_PortNotOpen() {
        plugin.serialPort = null;
        when(call.getString("data")).thenReturn("test data");

        plugin.write(call);

        verify(call).reject("Port not open");
    }

    @Test
    public void testWrite_NullData() {
        plugin.serialPort = serialPort;
        when(call.getString("data")).thenReturn(null);

        plugin.write(call);

        verify(call).reject("Invalid data");
    }

    @Test
    public void testStartReading_Success() {
        plugin.serialPort = serialPort;

        plugin.startReading(call);

        verify(call).resolve();
        verify(serialPort).read(any(UsbSerialInterface.UsbReadCallback.class));
        assertTrue(plugin.isReading);
    }

    @Test
    public void testStartReading_PortNotOpen() {
        plugin.serialPort = null;

        plugin.startReading(call);

        verify(call).reject("Port not open");
        assertFalse(plugin.isReading);
    }

    @Test
    public void testStopReading_Success() {
        plugin.serialPort = serialPort;
        plugin.isReading = true;

        plugin.stopReading(call);

        verify(serialPort).close();
        verify(call).resolve();
        assertFalse(plugin.isReading);
    }

    @Test
    public void testClose_Success() {
        plugin.serialPort = serialPort;

        plugin.close(call);

        verify(serialPort).close();
        verify(call).resolve();
        assertNull(plugin.serialPort);
    }

    @Test
    public void testClose_NoPort() {
        plugin.serialPort = null;

        plugin.close(call);

        verify(call).resolve();
    }
}