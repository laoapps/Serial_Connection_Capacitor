// filepath: capacitor.config.ts
import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.example.app',
  appName: 'MyApp',
  webDir: 'www',
  bundledWebRuntime: false,
  plugins: {
    SerialConnectionCapacitor: {
      // Plugin-specific configuration if needed
    }
  }
};

export default config;