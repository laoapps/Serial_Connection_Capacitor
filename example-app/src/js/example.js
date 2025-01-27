import { serialconnectioncapacitor } from 'serialconnectioncapacitor';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    serialconnectioncapacitor.echo({ value: inputValue })
}
