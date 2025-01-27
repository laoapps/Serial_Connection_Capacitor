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
  command: string;
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
 * Plugin interface for serial port communication.
 */
export interface SerialPortPlugin {
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
   * Reads data from the serial port.
   * @returns Promise that resolves with the read data.
   */
  read(): Promise<SerialPortReadResult>;

  /**
   * Closes the serial port connection.
   */
  close(): Promise<void>;
}