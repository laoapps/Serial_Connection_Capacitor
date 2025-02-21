import { WebPlugin, ListenerCallback, PluginListenerHandle } from '@capacitor/core';
import type { 
  SerialPortPlugin, 
  SerialPortOptions, 
  SerialPortWriteOptions, 
  SerialPortListResult, 
  SerialPortEventData, 
  SerialPortEventTypes 
} from './definitions';

export class SerialConnectionCapacitorAndroid extends WebPlugin implements SerialPortPlugin {
  protected listeners: { [eventName: string]: ListenerCallback[] } = {};

  constructor() {
    super({
      name: 'SerialConnectionCapacitor',
      platforms: ['android']
    });
  }

  async listPorts(): Promise<SerialPortListResult> {
    // Implement the method to list available serial ports on Android
    // throw new Error('Method not implemented for Android platform.');
    const result = await this.listPorts();
    console.log('Available ports:', result.ports);
    return result;
  }

  async open(_options: SerialPortOptions): Promise<void> {
    // Implement the method to open a serial port connection on Android
    // throw new Error('Method not implemented for Android platform.');
    return this.open(_options);
  }

  async write(_options: SerialPortWriteOptions): Promise<void> {
    // Implement the method to write data to the serial port on Android
    // throw new Error('Method not implemented for Android platform.');
    return this.write(_options);
  }

  async startReading(): Promise<void> {
    // Implement the method to start reading data from the serial port on Android
    // throw new Error('Method not implemented for Android platform.');
    return this.startReading();
  }

  async stopReading(): Promise<void> {
    // Implement the method to stop reading data from the serial port on Android
    // throw new Error('Method not implemented for Android platform.');
    return this.stopReading();
  }

  async close(): Promise<void> {
    // Implement the method to close the serial port connection on Android
    // throw new Error('Method not implemented for Android platform.');
    return this.close();
  }

  async addEvent(
    eventName: SerialPortEventTypes,
    listenerFunc: (event: SerialPortEventData) => void
  ): Promise<PluginListenerHandle> {
    if (!this.listeners[eventName]) {
      this.listeners[eventName] = [];
    }
    this.listeners[eventName].push(listenerFunc as ListenerCallback);

    return Promise.resolve({
      remove: async () => {
        this.removeEvent(eventName, listenerFunc);
      }
    });
  }

  async removeEvent(
    eventName: SerialPortEventTypes,
    listenerFunc?: (event: SerialPortEventData) => void
  ): Promise<void> {
    if (!this.listeners[eventName]) {
      return Promise.resolve();
    }
    if (listenerFunc) {
      this.listeners[eventName] = this.listeners[eventName].filter(callback => callback !== listenerFunc);
    } else {
      delete this.listeners[eventName];
    }
    return Promise.resolve();
  }
}

const SerialConnectionCapacitor = new SerialConnectionCapacitorAndroid();
export { SerialConnectionCapacitor };