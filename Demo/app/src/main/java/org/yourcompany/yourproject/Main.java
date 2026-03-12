package org.yourcompany.yourproject;

import java.io.ByteArrayOutputStream;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

public class Main {

    private static boolean quietMode = false;
    private static SSP ssp;

    public static void main(String[] args) {
        System.out.println("Testing CRC calculation...");
        SSPUtils.debugCRC();
        ssp = new SSP();
        Scanner scanner = new Scanner(System.in);

        // Set up all event listeners
        setupEventListeners();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("SSP TEST CONSOLE - Note Acceptor/Payout Device");
        System.out.println("=".repeat(60));
        System.out.println("\nAvailable Commands:");
        System.out.println("-".repeat(60));
        System.out.println("CONNECTION:");
        System.out.println("  open                    - Open serial port");
        System.out.println("  close                   - Close serial port");
        System.out.println("  quiet                   - Toggle quiet mode (hide polling)");
        System.out.println("\nBASIC COMMANDS:");
        System.out.println("  enable                  - Enable device");
        System.out.println("  disable                 - Disable device");
        System.out.println("  reset                   - Reset device");
        System.out.println("  sync                    - Send SYNC command");
        System.out.println("  poll                    - Single poll command");
        System.out.println("  start                   - Start continuous polling");
        System.out.println("  stop                    - Stop continuous polling");
        System.out.println("\nINFORMATION COMMANDS:");
        System.out.println("  serial                  - Get serial number");
        System.out.println("  setup                   - Get setup info");
        System.out.println("  unit                    - Get unit data");
        System.out.println("  channels                - Get channel values");
        System.out.println("  security                - Get channel security");
        System.out.println("  reject                  - Get last reject code");
        System.out.println("  counters                - Get counters");
        System.out.println("  firmware                - Get firmware version");
        System.out.println("  dataset                 - Get dataset version");
        System.out.println("\nCONFIGURATION COMMANDS:");
        System.out.println("  inhibits <bits>         - Set channel inhibits (e.g., inhibits 1111111)");
        System.out.println("  protocol <ver>          - Set protocol version (6)");
        System.out.println("  display on/off          - Turn display on/off");
        System.out.println("  route <ch> <val> <cc>   - Set denomination route");
        System.out.println("  refill <on/off/get>     - Set refill mode");
        System.out.println("  reporting <value/channel> - Set value reporting type");
        System.out.println("\nNOTE HANDLING:");
        System.out.println("  hold                    - Hold note in escrow");
        System.out.println("  rejectnote              - Reject note from escrow");
        System.out.println("\nPAYOUT COMMANDS (SMART Payout/Hopper):");
        System.out.println("  payout <amount> <cc>    - Payout amount (e.g., payout 10000 LAK)");
        System.out.println("  float <min> <amt> <cc>  - Float amount");
        System.out.println("  floatby <num> <val> <cc> - Float by denomination");
        System.out.println("  payoutby <num> <val> <cc> - Payout by denomination");
        System.out.println("  getlevel <val> <cc>     - Get denomination level");
        System.out.println("  empty                   - Empty all");
        System.out.println("  smartempty              - Smart empty");
        System.out.println("  halt                    - Halt payout");
        System.out.println("  enablepayout            - Enable payout device");
        System.out.println("  disablepayout           - Disable payout device");
        System.out.println("\nDEBUG COMMANDS:");
        System.out.println("  raw <hex>               - Send raw hex bytes");
        System.out.println("  read                    - Read raw data");
        System.out.println("  monitor                 - Monitor serial port");
        System.out.println("  exit                    - Exit program");
        System.out.println("-".repeat(60));

        while (true) {
            System.out.print("\nEnter command: ");
            String input = scanner.nextLine().trim();
            if (input.equals("exit")) {
                break;
            }

            try {
                processCommand(input);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Cleanup
        if (ssp != null) {
            try {
                ssp.stopPoll();
                ssp.close();
            } catch (Exception e) {
                // Ignore
            }
        }
        scanner.close();
        System.out.println("\nExited");
    }

    private static void processCommand(String input) throws Exception {
        String[] parts = input.split("\\s+");
        String command = parts[0].toLowerCase();

        switch (command) {
            // ========== CONNECTION COMMANDS ==========
            // ========== INITIALIZATION ==========
            case "initialize":
            case "init":
                System.out.println("Starting full initialization sequence...");
                ssp.initSSP()
                        .thenRun(() -> {
                            System.out.println("✅ Initialization complete!");
                            // Auto-start polling after successful init
                            ssp.startPoll();
                            System.out.println("▶️ Polling started - device ready");
                        })
                        .exceptionally(ex -> {
                            System.out.println("❌ Initialization failed: " + ex.getMessage());
                            return null;
                        })
                        .get(30, TimeUnit.SECONDS);
                break;
            case "quick":
            case "startup":
                System.out.println("Quick startup - opening port and initializing...");

                // Try to open port
                String[] portsToTry2 = {
                    "/dev/cu.usbserial-AG0KJZVC",
                    "/dev/tty.usbserial-AG0KJZVC"
                };

                boolean portOpened = false;
                for (String port : portsToTry2) {
                    try {
                        ssp.open(port);
                        System.out.println("✅ Port opened: " + port);
                        portOpened = true;
                        break;
                    } catch (Exception e) {
                        System.out.println("❌ Failed to open " + port + ": " + e.getMessage());
                    }
                }

                if (portOpened) {
                    // Initialize and start
                    ssp.initSSP()
                            .thenRun(() -> {
                                System.out.println("✅ Device ready - accepting notes");
                                ssp.startPoll();
                            })
                            .get(30, TimeUnit.SECONDS);
                }
                break;
            case "open":
                String[] portsToTry = {
                    "/dev/cu.usbserial-AG0KJZVC",
                    "/dev/tty.usbserial-AG0KJZVC",
                    "/dev/cu.usbserial-0001",
                    "/dev/ttyUSB0",
                    "/dev/ttyS0"
                };
                boolean opened = false;
                for (String port : portsToTry) {
                    try {
                        ssp.open(port);
                        System.out.println("✅ Port opened: " + port);
                        opened = true;
                        break;
                    } catch (Exception e) {
                        System.out.println("❌ Failed to open " + port + ": " + e.getMessage());
                    }
                }
                if (!opened) {
                    System.out.println("❌ Could not open any port");
                }
                break;

            case "close":
                ssp.close();
                System.out.println("✅ Port closed");
                break;

            case "quiet":
                quietMode = !quietMode;
                ssp.setQuietMode(quietMode);
                System.out.println(quietMode ? "✅ Quiet mode ON (polling hidden)" : "✅ Quiet mode OFF (polling shown)");
                break;

            // ========== BASIC COMMANDS ==========
            case "enable":
                ssp.enable().thenAccept(result -> {
                    System.out.println("✅ Device enabled: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "disable":
                ssp.disable().thenAccept(result -> {
                    System.out.println("✅ Device disabled: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "reset":
                ssp.command("RESET").thenAccept(result -> {
                    System.out.println("✅ Reset: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "sync":
                ssp.command("SYNC").thenAccept(result -> {
                    System.out.println("✅ SYNC: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "poll":
                ssp.command("POLL").thenAccept(result -> {
                    System.out.println("✅ POLL: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "start":
                ssp.startPoll();
                System.out.println("✅ Polling started");
                break;

            case "stop":
                ssp.stopPoll();
                System.out.println("✅ Polling stopped");
                break;

            // ========== INFORMATION COMMANDS ==========
            case "serial":
                ssp.command("GET_SERIAL_NUMBER").thenAccept(result -> {
                    JSONObject info = result.optJSONObject("info");
                    if (info != null) {
                        System.out.println("✅ Serial Number: " + info.optInt("serial_number", 0));
                    }
                }).get(5, TimeUnit.SECONDS);
                break;

            case "setup":
                ssp.command("SETUP_REQUEST").thenAccept(result -> {
                    System.out.println("✅ SETUP INFO:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "unit":
                ssp.command("UNIT_DATA").thenAccept(result -> {
                    System.out.println("✅ UNIT DATA:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "channels":
                ssp.command("CHANNEL_VALUE_REQUEST").thenAccept(result -> {
                    System.out.println("✅ CHANNEL VALUES:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "security":
                ssp.command("CHANNEL_SECURITY_DATA").thenAccept(result -> {
                    System.out.println("✅ CHANNEL SECURITY:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "reject":
                ssp.command("LAST_REJECT_CODE").thenAccept(result -> {
                    System.out.println("✅ LAST REJECT:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "counters":
                ssp.command("GET_COUNTERS").thenAccept(result -> {
                    System.out.println("✅ COUNTERS:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "firmware":
                ssp.command("GET_FIRMWARE_VERSION").thenAccept(result -> {
                    System.out.println("✅ FIRMWARE:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "dataset":
                ssp.command("GET_DATASET_VERSION").thenAccept(result -> {
                    System.out.println("✅ DATASET:\n" + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            // ========== CONFIGURATION COMMANDS ==========
            case "inhibits":
                if (parts.length < 2) {
                    System.out.println("❌ Usage: inhibits <bits> (e.g., inhibits 1111111)");
                    return;
                }
                String bits = parts[1];
                JSONArray channels = new JSONArray();
                for (char c : bits.toCharArray()) {
                    channels.put(c == '1' ? 1 : 0);
                }
                JSONObject inhibitArgs = new JSONObject().put("channels", channels);
                ssp.command("SET_CHANNEL_INHIBITS", inhibitArgs).thenAccept(result -> {
                    System.out.println("✅ Channel inhibits set: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "protocol":
                if (parts.length < 2) {
                    System.out.println("❌ Usage: protocol <version> (e.g., protocol 6)");
                    return;
                }
                int version = Integer.parseInt(parts[1]);
                JSONObject protoArgs = new JSONObject().put("version", version);
                ssp.command("HOST_PROTOCOL_VERSION", protoArgs).thenAccept(result -> {
                    System.out.println("✅ Protocol version set: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "display":
                if (parts.length < 2) {
                    System.out.println("❌ Usage: display on/off");
                    return;
                }
                String displayCmd = parts[1].equals("on") ? "DISPLAY_ON" : "DISPLAY_OFF";
                ssp.command(displayCmd).thenAccept(result -> {
                    System.out.println("✅ Display " + parts[1] + ": " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "route":
                if (parts.length < 4) {
                    System.out.println("❌ Usage: route <channel> <value> <country> (e.g., route 2 20000 LAK)");
                    return;
                }
                JSONObject routeArgs = new JSONObject()
                        .put("denomination", Integer.parseInt(parts[2]))
                        .put("country", parts[3])
                        .put("routeToCashbox", parts[1].equals("cashbox"));
                ssp.command("SET_DENOMINATION_ROUTE", routeArgs).thenAccept(result -> {
                    System.out.println("✅ Route set: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "refill":
                if (parts.length < 2) {
                    System.out.println("❌ Usage: refill on/off/get");
                    return;
                }
                JSONObject refillArgs = new JSONObject().put("mode", parts[1]);
                ssp.command("SET_REFILL_MODE", refillArgs).thenAccept(result -> {
                    System.out.println("✅ Refill mode: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "reporting":
                if (parts.length < 2) {
                    System.out.println("❌ Usage: reporting value/channel");
                    return;
                }
                JSONObject reportArgs = new JSONObject().put("reportBy", parts[1]);
                ssp.command("SET_VALUE_REPORTING_TYPE", reportArgs).thenAccept(result -> {
                    System.out.println("✅ Reporting type set: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            // ========== NOTE HANDLING ==========
            case "hold":
                ssp.command("HOLD").thenAccept(result -> {
                    System.out.println("✅ Note held: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "rejectnote":
                ssp.command("REJECT_BANKNOTE").thenAccept(result -> {
                    System.out.println("✅ Note rejected: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            // ========== PAYOUT COMMANDS ==========
            case "payout":
                if (parts.length < 3) {
                    System.out.println("❌ Usage: payout <amount> <country> (e.g., payout 10000 LAK)");
                    return;
                }
                JSONObject payoutArgs = new JSONObject()
                        .put("amount", Integer.parseInt(parts[1]))
                        .put("country_code", parts[2])
                        .put("test", false);
                ssp.command("PAYOUT_AMOUNT", payoutArgs).thenAccept(result -> {
                    System.out.println("✅ Payout: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "float":
                if (parts.length < 4) {
                    System.out.println("❌ Usage: float <min> <amount> <country> (e.g., float 1000 10000 LAK)");
                    return;
                }
                JSONObject floatArgs = new JSONObject()
                        .put("min_possible_payout", Integer.parseInt(parts[1]))
                        .put("amount", Integer.parseInt(parts[2]))
                        .put("country_code", parts[3])
                        .put("test", false);
                ssp.command("FLOAT_AMOUNT", floatArgs).thenAccept(result -> {
                    System.out.println("✅ Float: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "floatby":
                if (parts.length < 4) {
                    System.out.println("❌ Usage: floatby <number> <value> <country> (e.g., floatby 5 10000 LAK)");
                    return;
                }
                JSONArray floatValues = new JSONArray();
                JSONObject val = new JSONObject()
                        .put("number", Integer.parseInt(parts[1]))
                        .put("denomination", Integer.parseInt(parts[2]))
                        .put("country_code", parts[3]);
                floatValues.put(val);

                JSONObject floatByArgs = new JSONObject()
                        .put("value", floatValues)
                        .put("test", false);
                ssp.command("FLOAT_BY_DENOMINATION", floatByArgs).thenAccept(result -> {
                    System.out.println("✅ Float by denomination: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "payoutby":
                if (parts.length < 4) {
                    System.out.println("❌ Usage: payoutby <number> <value> <country> (e.g., payoutby 5 10000 LAK)");
                    return;
                }
                JSONArray payoutValues = new JSONArray();
                JSONObject pval = new JSONObject()
                        .put("number", Integer.parseInt(parts[1]))
                        .put("denomination", Integer.parseInt(parts[2]))
                        .put("country_code", parts[3]);
                payoutValues.put(pval);

                JSONObject payoutByArgs = new JSONObject()
                        .put("value", payoutValues)
                        .put("test", false);
                ssp.command("PAYOUT_BY_DENOMINATION", payoutByArgs).thenAccept(result -> {
                    System.out.println("✅ Payout by denomination: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "getlevel":
                if (parts.length < 3) {
                    System.out.println("❌ Usage: getlevel <value> <country> (e.g., getlevel 10000 LAK)");
                    return;
                }
                JSONObject levelArgs = new JSONObject()
                        .put("amount", Integer.parseInt(parts[1]))
                        .put("country_code", parts[2]);
                ssp.command("GET_DENOMINATION_LEVEL", levelArgs).thenAccept(result -> {
                    System.out.println("✅ Denomination level: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "empty":
                ssp.command("EMPTY_ALL").thenAccept(result -> {
                    System.out.println("✅ Empty all: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "smartempty":
                ssp.command("SMART_EMPTY").thenAccept(result -> {
                    System.out.println("✅ Smart empty: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "halt":
                ssp.command("HALT_PAYOUT").thenAccept(result -> {
                    System.out.println("✅ Halt payout: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "enablepayout":
                JSONObject enablePayoutArgs = new JSONObject()
                        .put("REQUIRE_FULL_STARTUP", false)
                        .put("OPTIMISE_FOR_PAYIN_SPEED", false);
                ssp.command("ENABLE_PAYOUT_DEVICE", enablePayoutArgs).thenAccept(result -> {
                    System.out.println("✅ Enable payout device: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "disablepayout":
                ssp.command("DISABLE_PAYOUT_DEVICE").thenAccept(result -> {
                    System.out.println("✅ Disable payout device: " + result.toString(2));
                }).get(5, TimeUnit.SECONDS);
                break;

            // ========== DEBUG COMMANDS ==========
            case "raw":
                if (parts.length < 2) {
                    System.out.println("❌ Usage: raw <hex bytes> (e.g., raw 7F8001116582)");
                    return;
                }
                byte[] rawBytes = hexStringToByteArray(parts[1]);
                ssp.sendRaw(rawBytes).thenAccept(response -> {
                    System.out.println("✅ Raw response: " + ssp.bytesToHex(response));
                }).get(5, TimeUnit.SECONDS);
                break;

            case "read":
                System.out.println("Reading data for 5 seconds...");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 5000) {
                    if (ssp.hasBytesAvailable()) {
                        byte[] buffer = new byte[ssp.getBytesAvailable()];
                        int read = ssp.readBytes(buffer);
                        if (read > 0) {
                            baos.write(buffer, 0, read);
                            System.out.println("Read: " + ssp.bytesToHex(buffer));
                        }
                    }
                    Thread.sleep(100);
                }
                System.out.println("Total read: " + ssp.bytesToHex(baos.toByteArray()));
                break;

            case "monitor":
                System.out.println("Monitoring serial port (press Ctrl+C to stop)");
                while (true) {
                    if (ssp.hasBytesAvailable()) {
                        byte[] buffer = new byte[ssp.getBytesAvailable()];
                        int read = ssp.readBytes(buffer);
                        if (read > 0) {
                            System.out.println("Rx: " + ssp.bytesToHex(buffer));
                        }
                    }
                    Thread.sleep(100);
                }

            default:
                System.out.println("❌ Unknown command. Type 'help' for available commands.");
        }
    }

    private static void setupEventListeners() {
        // CREDIT_NOTE - When a note is successfully credited
        ssp.on("CREDIT_NOTE", data -> {
            int channel = data.optInt("channel", 0);
            int[] values = {0, 1000, 2000, 5000, 10000, 20000, 50000, 100000};
            int noteValue = (channel >= 1 && channel <= 7) ? values[channel] : 0;

            System.out.println("\n" + "=".repeat(50));
            System.out.println("💰💰💰 CREDIT NOTE: Channel " + channel + " = " + noteValue + " LAK");
            System.out.println("=".repeat(50));
        });

        // READ_NOTE - When a note is being read
        ssp.on("READ_NOTE", data -> {
            int channel = data.optInt("channel", 0);
            System.out.println("📄 READING NOTE: Channel " + channel);
        });

        // NOTE_STACKING - Note being moved to stacker
        ssp.on("NOTE_STACKING", data -> {
            System.out.println("📦 NOTE STACKING...");
        });

        // NOTE_STACKED - Note successfully stacked
        ssp.on("NOTE_STACKED", data -> {
            System.out.println("✅ NOTE STACKED");
        });

        // NOTE_REJECTED - Note was rejected
        ssp.on("NOTE_REJECTED", data -> {
            String name = data.optString("name", "UNKNOWN");
            String description = data.optString("description", "");
            System.out.println("❌ NOTE REJECTED: " + name + " - " + description);
        });

        // FRAUD_ATTEMPT - Fraud detected
        ssp.on("FRAUD_ATTEMPT", data -> {
            int channel = data.optInt("channel", 0);
            System.out.println("🚨🚨🚨 FRAUD ATTEMPT on channel " + channel + "! 🚨🚨🚨");
        });

        // JAMMED - Note jam detected
        ssp.on("JAMMED", data -> {
            System.out.println("🔴🔴🔴 JAMMED! Check device 🔴🔴🔴");
        });

        // DISABLED/ENABLED
        ssp.on("DISABLED", data -> System.out.println("⏸️ DEVICE DISABLED"));
        ssp.on("ENABLED", data -> System.out.println("▶️ DEVICE ENABLED"));

        // STACKER_FULL
        ssp.on("STACKER_FULL", data -> {
            System.out.println("📦📦📦 STACKER FULL! Empty cashbox 📦📦📦");
        });

        // CASHBOX events
        ssp.on("CASHBOX_REMOVED", data -> System.out.println("📭 CASHBOX REMOVED"));
        ssp.on("CASHBOX_REPLACED", data -> System.out.println("📥 CASHBOX REPLACED"));

        // NOTE_HELD_IN_BEZEL
        ssp.on("NOTE_HELD_IN_BEZEL", data -> {
            JSONObject value = data.optJSONObject("value");
            if (value != null) {
                System.out.println("🔄 NOTE IN ESCROW: " + value.optInt("value", 0)
                        + " " + value.optString("country_code", ""));
            }
        });

        // Payout events
        ssp.on("DISPENSING", data -> System.out.println("💸 DISPENSING..."));
        ssp.on("DISPENSED", data -> System.out.println("💸💸 DISPENSED"));
        ssp.on("EMPTYING", data -> System.out.println("🗑️ EMPTYING..."));
        ssp.on("EMPTY", data -> System.out.println("🗑️✅ EMPTY"));
    }

    private static byte[] hexStringToByteArray(String s) {
        s = s.replace(" ", "");
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
