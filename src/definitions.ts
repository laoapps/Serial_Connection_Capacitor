// definitions.ts
import type { PluginListenerHandle } from '@capacitor/core';

export interface SerialPortOptions {
  portPath: string;
  baudRate: number;
}

export interface SerialPortWriteOptions {
  command: string;
}

export interface SerialPortReadResult {
  data: string;
}

export interface SerialPortListResult {
  ports: { [key: string]: number };
}

export interface SerialPortEventData {
  message?: string;
  data?: string;
  error?: string;
}

export type SerialPortEventTypes = 
  | 'portsListed'
  | 'connectionOpened'
  | 'connectionClosed'
  | 'writeSuccess'
  | 'dataReceived'
  | 'readingStarted'
  | 'readingStopped'
  | 'listError'
  | 'connectionError'
  | 'writeError'
  | 'readError'
  | 'deviceError';

export interface SerialPortPlugin {
  listPorts(): Promise<SerialPortListResult>;
  open(options: SerialPortOptions): Promise<void>;
  write(options: SerialPortWriteOptions): Promise<void>;
  startReading(): Promise<void>;
  stopReading(): Promise<void>;
  close(): Promise<void>;
  addEvent(
    eventName: SerialPortEventTypes,
    listenerFunc: (event: SerialPortEventData) => void
  ): Promise<PluginListenerHandle>;
  removeEvent?(
    eventName: SerialPortEventTypes,
    listenerFunc?: (event: SerialPortEventData) => void
  ): Promise<void>;
}