import { defineConfig } from '@playwright/test';

const backendUrl = 'http://localhost:8090';
const frontendPort = process.env.E2E_FRONTEND_PORT ?? '4174';
const frontendUrl = `http://localhost:${frontendPort}`;

export default defineConfig({
  testDir: './specs/offline',
  fullyParallel: true,
  workers: 2,
  retries: process.env.CI ? 1 : 0,
  timeout: 90_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: frontendUrl,
    trace: 'on-first-retry',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'es-AR',
    serviceWorkers: 'allow',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        browserName: 'chromium',
        launchOptions: process.env.E2E_CHROMIUM_PATH ? { executablePath: process.env.E2E_CHROMIUM_PATH } : {},
      },
    },
  ],
  webServer: {
    command: `npm run build && npm run preview -- --port ${frontendPort} --strictPort`,
    cwd: '../frontend',
    stdout: 'pipe',
    stderr: 'pipe',
    url: frontendUrl,
    timeout: 300_000,
    reuseExistingServer: !process.env.CI,
    env: { VITE_API_BASE_URL: backendUrl },
  },
});
