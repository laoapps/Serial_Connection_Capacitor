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
  // Use the correct listener type from WebPlugin
  protected _listeners: { [eventName: string]: ListenerCallback[] } = {};

  async listPorts(): Promise<SerialPortListResult> {
    return this.native.listPorts();
  }

  async open(options: SerialPortOptions): Promise<void> {
    return this.native.open(options);
  }

  async write(options: SerialPortWriteOptions): Promise<void> {
    return this.native.write(options);
  }

  async startReading(): Promise<void> {
    return this.native.startReading();
  }

  async stopReading(): Promise<void> {
    return this.native.stopReading();
  }

  async close(): Promise<void> {
    return this.native.close();
  }

  async addEvent(
    eventName: SerialPortEventTypes,
    listenerFunc: (event: SerialPortEventData) => void
  ): Promise<PluginListenerHandle> {
    if (!this._listeners[eventName]) {
      this._listeners[eventName] = [];
    }
    this._listeners[eventName].push(listenerFunc as ListenerCallback);
    
    return this.addListener(eventName, (event: SerialPortEventData) => {
      const listeners = this._listeners[eventName] || [];
      listeners.forEach(listener => listener(event));
    });
  }

  async removeEvent(eventName: SerialPortEventTypes): Promise<void> {
    if (this._listeners[eventName]) {
      delete this._listeners[eventName];
      await this.removeAllListeners();
    }
  }

  private get native(): SerialPortPlugin {
    return (window as any).SerialConnectionCapacitor;
  }
}

const SerialConnectionCapacitor = new SerialConnectionCapacitorAndroid();
export { SerialConnectionCapacitor };