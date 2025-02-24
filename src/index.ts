

import {registerPlugin } from '@capacitor/core';

import  { 
  SerialPortPlugin
} from './definitions';
export * from './definitions';

const SerialConnectionCapacitorNative = registerPlugin<SerialPortPlugin>('SerialCapacitor',{
  web: () => import('./web').then(m => new m.SerialConnectionCapacitorWeb()),
});


const SerialConnectionCapacitor = SerialConnectionCapacitorNative;

export * from './definitions';
export { SerialConnectionCapacitor };