package com.laoapps.plugins.serialconnectioncapacitor;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.HashMap;

import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SerialConnectionCapacitorPluginTest {
    @Mock
    private Bridge bridge;

    @Mock
    private Context context;

    private SerialConnectionCapacitorPlugin plugin;
    private UsbManager usbManager;
    private SerialConnectionCapacitor serialConnection;
    private PluginCall call;

    @Before
    public void setUp() throws Exception {
        usbManager = mock(UsbManager.class);
        serialConnection = mock(SerialConnectionCapacitor.class);
        call = mock(PluginCall.class);
        bridge = mock(Bridge.class); // Explicitly mock bridge
        context = mock(Context.class); // Explicitly mock context
    
        when(context.getSystemService(Context.USB_SERVICE)).thenReturn(usbManager);
        when(bridge.getContext()).thenReturn(context);
    
        plugin = new SerialConnectionCapacitorPlugin();
        plugin.setBridge(bridge); // Line 44
        plugin.load();
    
        setPrivateField(plugin, "usbManager", usbManager);
        setPrivateField(plugin, "serialConnection", serialConnection);
    
        doNothing().when(call).resolve();
        doNothing().when(call).resolve(any(JSObject.class));
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testListPorts() {
        HashMap<String, UsbDevice> deviceList = new HashMap<>();
        UsbDevice device = mock(UsbDevice.class);
        when(device.getDeviceName()).thenReturn("device1");
        when(device.getDeviceId()).thenReturn(1);
        deviceList.put("device1", device);
        when(usbManager.getDeviceList()).thenReturn(deviceList);

        plugin.listPorts(call);

        verify(call).resolve(any(JSObject.class));
    }

    @Test
    public void testOpen() {
        when(call.getString("portPath")).thenReturn("/dev/ttyUSB0");
        when(call.getInt("baudRate", 9600)).thenReturn(9600);
        when(serialConnection.openConnection("/dev/ttyUSB0", 9600)).thenReturn(true);

        plugin.open(call);

        verify(call).resolve();
    }

    @Test
    public void testWrite() throws Exception {
        when(call.getString("data")).thenReturn("Hello, World!");
        when(serialConnection.getOutputStream()).thenReturn(mock(java.io.OutputStream.class));

        plugin.write(call);

        verify(call).resolve();
    }

    @Test
    public void testStartReading() {
        when(serialConnection.getInputStream()).thenReturn(mock(java.io.InputStream.class));

        plugin.startReading(call);

        verify(call).resolve();
    }

    @Test
    public void testStopReading() throws Exception {
        when(serialConnection.getInputStream()).thenReturn(mock(java.io.InputStream.class));

        plugin.stopReading(call);

        verify(call).resolve();
    }

    @Test
    public void testClose() {
        plugin.close(call);

        verify(call).resolve();
    }
}