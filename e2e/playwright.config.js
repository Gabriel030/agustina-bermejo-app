import { defineConfig } from '@playwright/test';

const backendPort = process.env.E2E_BACKEND_PORT ?? '8090';
const frontendPort = process.env.E2E_FRONTEND_PORT ?? '4173';
const backendUrl = `http://localhost:${backendPort}`;
const frontendUrl = `http://localhost:${frontendPort}`;

export default defineConfig({
  testDir: './specs',
  fullyParallel: true,
  workers: 2,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: frontendUrl,
    trace: 'on-first-retry',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'es-AR',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: [
    {
      command: './mvnw -o -q spring-boot:test-run -Dspring-boot.run.main-class=com.red_escuela.backend.testsupport.system.SystemTestApplication',
      cwd: '../backend',
      stdout: 'pipe',
      stderr: 'pipe',
      url: `${backendUrl}/actuator/health`,
      timeout: 300_000,
      reuseExistingServer: false,
      env: {
        SYSTEM_TEST_BACKEND_PORT: backendPort,
        SYSTEM_TEST_FRONTEND_ORIGIN: frontendUrl,
      },
    },
    {
      command: `npm run build && npm run preview -- --port ${frontendPort} --strictPort`,
      cwd: '../frontend',
      stdout: 'pipe',
      stderr: 'pipe',
      url: frontendUrl,
      timeout: 300_000,
      reuseExistingServer: false,
      env: { VITE_API_BASE_URL: backendUrl },
    },
  ],
});
