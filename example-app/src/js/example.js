import { serialConnectionCapacitor } from 'serialconnectioncapacitor';

window.testConnection = async () => {
    try {
        if (!serialConnectionCapacitor) {
            console.error('Serial Connection plugin not available');
            return;
        }

        // First list available ports
        const { ports } = await serialConnectionCapacitor.listPorts();
        if (Object.keys(ports).length === 0) {
            console.error('No serial ports available');
            return;
        }

        // Get the first available port
        const portPath = Object.keys(ports)[0];

        // Open connection with required parameters
        await serialConnectionCapacitor.open({
            portPath: portPath,
            baudRate: 9600
        });

        // Set up event listeners
        await serialConnectionCapacitor.addEvent('dataReceived', (event) => {
            console.log('Received data:', event.data);
        });

        await serialConnectionCapacitor.addEvent('connectionError', (event) => {
            console.error('Connection error:', event.error);
        });

        // Start reading data
        await serialConnectionCapacitor.startReading();

    } catch (error) {
        console.error('Error:', error);
    }
};

window.sendData = async () => {
    try {
        const inputValue = document.getElementById("echoInput").value;
        if (!inputValue) {
            console.error('No input value provided');
            return;
        }

        await serialConnectionCapacitor.write({ 
            command: inputValue 
        });

    } catch (error) {
        console.error('Error sending data:', error);
    }
};

window.closeConnection = async () => {
    try {
        await serialConnectionCapacitor.stopReading();
        await serialConnectionCapacitor.close();
        console.log('Connection closed');
    } catch (error) {
        console.error('Error closing connection:', error);
    }
};

