# serialconnectioncapacitor

for serial connection

## Install

```bash
npm install serialconnectioncapacitor
npx cap sync
```

## API

<docgen-index>

* [`listPorts()`](#listports)
* [`open(...)`](#open)
* [`write(...)`](#write)
* [`read()`](#read)
* [`close()`](#close)
* [Interfaces](#interfaces)

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


### read()

```typescript
read() => Promise<SerialPortReadResult>
```

Reads data from the serial port.

**Returns:** <code>Promise&lt;<a href="#serialportreadresult">SerialPortReadResult</a>&gt;</code>

--------------------


### close()

```typescript
close() => Promise<void>
```

Closes the serial port connection.

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


#### SerialPortReadResult

Result of reading from a serial port.

| Prop       | Type                | Description                                          |
| ---------- | ------------------- | ---------------------------------------------------- |
| **`data`** | <code>string</code> | Data read from the serial port (hex string or text). |

</docgen-api>
