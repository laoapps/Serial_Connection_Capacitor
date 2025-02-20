import type { SerialPortPlugin, SerialPortOptions, SerialPortWriteOptions, SerialPortListResult, SerialPortEventData, SerialPortEventTypes } from './definitions';
import { PluginListenerHandle } from '@capacitor/core';

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
  private port: any = null;
  private reader: any = null;
  private listeners: { [eventName: string]: (data: SerialPortEventData) => void } = {};

  async listPorts(): Promise<SerialPortListResult> {
    try {
      if (!('serial' in navigator)) {
        throw new Error('Web Serial API not supported in this browser');
      }

      const ports = await (navigator as any).serial.getPorts();
      const portList: { [key: string]: number } = {};
      
      // Remove unused 'port' parameter
      ports.forEach((_: any, index: number) => {
        portList[`web-serial-${index}`] = index;
      });

      return { ports: portList };
    } catch (error) {
      console.error('Error listing ports:', error);
      throw error;
    }
  }

  async open(options: SerialPortOptions): Promise<void> {
    try {
      if (!('serial' in navigator)) {
        throw new Error('Web Serial API not supported in this browser');
      }

      this.port = await (navigator as any).serial.requestPort();
      await this.port.open({ baudRate: options.baudRate });
      
      const event: SerialPortEventData = { message: 'Connection opened successfully' };
      this.notifyListeners('connectionOpened', event);
    } catch (error:any) {
      const event: SerialPortEventData = { error: error.message };
      this.notifyListeners('connectionError', event);
      throw error;
    }
  }

  async write(options: SerialPortWriteOptions): Promise<void> {
    try {
      if (!this.port) {
        throw new Error('Port not open');
      }

      const writer = this.port.writable.getWriter();
      const data = new TextEncoder().encode(options.command);
      await writer.write(data);
      writer.releaseLock();

      const event: SerialPortEventData = { message: 'Write successful' };
      this.notifyListeners('writeSuccess', event);
    } catch (error:any) {
      const event: SerialPortEventData = { error: error.message };
      this.notifyListeners('writeError', event);
      throw error;
    }
  }

  async startReading(): Promise<void> {
    try {
      if (!this.port) {
        throw new Error('Port not open');
      }

      this.reader = this.port.readable.getReader();
      
      // Start the reading loop
      const readLoop = async () => {
        try {
          while (true) {
            const { value, done } = await this.reader.read();
            if (done) {
              break;
            }
            const data = new TextDecoder().decode(value);
            const event: SerialPortEventData = { data };
            this.notifyListeners('dataReceived', event);
          }
        } catch (error:any) {
          const event: SerialPortEventData = { error: error.message };
          this.notifyListeners('readError', event);
        }
      };

      readLoop();
      
      const event: SerialPortEventData = { message: 'Started reading' };
      this.notifyListeners('readingStarted', event);
    } catch (error:any) {
      const event: SerialPortEventData = { error: error.message };
      this.notifyListeners('readError', event);
      throw error;
    }
  }

  async stopReading(): Promise<void> {
    try {
      if (this.reader) {
        await this.reader.cancel();
        this.reader = null;
      }
      const event: SerialPortEventData = { message: 'Stopped reading' };
      this.notifyListeners('readingStopped', event);
    } catch (error:any) {
      const event: SerialPortEventData = { error: error.message };
      this.notifyListeners('readError', event);
      throw error;
    }
  }

  async close(): Promise<void> {
    try {
      if (this.reader) {
        await this.reader.cancel();
        this.reader = null;
      }
      if (this.port) {
        await this.port.close();
        this.port = null;
      }
      const event: SerialPortEventData = { message: 'Connection closed' };
      this.notifyListeners('connectionClosed', event);
    } catch (error:any) {
      const event: SerialPortEventData = { error: error.message };
      this.notifyListeners('connectionError', event);
      throw error;
    }
  }

  async addEvent(
    eventName: SerialPortEventTypes,
    listenerFunc: (event: SerialPortEventData) => void
  ): Promise<PluginListenerHandle> {
    this.listeners[eventName] = listenerFunc;
    return {
      remove: async () => {
        this.removeEvent(eventName);
      }
    };
  }

  async removeEvent(eventName: SerialPortEventTypes): Promise<void> {
    delete this.listeners[eventName];
  }

  private notifyListeners(eventName: SerialPortEventTypes, event: SerialPortEventData): void {
    const listener = this.listeners[eventName];
    if (listener) {
      listener(event);
    }
  }
}

// Register web implementation
const SerialConnectionCapacitor = new SerialConnectionCapacitorWeb();
export { SerialConnectionCapacitor };