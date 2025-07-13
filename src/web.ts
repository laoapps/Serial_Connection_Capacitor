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
    console.log('openUsbSerial', _options);
    throw new Error('openUsbSerial is not supported on the web platform.');
  }

  async openSerial(_options: SerialPortOptions): Promise<any> {
    console.log('openSerial', _options);
    throw new Error('openSerial is not supported on the web platform.');
  }

  async openSerialEssp(_options: SerialPortOptions): Promise<any> {
    console.log('openSerialEssp', _options);
    throw new Error('openSerialEssp is not supported on the web platform.');
  }

  async write(_options: SerialPortWriteOptions): Promise<any> {
    console.log('write', _options);
    throw new Error('write is not supported on the web platform.');
  }

  async writeVMC(_options: SerialPortWriteOptions): Promise<any> {
    console.log('writeVMC', _options);
    throw new Error('writeVMC is not supported on the web platform.');
  }
   async writeADH814(_options: SerialPortWriteOptions): Promise<any> {
    console.log('writeVMC', _options);
    throw new Error('writeVMC is not supported on the web platform.');
  }

  async writeEssp(_options: SerialPortWriteOptions): Promise<any> {
    console.log('writeEssp', _options);
    throw new Error('writeEssp is not supported on the web platform.');
  }

  async startReadingEssp(): Promise<any> {
    console.log('startReadingEssp');
    throw new Error('startReadingEssp is not supported on the web platform.');
  }

  async startReading(): Promise<any> {
    console.log('startReading');
    throw new Error('startReading is not supported on the web platform.');
  }

  async startReadingVMC(): Promise<any> {
    console.log('startReadingVMC');
    throw new Error('startReadingVMC is not supported on the web platform.');
  }
  async startReadingADH814(): Promise<any> {
    console.log('startReadingVMC');
    throw new Error('startReadingVMC is not supported on the web platform.');
  }

  async stopReading(): Promise<any> {
    console.log('stopReading');
    throw new Error('stopReading is not supported on the web platform.');
  }

  async close(): Promise<any> {
    console.log('close');
    throw new Error('close is not supported on the web platform.');
  }
  async querySwap(_options: { address: number }): Promise<any> {
    console.log('querySwap', _options);
    throw new Error('querySwap is not supported on the web platform.');
  }

  async setSwap(_options: { address: number; swapEnabled: number }): Promise<any> {
    console.log('setSwap', _options);
    throw new Error('setSwap is not supported on the web platform.');
  }

  async switchToTwoWireMode(_options: { address: number }): Promise<any> {
    console.log('switchToTwoWireMode', _options);
    throw new Error('switchToTwoWireMode is not supported on the web platform.');
  }
  async requestID(_options: { address: number }): Promise<any> {
    console.log('requestID', _options);
    throw new Error('requestID is not supported on the web platform.');
  }

  async scanDoorFeedback(_options: { address: number }): Promise<any> {
    console.log('scanDoorFeedback', _options);
    throw new Error('scanDoorFeedback is not supported on the web platform.');
  }

  async pollStatus(_options: { address: number }): Promise<any> {
    console.log('pollStatus', _options);
    throw new Error('pollStatus is not supported on the web platform.');
  }

  async setTemperature(_options: { address: number; mode: number; tempValue: number }): Promise<any> {
    console.log('setTemperature', _options);
    throw new Error('setTemperature is not supported on the web platform.');
  }

  async startMotor(_options: { address: number; motorNumber: number }): Promise<any> {
    console.log('startMotor', _options);
    throw new Error('startMotor is not supported on the web platform.');
  }

  async acknowledgeResult(_options: { address: number }): Promise<any> {
    console.log('acknowledgeResult', _options);
    throw new Error('acknowledgeResult is not supported on the web platform.');
  }

  async startMotorCombined(_options: { address: number; motorNumber1: number; motorNumber2: number }): Promise<any> {
    console.log('startMotorCombined', _options);
    throw new Error('startMotorCombined is not supported on the web platform.');
  }

  async startPolling(_options: { address: number; interval: number }): Promise<any> {
    console.log('startPolling', _options);
    throw new Error('startPolling is not supported on the web platform.');
  }

  async stopPolling(): Promise<any> {
    console.log('stopPolling');
    throw new Error('stopPolling is not supported on the web platform.');
  }

  addListener(eventName: SerialPortEventTypes, listenerFunc: (...args: any[]) => void): Promise<PluginListenerHandle> & PluginListenerHandle {
    console.log('Adding listener for:', eventName, listenerFunc);
    throw new Error('addListener is not supported on the web platform.');
  }

}