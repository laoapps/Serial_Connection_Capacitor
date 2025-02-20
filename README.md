# SerialConnectionCapacitor

A Capacitor plugin for serial port communication in web and mobile applications.

## Installation

```bash
npm install serialconnectioncapacitor
npx cap sync
```

### iOS Setup
Add the following to your `Info.plist`:
```xml
<key>UISupportedExternalAccessoryProtocols</key>
<array>
    <string>com.serial.protocol</string>
</array>
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
| `dataReceived` | When serial data is received | `{ data: string }` |
| `readError` | When a read error occurs | `{ error: string }` |
| `connectionError` | When connection fails | `{ error: string }` |

### Event Handling Example

```typescript
import { serialConnectionCapacitor } from 'serialconnectioncapacitor';

// Listen for received data
serialConnectionCapacitor.addListener('dataReceived', (event) => {
  console.log('Received data:', event.data);
});

// Handle errors
serialConnectionCapacitor.addListener('readError', (event) => {
  console.error('Read error:', event.error);
});

// Remove listener when done
serialConnectionCapacitor.removeListener('dataReceived');
```

## API

<docgen-index>

* [`listPorts()`](#listports)
* [`open(...)`](#open)
* [`write(...)`](#write)
* [`startReading()`](#startreading)
* [`stopReading()`](#stopreading)
* [`close()`](#close)
* [`triggerEvent(...)`](#triggerevent)
* [`addListener(SerialPortEventTypes, ...)`](#addlistenerserialporteventtypes-)
* [`removeListener(...)`](#removelistener)
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


### open(...)

```typescript
open(options: SerialPortOptions) => Promise<void>
```

Opens a serial port connection.

| Param         | Type                                                            | Description                                           |
| ------------- | --------------------------------------------------------------- | ----------------------------------------------------- |
| **`options`** | <code><a href="#serialportoptions">SerialPortOptions</a></code> | Connection options including port path and baud rate. |

--------------------


### write(...)

```typescript
write(options: SerialPortWriteOptions) => Promise<void>
```

Writes data to the serial port.

| Param         | Type                                                                      | Description                                   |
| ------------- | ------------------------------------------------------------------------- | --------------------------------------------- |
| **`options`** | <code><a href="#serialportwriteoptions">SerialPortWriteOptions</a></code> | Write options containing the command to send. |

--------------------


### startReading()

```typescript
startReading() => Promise<void>
```

Start reading data from the serial port.

--------------------


### stopReading()

```typescript
stopReading() => Promise<void>
```

Stop reading data from the serial port.

--------------------


### close()

```typescript
close() => Promise<void>
```

Closes the serial port connection.

--------------------


### triggerEvent(...)

```typescript
triggerEvent(eventName: string, data: any) => void
```

Demonstrate trigger Events.

| Param           | Type                |
| --------------- | ------------------- |
| **`eventName`** | <code>string</code> |
| **`data`**      | <code>any</code>    |

--------------------


### addListener(SerialPortEventTypes, ...)

```typescript
addListener(eventName: SerialPortEventTypes, listenerFunc: (event: SerialPortEventData) => void) => void
```

Add listener for serial port events

| Param              | Type                                                                                    | Description                         |
| ------------------ | --------------------------------------------------------------------------------------- | ----------------------------------- |
| **`eventName`**    | <code><a href="#serialporteventtypes">SerialPortEventTypes</a></code>                   | The event to listen for             |
| **`listenerFunc`** | <code>(event: <a href="#serialporteventdata">SerialPortEventData</a>) =&gt; void</code> | Callback function when event occurs |

--------------------


### removeListener(...)

```typescript
removeListener(eventName: SerialPortEventTypes) => void
```

Remove listener for serial port events

| Param           | Type                                                                  | Description                     |
| --------------- | --------------------------------------------------------------------- | ------------------------------- |
| **`eventName`** | <code><a href="#serialporteventtypes">SerialPortEventTypes</a></code> | The event to stop listening for |

--------------------


### Interfaces


#### SerialPortListResult

Result of listing available serial ports.

| Prop        | Type                                    | Description             |
| ----------- | --------------------------------------- | ----------------------- |
| **`ports`** | <code>{ [key: string]: number; }</code> | Available serial ports. |


#### SerialPortOptions

Options for opening a serial port connection.

| Prop           | Type                | Description                                               |
| -------------- | ------------------- | --------------------------------------------------------- |
| **`portPath`** | <code>string</code> | Path to the serial port (e.g., `/dev/ttyUSB0` or `COM3`). |
| **`baudRate`** | <code>number</code> | Baud rate for the serial port connection.                 |


#### SerialPortWriteOptions

Options for writing to a serial port.

| Prop          | Type                | Description                                              |
| ------------- | ------------------- | -------------------------------------------------------- |
| **`command`** | <code>string</code> | Command to send to the serial port (hex string or text). |


#### SerialPortEventData

Event data types for serial port events

| Prop          | Type                |
| ------------- | ------------------- |
| **`message`** | <code>string</code> |
| **`data`**    | <code>string</code> |
| **`error`**   | <code>string</code> |


### Type Aliases


#### SerialPortEventTypes

<code>'portsListed' | 'connectionOpened' | 'connectionClosed' | 'writeSuccess' | 'dataReceived' | 'readingStarted' | 'readingStopped' | 'listError' | 'connectionError' | 'writeError' | 'readError' | 'deviceError'</code>

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

      const portPath = Object.keys(ports)[0];
      await serialConnectionCapacitor.open({
        portPath,
        baudRate: 115200
      });

      this.setupListeners();
      return true;
    } catch (error) {
      console.error('Initialization failed:', error);
      return false;
    }
  }

  private setupListeners() {
    serialConnectionCapacitor.addListener('dataReceived', (event) => {
      console.log('Received data:', event.data);
    });

    serialConnectionCapacitor.addListener('readError', (event) => {
      console.error('Read error:', event.error);
    });

    serialConnectionCapacitor.addListener('connectionError', (event) => {
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
