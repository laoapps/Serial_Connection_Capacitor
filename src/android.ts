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
    return SerialConnectionCapacitor.listPorts();
  }

  async open(options: SerialPortOptions): Promise<void> {
    return SerialConnectionCapacitor.open(options);
  }

  async write(options: SerialPortWriteOptions): Promise<void> {
    return SerialConnectionCapacitor.write(options);
  }

  async startReading(): Promise<void> {
    return SerialConnectionCapacitor.startReading();
  }

  async stopReading(): Promise<void> {
    return SerialConnectionCapacitor.stopReading();
  }

  async close(): Promise<void> {
    return SerialConnectionCapacitor.close();
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