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
  /**
   * Enable NV9 mode for SSP protocol
   */
  isNV9?: boolean;
  /**
   * Auto-connect to USB NV9 after serial connection
   */
  autoConnectUSB?: boolean;
}

/**
 * Options for NV9 SSP commands
 */
export interface NV9CommandOptions {
  /**
   * Command name (e.g., 'POLL', 'ENABLE', 'DISABLE', 'GET_SERIAL_NUMBER')
   */
  command: string;
  /**
   * Optional arguments for the command as JSON string
   */
  args?: string;
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
 * NV9 Event types from SSP protocol
 */
export type NV9EventType = 
  | 'READ_NOTE'           // Note being read
  | 'CREDIT_NOTE'         // Credit issued
  | 'NOTE_STACKED'        // Note stacked in cashbox
  | 'NOTE_REJECTING'      // Note being rejected
  | 'NOTE_REJECTED'       // Note rejected
  | 'DISABLED'            // Device disabled
  | 'ENABLED'             // Device enabled
  | 'STACKER_FULL'        // Cashbox full
  | 'FRAUD_ATTEMPT'       // Fraud detected
  | 'SAFE_NOTE_JAM'       // Safe jam
  | 'UNSAFE_NOTE_JAM'     // Unsafe jam
  | 'CASHBOX_REMOVED'     // Cashbox removed
  | 'CASHBOX_REPLACED'    // Cashbox replaced
  | 'NOTE_HELD_IN_BEZEL'  // Note waiting to be taken
  | 'NOTE_CLEARED_FROM_FRONT' // User took note back
  | 'NOTE_CLEARED_TO_CASHBOX' // Note to cashbox
  | 'CHANNEL_DISABLE'     // Channel disabled
  | 'nv9Ready'            // NV9 initialized and ready
  | 'nv9Error';           // NV9 error

/**
 * NV9 Event data structure
 */
export interface NV9EventData {
  /**
   * Event name
   */
  event: NV9EventType | string;
  /**
   * Event data as JSON string
   */
  data?: string;
  /**
   * Error message if event is nv9Error
   */
  error?: string;
  /**
   * Timestamp of the event
   */
  timestamp?: number;
}

/**
 * READ_NOTE specific data
 */
export interface ReadNoteData {
  code: number;
  name: 'READ_NOTE';
  channel: number;
}

/**
 * CREDIT_NOTE specific data
 */
export interface CreditNoteData {
  code: number;
  name: 'CREDIT_NOTE';
  channel: number;
}

/**
 * NOTE_HELD_IN_BEZEL specific data
 */
export interface NoteHeldInBezelData {
  code: number;
  name: 'NOTE_HELD_IN_BEZEL';
  position: number;
  value: number;
  country_code: string;
}

/**
 * USB Device event data
 */
export interface USBDeviceEventData {
  event: 'usbAttached' | 'usbDetached' | 'usbPermissionGranted' | 'usbScanComplete' | 'usbAutoConnected' | 'usbAutoConnectFailed';
  deviceName?: string;
  vendorId?: number;
  productId?: number;
  hasPermission?: boolean;
  found?: boolean;
  count?: number;
  devices?: string;
  reason?: string;
  success?: boolean;
}

/**
 * Event data types for serial port events
 */
export interface SerialPortEventData {
  message?: string;
  data?: any;
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
  | 'adh814Response'
  | 'nv9Event'           // NV9 events
  | 'usbDeviceEvent';     // USB device events

/**
 * Plugin interface for serial port communication.
 */
export interface SerialPortPlugin {
  /**
   * Lists available serial ports.
   * @returns Promise that resolves with the list of available ports.
   */
  listPorts(): Promise<{ ports: { [key: string]: number } }>;

  /**
   * Lists only USB devices
   * @returns Promise that resolves with USB devices list
   */
  listUSBDevices(): Promise<{ devices: string; count: number }>;

  /**
   * Opens a serial port connection.
   * @param options Connection options including port path and baud rate.
   */
  openSerial(options: SerialPortOptions): Promise<{ 
    success: boolean; 
    message: string; 
    portName: string; 
    baudRate: number; 
    connectionType: string;
    isNV9?: boolean;
  }>;

  /**
   * Opens a USB serial port connection explicitly.
   * @param options Connection options including port name.
   */
  openUSB(options: { portName: string }): Promise<{
    success: boolean;
    message: string;
    portName: string;
    connectionType: string;
  }>;

  /**
   * Opens a USB serial port connection.
   * @param options Connection options including port path and baud rate.
   */
  openUsbSerial(options: SerialPortOptions): Promise<any>;

  /**
   * Sends an NV9 SSP command to the USB device
   * @param options NV9 command options
   */
  sendNV9Command(options: NV9CommandOptions): Promise<{
    success: boolean;
    event: string;
    command: string;
    data?: any;
    error?: string;
  }>;

  /**
   * Checks USB connection status
   */
  checkUSBStatus(): Promise<{
    usbManagerAvailable: boolean;
    totalUsbDevices: number;
    devices?: string;
    usbSerialPortOpen: boolean;
    isNV9Mode: boolean;
    isPolling: boolean;
    serialPortOpen: boolean;
  }>;

  /**
   * Gets detailed USB device info
   * @param options Port name
   */
  getUSBDeviceInfo(options: { portName: string }): Promise<any>;

  /**
   * Tests USB connection with a specific device
   * @param options Port name
   */
  testUSBConnection(options: { portName: string }): Promise<any>;

  /**
   * Requests USB permission for a device
   * @param options Port name
   */
  requestUSBPermission(options: { portName: string }): Promise<any>;

  /**
   * Opens a USB serial port connection for ESSP.
   * @param options Connection options including port path and baud rate.
   */
  openSerialEssp(options: SerialPortOptions): Promise<any>;

  /**
   * Writes data to the serial port.
   * @param options Write options containing the command to send.
   */
  write(options: { data: string }): Promise<any>;

  /**
   * Writes data to the serial port for VMC.
   * @param options Write options containing the command to send.
   */
  writeVMC(options: { data: string }): Promise<any>;
  
  /**
   * Writes data to the serial port for VMC.
   * @param options Write options containing the command to send.
   */
  writeMT102(options: { data: string }): Promise<any>;

  /**
   * Writes data to the serial port for ADH814.
   * @param options Write options containing the command to send.
   */
  writeADH814(options: { data: string }): Promise<any>;

  /**
   * Writes data to the serial port for ESSP.
   * @param options Write options containing the command to send.
   */
  writeEssp(options: { data: string }): Promise<any>;

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
   * Starts reading data from the serial port for MT102.
   * @returns Promise that resolves when reading starts.
   */
  startReadingMT102(): Promise<any>;
   
  /**
   * Starts reading data from the serial port for ADH814.
   * @returns Promise that resolves when reading starts.
   */
  startReadingADH814(): Promise<any>;

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
   * Stops NV9 polling
   */
  stopNV9Polling(): Promise<{ success: boolean; message: string }>;

  /**
   * Closes the serial port connection.
   */
  close(): Promise<{ success: boolean; message: string }>;

  // ADH814 Methods (keep your existing ones)
  requestID(options: { address: number }): Promise<any>;
  scanDoorFeedback(options: { address: number }): Promise<any>;
  pollStatus(options: { address: number }): Promise<any>;
  setTemperature(options: { address: number; mode: number; tempValue: number }): Promise<any>;
  startMotor(options: { address: number; motorNumber: number }): Promise<any>;
  acknowledgeResult(options: { address: number }): Promise<any>;
  startMotorCombined(options: { address: number; motorNumber1: number; motorNumber2: number }): Promise<any>;
  startPolling(options: { address: number; interval: number }): Promise<any>;
  stopPolling(): Promise<any>;
  querySwap(options: { address: number }): Promise<any>;
  setSwap(options: { address: number; swapEnabled: number }): Promise<any>;
  switchToTwoWireMode(options: { address: number }): Promise<any>;

  /**
   * Add listener for serial port events.
   * @param eventName The event to listen for.
   * @param listenerFunc Callback function when event occurs.
   * @returns Promise with the listener handle.
   */
  addListener(eventName: SerialPortEventTypes, listenerFunc: (event: any) => void): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Remove all listeners
   */
  removeAllListeners(): Promise<void>;
}