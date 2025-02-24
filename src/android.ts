import { WebPlugin, ListenerCallback, PluginListenerHandle, registerPlugin } from '@capacitor/core';
import type { 
  SerialPortPlugin, 
  SerialPortOptions, 
  SerialPortWriteOptions, 
  SerialPortListResult, 
  SerialPortEventData, 
  SerialPortEventTypes 
} from './definitions';

const SerialConnectionCapacitor = registerPlugin<SerialPortPlugin>('SerialConnectionCapacitor');

export class SerialConnectionCapacitorAndroid extends WebPlugin implements SerialPortPlugin {
  protected listeners: { [eventName: string]: ListenerCallback[] } = {};

  constructor() {
    super();
  }

  async listPorts(): Promise<SerialPortListResult> {
    console.log("Calling listPorts on Android...");
    return SerialConnectionCapacitor.listPorts();
  }

  async openNativeSerial(options: SerialPortOptions): Promise<void> {
    console.log("Opening Native serial port:", options);
    return SerialConnectionCapacitor.openNativeSerial(options);
  }
  async openUsbSerial(options: SerialPortOptions): Promise<void> {
    console.log("Opening serial port:", options);
    return SerialConnectionCapacitor.openUsbSerial(options);
  }


  async write(options: SerialPortWriteOptions): Promise<void> {
    console.log("Writing to serial port:", options);
    return SerialConnectionCapacitor.write(options);
  }

  async startReading(): Promise<void> {
    console.log("Starting serial read...");
    return SerialConnectionCapacitor.startReading();
  }

  async stopReading(): Promise<void> {
    console.log("Stopping serial read...");
    return SerialConnectionCapacitor.stopReading();
  }

  async close(): Promise<void> {
    console.log("Closing serial connection...");
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

export { SerialConnectionCapacitor };