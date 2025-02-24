import { WebPlugin, ListenerCallback } from '@capacitor/core';
import type { 
  SerialPortPlugin, 
  SerialPortOptions, 
  SerialPortWriteOptions, 
  SerialPortListResult, 

} from './definitions';


export class SerialConnectionCapacitorWeb extends WebPlugin implements SerialPortPlugin {
  protected listeners: { [eventName: string]: ListenerCallback[] } = {};

  constructor() {
    super();
  }

  async listPorts(): Promise<SerialPortListResult> {
    throw new Error('listPorts is not supported on the web platform.');
  }

  async openUsbSerial(_options: SerialPortOptions): Promise<void> {
    throw new Error('open is not supported on the web platform.');
  }
  async openNativeSerial(_options: SerialPortOptions): Promise<void> {
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
}