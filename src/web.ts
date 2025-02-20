import type { SerialPortPlugin, SerialPortOptions, SerialPortWriteOptions, SerialPortListResult, SerialPortEventData, SerialPortEventTypes } from './definitions';

/**
 * @name SerialPortPlugin
 * @description Serial port communication plugin for Capacitor applications.
 * @method listPorts
 * @method open
 * @method write
 * @method read
 * @method close
 * @usage
 * ```typescript
 * import { serialconnectioncapacitor } from 'serialconnectioncapacitor';
 * 
 * const serial = new serialconnectioncapacitor();
 * const ports = await serial.listPorts();
 * console.log('Available ports:', ports);
 * 
 * await serial.open({
 *   portPath: '/dev/tty.usbserial',
 *   baudRate: 9600
 * });
 * ```
 */
export class SerialConnectionCapacitorWeb implements SerialPortPlugin {
  private listeners: { [eventName: string]: (data: any) => void } = {};

  /**
   * Lists available serial ports.
   * @returns Promise that resolves with the list of available ports.
   */
  async listPorts(): Promise<SerialPortListResult> {
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Opens a connection to the serial port.
   * @param _options Connection options including port path and baud rate.
   * @returns Promise that resolves when the connection is established.
   */
  async open(_options: SerialPortOptions): Promise<void> {
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Writes data to the serial port.
   * @param options Write options containing the command to send.
   * @returns Promise that resolves when the write is complete.
   */
  async write(_options: SerialPortWriteOptions): Promise<void> {
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Start reading data from the serial port.
   * @returns Promise that resolves with the data read from the serial port.
   */
  async startReading(): Promise<void> {
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Stop reading data from the serial port.
   * @returns Promise that resolves with the data read from the serial port.
   */
  async stopReading(): Promise<void> {
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Closes the serial port connection.
   * @returns Promise that resolves when the connection is closed.
   */
  async close(): Promise<void> {
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Add listener for serial port events
   * @param eventName The event to listen for
   * @param listenerFunc Callback function when event occurs
   */
  async addEvent(
    eventName: SerialPortEventTypes,
    listenerFunc: (event: SerialPortEventData) => void
  ): Promise<void> {
    this.listeners[eventName] = listenerFunc;
    return Promise.resolve();
  }

  /**
   * Remove listener for serial port events
   * @param eventName The event to stop listening for
   */
  async removeEvent(
    eventName: SerialPortEventTypes
  ): Promise<void> {
    delete this.listeners[eventName];
    return Promise.resolve();
  }
}