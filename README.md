# SerialConnectionCapacitor

A Capacitor plugin for serial port communication in web and mobile applications.

## Installation

```bash
npm install serialconnectioncapacitor
npx cap sync
```
```
for Android Only , 24 and above, serial port need a rooted android 
capacitor 5.0.1 special version for VMC using polling and timing method
if you need another version please find the versions 
it uses native libs libserial_port.so , I have built for many architures , thanks grok that guided me along!
```
### Android Setup
Add the following permission to your `AndroidManifest.xml`:
```xml
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.USB_PERMISSION" />
```

## Events

The plugin emits the following events:

| Event Name | Description | Data Structure |
|------------|-------------|----------------|
| `connectionOpened` | When connection is established | `{ message: string }` |
| `connectionError` | When connection fails | `{ error: string }` |
| `nativeWriteSuccess` | When data is written successfully | `{ message: string }` |
| `writeError` | When write operation fails | `{ error: string }` |
| `dataReceived` | When data is received | `{ data: string }` |
| `readError` | When read operation fails | `{ error: string }` |
| `readingStopped` | When reading is stopped | `{ message: string }` |
| `connectionClosed` | When connection is closed | `{ message: string }` |

### Event Handling Example

```typescript
import { serialConnectionCapacitor } from 'serialconnectioncapacitor';

// Connection events
serialConnectionCapacitor.addEvent('connectionOpened', (event) => {
  console.log('Connection established:', event.message);
});

// Data events
serialConnectionCapacitor.addEvent('dataReceived', (event) => {
  console.log('Received data:', event.data);
});

// Error handling
serialConnectionCapacitor.addEvent('readError', (event) => {
  console.error('Read error:', event.error);
});

serialConnectionCapacitor.addEvent('connectionError', (event) => {
  console.error('Connection error:', event.error);
});

// Cleanup
serialConnectionCapacitor.addEvent('connectionClosed', (event) => {
  console.log('Connection closed:', event.message);
});
```

## API

<docgen-index>

* [`listPorts()`](#listports)
* [`listUSBDevices()`](#listusbdevices)
* [`openSerial(...)`](#openserial)
* [`openUSB(...)`](#openusb)
* [`openUsbSerial(...)`](#openusbserial)
* [`sendNV9Command(...)`](#sendnv9command)
* [`checkUSBStatus()`](#checkusbstatus)
* [`getUSBDeviceInfo(...)`](#getusbdeviceinfo)
* [`testUSBConnection(...)`](#testusbconnection)
* [`requestUSBPermission(...)`](#requestusbpermission)
* [`openSerialEssp(...)`](#openserialessp)
* [`write(...)`](#write)
* [`writeVMC(...)`](#writevmc)
* [`writeMT102(...)`](#writemt102)
* [`writeADH814(...)`](#writeadh814)
* [`writeEssp(...)`](#writeessp)
* [`startReading()`](#startreading)
* [`startReadingVMC()`](#startreadingvmc)
* [`startReadingMT102()`](#startreadingmt102)
* [`startReadingADH814()`](#startreadingadh814)
* [`startReadingEssp()`](#startreadingessp)
* [`stopReading()`](#stopreading)
* [`stopNV9Polling()`](#stopnv9polling)
* [`close()`](#close)
* [`requestID(...)`](#requestid)
* [`scanDoorFeedback(...)`](#scandoorfeedback)
* [`pollStatus(...)`](#pollstatus)
* [`setTemperature(...)`](#settemperature)
* [`startMotor(...)`](#startmotor)
* [`acknowledgeResult(...)`](#acknowledgeresult)
* [`startMotorCombined(...)`](#startmotorcombined)
* [`startPolling(...)`](#startpolling)
* [`stopPolling()`](#stoppolling)
* [`querySwap(...)`](#queryswap)
* [`setSwap(...)`](#setswap)
* [`switchToTwoWireMode(...)`](#switchtotwowiremode)
* [`addListener(SerialPortEventTypes, ...)`](#addlistenerserialporteventtypes-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Plugin interface for serial port communication.

### listPorts()

```typescript
listPorts() => Promise<{ ports: { [key: string]: number; }; }>
```

Lists available serial ports.

**Returns:** <code>Promise&lt;{ ports: { [key: string]: number; }; }&gt;</code>

--------------------


### listUSBDevices()

```typescript
listUSBDevices() => Promise<{ devices: string; count: number; }>
```

Lists only USB devices

**Returns:** <code>Promise&lt;{ devices: string; count: number; }&gt;</code>

--------------------


### openSerial(...)

```typescript
openSerial(options: SerialPortOptions) => Promise<{ success: boolean; message: string; portName: string; baudRate: number; connectionType: string; isNV9?: boolean; }>
```

Opens a serial port connection.

| Param         | Type                                                            | Description                                           |
| ------------- | --------------------------------------------------------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#serialportoptions">SerialPortOptions</a></code> | Connection options including port path and baud rate. |

**Returns:** <code>Promise&lt;{ success: boolean; message: string; portName: string; baudRate: number; connectionType: string; isNV9?: boolean; }&gt;</code>

--------------------


### openUSB(...)

```typescript
openUSB(options: { portName: string; }) => Promise<{ success: boolean; message: string; portName: string; connectionType: string; }>
```

Opens a USB serial port connection explicitly.

| Param         | Type                               | Description                             |
| ------------- | ---------------------------------- | --------------------------------------- |
| **`options`** | <code>{ portName: string; }</code> | Connection options including port name. |

**Returns:** <code>Promise&lt;{ success: boolean; message: string; portName: string; connectionType: string; }&gt;</code>

--------------------


### openUsbSerial(...)

```typescript
openUsbSerial(options: SerialPortOptions) => Promise<any>
```

Opens a USB serial port connection.

| Param         | Type                                                            | Description                                           |
| ------------- | --------------------------------------------------------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#serialportoptions">SerialPortOptions</a></code> | Connection options including port path and baud rate. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### sendNV9Command(...)

```typescript
sendNV9Command(options: NV9CommandOptions) => Promise<{ success: boolean; event: string; command: string; data?: any; error?: string; }>
```

Sends an NV9 SSP command to the USB device

| Param         | Type                                                            | Description         |
| ------------- | --------------------------------------------------------------- | ------------------- |
| **`options`** | <code><a href="#nv9commandoptions">NV9CommandOptions</a></code> | NV9 command options |

**Returns:** <code>Promise&lt;{ success: boolean; event: string; command: string; data?: any; error?: string; }&gt;</code>

--------------------


### checkUSBStatus()

```typescript
checkUSBStatus() => Promise<{ usbManagerAvailable: boolean; totalUsbDevices: number; devices?: string; usbSerialPortOpen: boolean; isNV9Mode: boolean; isPolling: boolean; serialPortOpen: boolean; }>
```

Checks USB connection status

**Returns:** <code>Promise&lt;{ usbManagerAvailable: boolean; totalUsbDevices: number; devices?: string; usbSerialPortOpen: boolean; isNV9Mode: boolean; isPolling: boolean; serialPortOpen: boolean; }&gt;</code>

--------------------


### getUSBDeviceInfo(...)

```typescript
getUSBDeviceInfo(options: { portName: string; }) => Promise<any>
```

Gets detailed USB device info

| Param         | Type                               | Description |
| ------------- | ---------------------------------- | ----------- |
| **`options`** | <code>{ portName: string; }</code> | Port name   |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### testUSBConnection(...)

```typescript
testUSBConnection(options: { portName: string; }) => Promise<any>
```

Tests USB connection with a specific device

| Param         | Type                               | Description |
| ------------- | ---------------------------------- | ----------- |
| **`options`** | <code>{ portName: string; }</code> | Port name   |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### requestUSBPermission(...)

```typescript
requestUSBPermission(options: { portName: string; }) => Promise<any>
```

Requests USB permission for a device

| Param         | Type                               | Description |
| ------------- | ---------------------------------- | ----------- |
| **`options`** | <code>{ portName: string; }</code> | Port name   |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### openSerialEssp(...)

```typescript
openSerialEssp(options: SerialPortOptions) => Promise<any>
```

Opens a USB serial port connection for ESSP.

| Param         | Type                                                            | Description                                           |
| ------------- | --------------------------------------------------------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#serialportoptions">SerialPortOptions</a></code> | Connection options including port path and baud rate. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### write(...)

```typescript
write(options: { data: string; }) => Promise<any>
```

Writes data to the serial port.

| Param         | Type                           | Description                                   |
| ------------- | ------------------------------ | --------------------------------------------- |
| **`options`** | <code>{ data: string; }</code> | Write options containing the command to send. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### writeVMC(...)

```typescript
writeVMC(options: { data: string; }) => Promise<any>
```

Writes data to the serial port for VMC.

| Param         | Type                           | Description                                   |
| ------------- | ------------------------------ | --------------------------------------------- |
| **`options`** | <code>{ data: string; }</code> | Write options containing the command to send. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### writeMT102(...)

```typescript
writeMT102(options: { data: string; }) => Promise<any>
```

Writes data to the serial port for VMC.

| Param         | Type                           | Description                                   |
| ------------- | ------------------------------ | --------------------------------------------- |
| **`options`** | <code>{ data: string; }</code> | Write options containing the command to send. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### writeADH814(...)

```typescript
writeADH814(options: { data: string; }) => Promise<any>
```

Writes data to the serial port for ADH814.

| Param         | Type                           | Description                                   |
| ------------- | ------------------------------ | --------------------------------------------- |
| **`options`** | <code>{ data: string; }</code> | Write options containing the command to send. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### writeEssp(...)

```typescript
writeEssp(options: { data: string; }) => Promise<any>
```

Writes data to the serial port for ESSP.

| Param         | Type                           | Description                                   |
| ------------- | ------------------------------ | --------------------------------------------- |
| **`options`** | <code>{ data: string; }</code> | Write options containing the command to send. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startReading()

```typescript
startReading() => Promise<any>
```

Starts reading data from the serial port.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startReadingVMC()

```typescript
startReadingVMC() => Promise<any>
```

Starts reading data from the serial port for VMC.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startReadingMT102()

```typescript
startReadingMT102() => Promise<any>
```

Starts reading data from the serial port for MT102.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startReadingADH814()

```typescript
startReadingADH814() => Promise<any>
```

Starts reading data from the serial port for ADH814.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startReadingEssp()

```typescript
startReadingEssp() => Promise<any>
```

Starts reading ESSP data from the serial port.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### stopReading()

```typescript
stopReading() => Promise<any>
```

Stops reading data from the serial port.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### stopNV9Polling()

```typescript
stopNV9Polling() => Promise<{ success: boolean; message: string; }>
```

Stops NV9 polling

**Returns:** <code>Promise&lt;{ success: boolean; message: string; }&gt;</code>

--------------------


### close()

```typescript
close() => Promise<{ success: boolean; message: string; }>
```

Closes the serial port connection.

**Returns:** <code>Promise&lt;{ success: boolean; message: string; }&gt;</code>

--------------------


### requestID(...)

```typescript
requestID(options: { address: number; }) => Promise<any>
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ address: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### scanDoorFeedback(...)

```typescript
scanDoorFeedback(options: { address: number; }) => Promise<any>
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ address: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### pollStatus(...)

```typescript
pollStatus(options: { address: number; }) => Promise<any>
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ address: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### setTemperature(...)

```typescript
setTemperature(options: { address: number; mode: number; tempValue: number; }) => Promise<any>
```

| Param         | Type                                                               |
| ------------- | ------------------------------------------------------------------ |
| **`options`** | <code>{ address: number; mode: number; tempValue: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startMotor(...)

```typescript
startMotor(options: { address: number; motorNumber: number; }) => Promise<any>
```

| Param         | Type                                                   |
| ------------- | ------------------------------------------------------ |
| **`options`** | <code>{ address: number; motorNumber: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### acknowledgeResult(...)

```typescript
acknowledgeResult(options: { address: number; }) => Promise<any>
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ address: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startMotorCombined(...)

```typescript
startMotorCombined(options: { address: number; motorNumber1: number; motorNumber2: number; }) => Promise<any>
```

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code>{ address: number; motorNumber1: number; motorNumber2: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startPolling(...)

```typescript
startPolling(options: { address: number; interval: number; }) => Promise<any>
```

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code>{ address: number; interval: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### stopPolling()

```typescript
stopPolling() => Promise<any>
```

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### querySwap(...)

```typescript
querySwap(options: { address: number; }) => Promise<any>
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ address: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### setSwap(...)

```typescript
setSwap(options: { address: number; swapEnabled: number; }) => Promise<any>
```

| Param         | Type                                                   |
| ------------- | ------------------------------------------------------ |
| **`options`** | <code>{ address: number; swapEnabled: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### switchToTwoWireMode(...)

```typescript
switchToTwoWireMode(options: { address: number; }) => Promise<any>
```

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ address: number; }</code> |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### addListener(SerialPortEventTypes, ...)

```typescript
addListener(eventName: SerialPortEventTypes, listenerFunc: (event: any) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Add listener for serial port events.

| Param              | Type                                                                  | Description                          |
| ------------------ | --------------------------------------------------------------------- | ------------------------------------ |
| **`eventName`**    | <code><a href="#serialporteventtypes">SerialPortEventTypes</a></code> | The event to listen for.             |
| **`listenerFunc`** | <code>(event: any) =&gt; void</code>                                  | Callback function when event occurs. |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

Remove all listeners

--------------------


### Interfaces


#### SerialPortOptions

Options for opening a serial port connection.

| Prop                 | Type                 | Description                                               |
| -------------------- | -------------------- | --------------------------------------------------------- |
| **`portName`**       | <code>string</code>  | Path to the serial port (e.g., `/dev/ttyUSB0` or `COM3`). |
| **`baudRate`**       | <code>number</code>  | Baud rate for the serial port connection.                 |
| **`dataBits`**       | <code>number</code>  |                                                           |
| **`stopBits`**       | <code>number</code>  |                                                           |
| **`parity`**         | <code>string</code>  |                                                           |
| **`bufferSize`**     | <code>number</code>  |                                                           |
| **`flags`**          | <code>number</code>  |                                                           |
| **`isNV9`**          | <code>boolean</code> | Enable NV9 mode for SSP protocol                          |
| **`autoConnectUSB`** | <code>boolean</code> | Auto-connect to USB NV9 after serial connection           |


#### NV9CommandOptions

Options for NV9 SSP commands

| Prop          | Type                | Description                                                           |
| ------------- | ------------------- | --------------------------------------------------------------------- |
| **`command`** | <code>string</code> | Command name (e.g., 'POLL', 'ENABLE', 'DISABLE', 'GET_SERIAL_NUMBER') |
| **`args`**    | <code>string</code> | Optional arguments for the command as JSON string                     |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### SerialPortEventTypes

Event types for serial port events

<code>'portsListed' | 'serialOpened' | 'usbSerialOpened' | 'connectionClosed' | 'usbWriteSuccess' | 'dataReceived' | 'readingStarted' | 'readingStopped' | 'serialWriteSuccess' | 'commandAcknowledged' | 'commandQueued' | 'adh814Response' | 'nv9Event' | 'usbDeviceEvent'</code>

</docgen-api>

## Example Usage

```typescript
import { serialConnectionCapacitor } from 'serialconnectioncapacitor';

class SerialPortManager {
  private isReading = false;

  async initialize() {
    try {
      const { ports } = await serialConnectionCapacitor.listPorts();
      if (Object.keys(ports).length === 0) {
        throw new Error('No serial ports available');
      }

      const portName = Object.keys(ports)[0];
      await serialConnectionCapacitor.open({
        portName,
        baudRate: 115200
      });

      this.setupEvents();
      return true;
    } catch (error) {
      console.error('Initialization failed:', error);
      return false;
    }
  }

  private setupEvents() {
    // Connection events
    serialConnectionCapacitor.addEvent('connectionOpened', (event) => {
      console.log('Connected:', event.message);
    });

    serialConnectionCapacitor.addEvent('connectionClosed', (event) => {
      console.log('Disconnected:', event.message);
    });

    // Data events
    serialConnectionCapacitor.addEvent('dataReceived', (event) => {
      console.log('Received:', event.data);
    });

    serialConnectionCapacitor.addEvent('nativeWriteSuccess', (event) => {
      console.log('Write successful:', event.message);
    });

    // Error events
    serialConnectionCapacitor.addEvent('readError', (event) => {
      console.error('Read error:', event.error);
    });

    serialConnectionCapacitor.addEvent('connectionError', (event) => {
      console.error('Connection error:', event.error);
    });
  }

  async startReading() {
    if (!this.isReading) {
      await serialConnectionCapacitor.startReading();
      this.isReading = true;
    }
  }

  async stopReading() {
    if (this.isReading) {
      await serialConnectionCapacitor.stopReading();
      this.isReading = false;
    }
  }

  async sendCommand(command: string) {
    await serialConnectionCapacitor.write({ command });
  }

  async cleanup() {
    await this.stopReading();
    await serialConnectionCapacitor.close();
  }
}

// Usage
const serialManager = new SerialPortManager();
await serialManager.initialize();
await serialManager.startReading();
await serialManager.sendCommand('TEST');
// ... later
await serialManager.cleanup();
```

## License

MIT
