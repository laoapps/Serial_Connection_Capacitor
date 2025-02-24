// filepath: src/app/home/home.page.ts
import { Component } from '@angular/core';
import { serialconnectioncapacitor } from 'serialconnectioncapacitor';

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
})
export class HomePage {
  constructor() {}

  async listPorts() {
    try {
      const result = await serialconnectioncapacitor.listPorts();
      console.log('Available ports:', result.ports);
    } catch (error) {
      console.error('Error listing ports:', error);
    }
  }

  async openPort() {
    try {
      await serialconnectioncapacitor.open({
        portName: '/dev/tty.usbserial',
        baudRate: 9600,
      });
      console.log('Port opened successfully');
    } catch (error) {
      console.error('Error opening port:', error);
    }
  }

  async writeData() {
    try {
      await serialconnectioncapacitor.write({
        command: 'Hello, Serial Port!',
      });
      console.log('Data written successfully');
    } catch (error) {
      console.error('Error writing data:', error);
    }
  }

  async readData() {
    try {
      const result = await serialconnectioncapacitor.read();
      console.log('Data read from port:', result.data);
    } catch (error) {
      console.error('Error reading data:', error);
    }
  }

  async closePort() {
    try {
      await serialconnectioncapacitor.close();
      console.log('Port closed successfully');
    } catch (error) {
      console.error('Error closing port:', error);
    }
  }
}