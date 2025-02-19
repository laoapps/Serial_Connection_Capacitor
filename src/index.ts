import { registerPlugin } from '@capacitor/core';
import type { SerialPortPlugin } from './definitions';

/**
 * @name serialconnectioncapacitor
 * @description
 * Capacitor plugin for serial port communication.
 * Provides methods to communicate with serial devices through USB/UART.
 * @example
 * ```typescript
 * import { serialconnectioncapacitor } from 'serialconnectioncapacitor';
 * 
 * const connectToDevice = async () => {
 *   const ports = await serialconnectioncapacitor.listPorts();
 *   console.log('Available ports:', ports);
 * 
 *   await serialconnectioncapacitor.open({
 *     portPath: '/dev/tty.usbserial',
 *     baudRate: 9600
 *   });
 * };
 * ```
 */
const serialconnectioncapacitor = registerPlugin<SerialPortPlugin>('serialconnectioncapacitor', {
  web: () => import('./web').then((m) => new m.SerialConnectionCapacitorWeb()),
  android: () => import('./android').then((m) => new m.SerialConnectionCapacitorAndroid()),
  // ios: () => import('./ios').then((m) => new m.SerialConnectionCapacitorIOS()),
});

export * from './definitions';
export { serialconnectioncapacitor };