import { defineConfig } from '@playwright/test';
import baseConfig from './playwright.config.js';

const reuseServers = process.env.E2E_REUSE_SERVERS === '1';
const [backendServer, frontendServer] = baseConfig.webServer;

export default defineConfig({
  ...baseConfig,
  testDir: './specs/demo',
  testIgnore: undefined,
  webServer: [
    {
      ...backendServer,
      command: backendServer.command.replace('SystemTestApplication', 'DemoSystemTestApplication'),
      reuseExistingServer: reuseServers,
    },
    { ...frontendServer, reuseExistingServer: reuseServers },
  ],
});
