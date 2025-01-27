import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(serialconnectioncapacitorPlugin)
public class serialconnectioncapacitorPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "serialconnectioncapacitorPlugin"
    public let jsName = "serialconnectioncapacitor"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = serialconnectioncapacitor()

    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve([
            "value": implementation.echo(value)
        ])
    }
}
