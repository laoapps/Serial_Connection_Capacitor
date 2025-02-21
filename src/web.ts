import { WebPlugin, ListenerCallback, PluginListenerHandle } from '@capacitor/core';
import type { 
  SerialPortPlugin, 
  SerialPortOptions, 
  SerialPortWriteOptions, 
  SerialPortListResult, 
  SerialPortEventData, 
  SerialPortEventTypes 
} from './definitions';


export class SerialConnectionCapacitorWeb extends WebPlugin implements SerialPortPlugin {
  protected listeners: { [eventName: string]: ListenerCallback[] } = {};

  constructor() {
    super();
    // super({
    //   name: 'SerialConnectionCapacitor',
    //   platforms: ['web']
    // });
  }

  async listPorts(): Promise<SerialPortListResult> {
    throw new Error('listPorts is not supported on the web platform.');
  }

  async open(_options: SerialPortOptions): Promise<void> {
    throw new Error('open is not supported on the web platform.');
  }

  async write(_options: SerialPortWriteOptions): Promise<void> {
    throw new Error('write is not supported on the web platform.');
  }

  async startReading(): Promise<void> {
    throw new Error('startReading is not supported on the web platform.');
  }

  async stopReading(): Promise<void> {
    throw new Error('stopReading is not supported on the web platform.');
  }

  async close(): Promise<void> {
    throw new Error('close is not supported on the web platform.');
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

const SerialConnectionCapacitor = new SerialConnectionCapacitorWeb();
export { SerialConnectionCapacitor };