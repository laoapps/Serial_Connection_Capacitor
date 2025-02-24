import { registerPlugin } from '@capacitor/core';
import type { SerialPortPlugin } from './definitions';

const SerialConnectionCapacitor = registerPlugin<SerialPortPlugin>('SerialConnectionCapacitor', {
  web: () => import('./web').then((m) => new m.SerialConnectionCapacitorWeb())
});

export * from './definitions';
export { SerialConnectionCapacitor };