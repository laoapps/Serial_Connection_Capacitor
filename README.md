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
* [`openSerial(...)`](#openserial)
* [`openUsbSerial(...)`](#openusbserial)
* [`openSerialEssp(...)`](#openserialessp)
* [`write(...)`](#write)
* [`writeVMC(...)`](#writevmc)
* [`writeEssp(...)`](#writeessp)
* [`startReading()`](#startreading)
* [`startReadingVMC()`](#startreadingvmc)
* [`startReadingEssp()`](#startreadingessp)
* [`stopReading()`](#stopreading)
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
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Plugin interface for serial port communication.

### listPorts()

```typescript
listPorts() => Promise<SerialPortListResult>
```

Lists available serial ports.

**Returns:** <code>Promise&lt;<a href="#serialportlistresult">SerialPortListResult</a>&gt;</code>

--------------------


### openSerial(...)

```typescript
openSerial(options: SerialPortOptions) => Promise<any>
```

Opens a serial port connection.

| Param         | Type                                                            | Description                                           |
| ------------- | --------------------------------------------------------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#serialportoptions">SerialPortOptions</a></code> | Connection options including port path and baud rate. |

**Returns:** <code>Promise&lt;any&gt;</code>

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
write(options: SerialPortWriteOptions) => Promise<any>
```

Writes data to the serial port.

| Param         | Type                                                                      | Description                                   |
| ------------- | ------------------------------------------------------------------------- | --------------------------------------------- |
| **`options`** | <code><a href="#serialportwriteoptions">SerialPortWriteOptions</a></code> | Write options containing the command to send. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### writeVMC(...)

```typescript
writeVMC(options: SerialPortWriteOptions) => Promise<any>
```

Writes data to the serial port for VMC.

| Param         | Type                                                                      | Description                                   |
| ------------- | ------------------------------------------------------------------------- | --------------------------------------------- |
| **`options`** | <code><a href="#serialportwriteoptions">SerialPortWriteOptions</a></code> | Write options containing the command to send. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### writeEssp(...)

```typescript
writeEssp(options: SerialPortWriteOptions) => Promise<any>
```

Writes data to the serial port for ESSP.

| Param         | Type                                                                      | Description                                   |
| ------------- | ------------------------------------------------------------------------- | --------------------------------------------- |
| **`options`** | <code><a href="#serialportwriteoptions">SerialPortWriteOptions</a></code> | Write options containing the command to send. |

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


### close()

```typescript
close() => Promise<any>
```

Closes the serial port connection.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### requestID(...)

```typescript
requestID(options: { address: number; }) => Promise<any>
```

Requests the device ID for ADH814.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ address: number; }</code> | Address of the device. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### scanDoorFeedback(...)

```typescript
scanDoorFeedback(options: { address: number; }) => Promise<any>
```

Scans door feedback for ADH814.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ address: number; }</code> | Address of the device. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### pollStatus(...)

```typescript
pollStatus(options: { address: number; }) => Promise<any>
```

Polls status for ADH814.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ address: number; }</code> | Address of the device. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### setTemperature(...)

```typescript
setTemperature(options: { address: number; mode: number; tempValue: number; }) => Promise<any>
```

Sets temperature for ADH814.

| Param         | Type                                                               | Description                           |
| ------------- | ------------------------------------------------------------------ | ------------------------------------- |
| **`options`** | <code>{ address: number; mode: number; tempValue: number; }</code> | Address, mode, and temperature value. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startMotor(...)

```typescript
startMotor(options: { address: number; motorNumber: number; }) => Promise<any>
```

Starts a motor for ADH814.

| Param         | Type                                                   | Description               |
| ------------- | ------------------------------------------------------ | ------------------------- |
| **`options`** | <code>{ address: number; motorNumber: number; }</code> | Address and motor number. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### acknowledgeResult(...)

```typescript
acknowledgeResult(options: { address: number; }) => Promise<any>
```

Acknowledges result for ADH814.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ address: number; }</code> | Address of the device. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startMotorCombined(...)

```typescript
startMotorCombined(options: { address: number; motorNumber1: number; motorNumber2: number; }) => Promise<any>
```

Starts combined motors for ADH814.

| Param         | Type                                                                          | Description                    |
| ------------- | ----------------------------------------------------------------------------- | ------------------------------ |
| **`options`** | <code>{ address: number; motorNumber1: number; motorNumber2: number; }</code> | Address and two motor numbers. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### startPolling(...)

```typescript
startPolling(options: { address: number; interval: number; }) => Promise<any>
```

Starts polling for ADH814 status.

| Param         | Type                                                | Description                   |
| ------------- | --------------------------------------------------- | ----------------------------- |
| **`options`** | <code>{ address: number; interval: number; }</code> | Address and polling interval. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### stopPolling()

```typescript
stopPolling() => Promise<any>
```

Stops polling for ADH814.

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### querySwap(...)

```typescript
querySwap(options: { address: number; }) => Promise<any>
```

Queries row/column swap status for ADH814.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ address: number; }</code> | Address of the device. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### setSwap(...)

```typescript
setSwap(options: { address: number; swapEnabled: number; }) => Promise<any>
```

Sets row/column swap for ADH814.

| Param         | Type                                                   | Description                                     |
| ------------- | ------------------------------------------------------ | ----------------------------------------------- |
| **`options`** | <code>{ address: number; swapEnabled: number; }</code> | Address and swap enabled status (0x00 or 0x01). |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### switchToTwoWireMode(...)

```typescript
switchToTwoWireMode(options: { address: number; }) => Promise<any>
```

Switches to two-wire mode for ADH814.

| Param         | Type                              | Description            |
| ------------- | --------------------------------- | ---------------------- |
| **`options`** | <code>{ address: number; }</code> | Address of the device. |

**Returns:** <code>Promise&lt;any&gt;</code>

--------------------


### addListener(SerialPortEventTypes, ...)

```typescript
addListener(eventName: SerialPortEventTypes, listenerFunc: (...args: any[]) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

Add listener for serial port events.

| Param              | Type                                                                  | Description                          |
| ------------------ | --------------------------------------------------------------------- | ------------------------------------ |
| **`eventName`**    | <code><a href="#serialporteventtypes">SerialPortEventTypes</a></code> | The event to listen for.             |
| **`listenerFunc`** | <code>(...args: any[]) =&gt; void</code>                              | Callback function when event occurs. |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### Interfaces


#### SerialPortListResult

Result of listing available serial ports.

| Prop        | Type                                    | Description             |
| ----------- | --------------------------------------- | ----------------------- |
| **`ports`** | <code>{ [key: string]: number; }</code> | Available serial ports. |


#### SerialPortOptions

Options for opening a serial port connection.

| Prop             | Type                | Description                                               |
| ---------------- | ------------------- | --------------------------------------------------------- |
| **`portName`**   | <code>string</code> | Path to the serial port (e.g., `/dev/ttyUSB0` or `COM3`). |
| **`baudRate`**   | <code>number</code> | Baud rate for the serial port connection.                 |
| **`dataBits`**   | <code>number</code> |                                                           |
| **`stopBits`**   | <code>number</code> |                                                           |
| **`parity`**     | <code>string</code> |                                                           |
| **`bufferSize`** | <code>number</code> |                                                           |
| **`flags`**      | <code>number</code> |                                                           |


#### SerialPortWriteOptions

Options for writing to a serial port.

| Prop       | Type                | Description                                              |
| ---------- | ------------------- | -------------------------------------------------------- |
| **`data`** | <code>string</code> | Command to send to the serial port (hex string or text). |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


### Type Aliases


#### SerialPortEventTypes

Event types for serial port events

<code>'portsListed' | 'serialOpened' | 'usbSerialOpened' | 'connectionClosed' | 'usbWriteSuccess' | 'dataReceived' | 'readingStarted' | 'readingStopped' | 'serialWriteSuccess' | 'commandAcknowledged' | 'commandQueued' | 'adh814Response'</code>

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
