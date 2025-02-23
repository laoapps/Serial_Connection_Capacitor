import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Options for opening a serial port connection.
 */
export interface SerialPortOptions {
  /**
   * Path to the serial port (e.g., `/dev/ttyUSB0` or `COM3`).
   */
  portPath: string;

  /**
   * Baud rate for the serial port connection.
   */
  baudRate: number;
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
  | 'connectionOpened'
  | 'connectionClosed'
  | 'writeSuccess'
  | 'dataReceived'
  | 'readingStarted'
  | 'readingStopped'
  | 'listError'
  | 'connectionError'
  | 'writeError'
  | 'readError'
  | 'deviceError';

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
  open(options: SerialPortOptions): Promise<void>;

  /**
   * Writes data to the serial port.
   * @param options Write options containing the command to send.
   */
  write(options: SerialPortWriteOptions): Promise<void>;

  /**
   * Start reading data from the serial port.
   * @returns Promise that resolves with the read data.
   */
  startReading(): Promise<void>;

  /**
   * Stop reading data from the serial port.
   * @returns Promise that resolves with the read data.
   */
  stopReading(): Promise<void>;

  /**
   * Closes the serial port connection.
   */
  close(): Promise<void>;

  /**
   * Add listener for serial port events
   * @param eventName The event to listen for
   * @param listenerFunc Callback function when event occurs
   * @returns Promise with the listener handle
   */
  addEvent(
    eventName: SerialPortEventTypes,
    listenerFunc: (event: SerialPortEventData) => void
  ): Promise<PluginListenerHandle>;

  /**
   * Remove listener for serial port events
   * @param listener The event to stop listening for
   */
  removeEvent(
    eventName: SerialPortEventTypes,
    listenerFunc?: (event: SerialPortEventData) => void
  ): Promise<void>;
}