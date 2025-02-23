// android.ts
import { registerPlugin, WebPlugin } from '@capacitor/core';
import type { 
  SerialPortPlugin, 
  SerialPortOptions, 
  SerialPortWriteOptions, 
  SerialPortListResult, 
  SerialPortEventData, 
  SerialPortEventTypes 
} from './definitions';

class SerialConnectionCapacitorWeb extends WebPlugin implements SerialPortPlugin {
  constructor() {
    super({
      name: 'SerialConnectionCapacitor',
      platforms: ['web'],
    });
  }

  async listPorts(): Promise<SerialPortListResult> {
    throw new Error('USB serial not supported on web');
  }

  async open(_options: SerialPortOptions): Promise<void> {
    throw new Error('USB serial not supported on web');
  }

  async write(_options: SerialPortWriteOptions): Promise<void> {
    throw new Error('USB serial not supported on web');
  }

  async startReading(): Promise<void> {
    throw new Error('USB serial not supported on web');
  }

  async stopReading(): Promise<void> {
    throw new Error('USB serial not supported on web');
  }

  async close(): Promise<void> {
    throw new Error('USB serial not supported on web');
  }

  async addEvent(
    _eventName: SerialPortEventTypes,
    _listenerFunc: (event: SerialPortEventData) => void
  ): Promise<import('@capacitor/core').PluginListenerHandle> {
    throw new Error('Event listening not supported on web');
  }

  async removeEvent(
    _eventName: SerialPortEventTypes,
    _listenerFunc?: (event: SerialPortEventData) => void
  ): Promise<void> {
    throw new Error('Event removing not supported on web');
  }
}

const SerialConnectionCapacitor = registerPlugin<SerialPortPlugin>('SerialConnectionCapacitor', {
  web: () => Promise.resolve(new SerialConnectionCapacitorWeb()),
});

export { SerialConnectionCapacitor };