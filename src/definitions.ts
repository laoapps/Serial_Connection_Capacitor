import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Options for opening a serial port connection.
 */

export interface SerialPortOptions {
  /**
   * Path to the serial port (e.g., `/dev/ttyUSB0` or `COM3`).
   */
  portName: string;

  /**
   * Baud rate for the serial port connection.
   */
  baudRate: number;
  dataBits?: number;//8
  stopBits?: number,//1
  parity?: string,//'none'
  bufferSize?: number,//0
  flags?: number,//0
}

/**
 * Options for writing to a serial port.
 */
export interface SerialPortWriteOptions {
  /**
   * Command to send to the serial port (hex string or text).
   */
  data: string;
}

/**
 * Result of reading from a serial port.
 */
export interface SerialPortReadResult {
  /**
   * Data read from the serial port (hex string or text).
   */
  data: string;
}

/**
 * Result of listing available serial ports.
 */
export interface SerialPortListResult {
  /**
   * Available serial ports.
   */
  ports: { [key: string]: number };
}

/**
 * Event data types for serial port events
 */
export interface SerialPortEventData {
  message?: string;
  data?: string;
  error?: string;
}

export type SerialPortEventTypes =
  | 'portsListed'
  | 'serialOpened'
  | 'usbSerialOpened'
  | 'connectionClosed'
  | 'usbWriteSuccess'
  | 'dataReceived'
  | 'readingStarted'
  | 'readingStopped'
  | 'serialWriteSuccess'
  | 'commandAcknowledged'
  | 'commandQueued';

/**
 * Plugin interface for serial port communication.
 */
export interface SerialPortPlugin {
  /**
   * Lists available serial ports.
   * @returns Promise that resolves with the list of available ports.
   */
  listPorts(): Promise<SerialPortListResult>;

  /**
   * Opens a serial port connection.
   * @param options Connection options including port path and baud rate.
   */
  openSerial(options: SerialPortOptions): Promise<any>;
  /**
  * Opens a USB serial port connection.
  * @param options Connection options including port path and baud rate.
  */
  openUsbSerial(options: SerialPortOptions): Promise<any>;
  /**
  * Opens a USB serial port connection.
  * @param options Connection options including port path and baud rate.
  */
  openSerialEssp(options: SerialPortOptions): Promise<any>;


  /**
   * Writes data to the serial port.
   * @param options Write options containing the command to send.
   */

  write(options: SerialPortWriteOptions): Promise<any>;
  /**
 * Writes data to the serial port.
 * @param options Write options containing the command to send.
 */

  writeVMC(options: SerialPortWriteOptions): Promise<any>;
    /**
 * Writes data to the serial port.
 * @param options Write options containing the command to send.
 */

    writeEssp(options: SerialPortWriteOptions): Promise<any>;

  /**
   * Start reading data from the serial port.
   * @returns Promise that resolves with the read data.
   */
  startReading(): Promise<any>;
  /**
  * Start reading data from the serial port.
  * @returns Promise that resolves with the read data.
  */
  startReadingVMC(): Promise<any>;
 /**
  * Start reading  essp data from the serial port.
  * @returns Promise that resolves with the read data.
  */
  startReadingEssp(): Promise<any>;
  /**
   * Stop reading data from the serial port.
   * @returns Promise that resolves with the read data.
   */
  stopReading(): Promise<any>;

  /**
   * Closes the serial port connection.
   */
  close(): Promise<any>;

  /**
   * Add listener for serial port events
   * @param eventName The event to listen for
   * @param listenerFunc Callback function when event occurs
   * @returns Promise with the listener handle
   */
  addListener(eventName: SerialPortEventTypes, listenerFunc: (...args: any[]) => void): Promise<PluginListenerHandle> & PluginListenerHandle;

}