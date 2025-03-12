import { WebPlugin,PluginListenerHandle } from '@capacitor/core';

import  { 
  SerialPortPlugin, 
  SerialPortOptions, 
  SerialPortWriteOptions, 
  SerialPortListResult,
  SerialPortEventTypes
} from './definitions';


export class SerialConnectionCapacitorWeb extends WebPlugin implements SerialPortPlugin {

  constructor() {
    super();
  }

  async listPorts(): Promise<SerialPortListResult> {
    console.log('listPorts');
    throw new Error('listPorts is not supported on the web platform.');
  }

  async openUsbSerial(_options: SerialPortOptions): Promise<any> {
    console.log('openUsbSerial',_options);
    throw new Error('open is not supported on the web platform.');
  }
  async openSerial(_options: SerialPortOptions): Promise<any> {
    console.log('openNativeSerial',_options);
    throw new Error('open is not supported on the web platform.');
  }
  async openSerialEssp(_options: SerialPortOptions): Promise<any> {
    console.log('openSerialEssp',_options);
    throw new Error('open is not supported on the web platform.');
  }
  



  async write(_options: SerialPortWriteOptions): Promise<any> {
    console.log('write',_options);
    throw new Error('write is not supported on the web platform.');
  }
  async writeVMC(_options: SerialPortWriteOptions): Promise<any> {
    console.log('writeVMC',_options);
    throw new Error('writeVMC is not supported on the web platform.');
  }
  async writeEssp(_options: SerialPortWriteOptions): Promise<any> {
    console.log('writeVMC',_options);
    throw new Error('writeVMC is not supported on the web platform.');
  }
  async startReadingEssp(): Promise<any> {
    console.log('startReading');
    throw new Error('startReading is not supported on the web platform.');
  }
  async startReading(): Promise<any> {
    console.log('startReading');
    throw new Error('startReading is not supported on the web platform.');
  }
  async startReadingVMC(): Promise<any> {
    console.log('startReading');
    throw new Error('startReading is not supported on the web platform.');
  }

  async stopReading(): Promise<any> {
    console.log('stopReading');
    throw new Error('stopReading is not supported on the web platform.');
  }

  async close(): Promise<any> {
    console.log('close');
    throw new Error('close is not supported on the web platform.');
  }
  addListener(eventName: SerialPortEventTypes, listenerFunc: (...args: any[]) => void): Promise<PluginListenerHandle> & PluginListenerHandle {
    console.log("Adding listener for:", eventName,listenerFunc);
    throw new Error('addListener is not supported on the web platform.');
  }


}