// import { WebPlugin,PluginListenerHandle } from '@capacitor/core';
// import { SerialConnectionCapacitor,SerialPortEventTypes } from './index';  // Import the registered plugin
// import type { 
//   SerialPortPlugin, 
//   SerialPortOptions, 
//   SerialPortWriteOptions, 
//   SerialPortListResult 
// } from './definitions';

// export class SerialConnectionCapacitorAndroid extends WebPlugin implements SerialPortPlugin {
//   constructor() {
//     super();
//   }

//   async listPorts(): Promise<SerialPortListResult> {
//     console.log("Calling listPorts on Android...");
//     return SerialConnectionCapacitor.listPorts();
//   }

//   async openNativeSerial(options: SerialPortOptions): Promise<void> {
//     console.log("Opening Native serial port:", options);
//     return SerialConnectionCapacitor.openNativeSerial(options);
//   }

//   async openUsbSerial(options: SerialPortOptions): Promise<void> {
//     console.log("Opening serial port:", options);
//     return SerialConnectionCapacitor.openUsbSerial(options);
//   }

//   async write(options: SerialPortWriteOptions): Promise<void> {
//     console.log("Writing to serial port:", options);
//     return SerialConnectionCapacitor.write(options);
//   }

//   async startReading(): Promise<void> {
//     console.log("Starting serial read...");
//     return SerialConnectionCapacitor.startReading();
//   }

//   async stopReading(): Promise<void> {
//     console.log("Stopping serial read...");
//     return SerialConnectionCapacitor.stopReading();
//   }

//   async close(): Promise<void> {
//     console.log("Closing serial connection...");
//     return SerialConnectionCapacitor.close();
//   }
//   addListener(eventName: SerialPortEventTypes, listenerFunc: (...args: any[]) => void): Promise<PluginListenerHandle> & PluginListenerHandle {
//     return SerialConnectionCapacitor.addListener(eventName, listenerFunc);
//   }



// }

// export { SerialConnectionCapacitor };