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
  dataBits?: number; // 8
  stopBits?: number; // 1
  parity?: string; // 'none'
  bufferSize?: number; // 0
  flags?: number; // 0
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
  data?: any; // Structured data for ADH814 responses
  error?: string;
}

/**
 * Event types for serial port events
 */
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
  | 'commandQueued'
  | 'adh814Response';

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
   * Opens a USB serial port connection for ESSP.
   * @param options Connection options including port path and baud rate.
   */
  openSerialEssp(options: SerialPortOptions): Promise<any>;

  /**
   * Writes data to the serial port.
   * @param options Write options containing the command to send.
   */
  write(options: SerialPortWriteOptions): Promise<any>;

  /**
   * Writes data to the serial port for VMC.
   * @param options Write options containing the command to send.
   */
  writeVMC(options: SerialPortWriteOptions): Promise<any>;

  /**
   * Writes data to the serial port for ESSP.
   * @param options Write options containing the command to send.
   */
  writeEssp(options: SerialPortWriteOptions): Promise<any>;

  /**
   * Starts reading data from the serial port.
   * @returns Promise that resolves when reading starts.
   */
  startReading(): Promise<any>;

  /**
   * Starts reading data from the serial port for VMC.
   * @returns Promise that resolves when reading starts.
   */
  startReadingVMC(): Promise<any>;

  /**
   * Starts reading ESSP data from the serial port.
   * @returns Promise that resolves when reading starts.
   */
  startReadingEssp(): Promise<any>;

  /**
   * Stops reading data from the serial port.
   * @returns Promise that resolves when reading stops.
   */
  stopReading(): Promise<any>;

  /**
   * Closes the serial port connection.
   */
  close(): Promise<any>;

  /**
   * Requests the device ID for ADH814.
   * @param options Address of the device.
   */
  requestID(options: { address: number }): Promise<any>;

  /**
   * Scans door feedback for ADH814.
   * @param options Address of the device.
   */
  scanDoorFeedback(options: { address: number }): Promise<any>;

  /**
   * Polls status for ADH814.
   * @param options Address of the device.
   */
  pollStatus(options: { address: number }): Promise<any>;

  /**
   * Sets temperature for ADH814.
   * @param options Address, mode, and temperature value.
   */
  setTemperature(options: { address: number; mode: number; tempValue: number }): Promise<any>;

  /**
   * Starts a motor for ADH814.
   * @param options Address and motor number.
   */
  startMotor(options: { address: number; motorNumber: number }): Promise<any>;

  /**
   * Acknowledges result for ADH814.
   * @param options Address of the device.
   */
  acknowledgeResult(options: { address: number }): Promise<any>;

  /**
   * Starts combined motors for ADH814.
   * @param options Address and two motor numbers.
   */
  startMotorCombined(options: { address: number; motorNumber1: number; motorNumber2: number }): Promise<any>;

  /**
   * Starts polling for ADH814 status.
   * @param options Address and polling interval.
   */
  startPolling(options: { address: number; interval: number }): Promise<any>;

  /**
   * Stops polling for ADH814.
   */
  stopPolling(): Promise<any>;

  /**
   * Add listener for serial port events.
   * @param eventName The event to listen for.
   * @param listenerFunc Callback function when event occurs.
   * @returns Promise with the listener handle.
   */
  addListener(eventName: SerialPortEventTypes, listenerFunc: (...args: any[]) => void): Promise<PluginListenerHandle> & PluginListenerHandle;
}