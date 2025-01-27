import type { SerialPortPlugin, SerialPortOptions, SerialPortWriteOptions, SerialPortReadResult } from './definitions';

/**
 * @name SerialPortPlugin
 * @description Serial port communication plugin for Capacitor applications.
 * @method open
 * @method write
 * @method read
 * @method close
 * @usage
 * ```typescript
 * import { SerialConnectionCapacitor } from 'serialconnectioncapacitor';
 * 
 * const serial = new SerialConnectionCapacitor();
 * await serial.open({
 *   portPath: '/dev/tty.usbserial',
 *   baudRate: 9600
 * });
 * ```
 */
export class SerialConnectionCapacitorWeb implements SerialPortPlugin {
  /**
   * Opens a connection to the serial port.
   * @param _options Connection options including port path and baud rate.
   * @returns Promise that resolves when the connection is established.
   */
  async open(_options: SerialPortOptions): Promise<void> {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Writes data to the serial port.
   * @param options Write options containing the command to send.
   * @returns Promise that resolves when the write is complete.
   */
  async write(_options: SerialPortWriteOptions): Promise<void> {
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Reads data from the serial port.
   * @returns Promise that resolves with the data read from the serial port.
   */
  async read(): Promise<SerialPortReadResult> {
    throw new Error('Method not implemented for web platform.');
  }

  /**
   * Closes the serial port connection.
   * @returns Promise that resolves when the connection is closed.
   */
  async close(): Promise<void> {
    throw new Error('Method not implemented for web platform.');
  }
}