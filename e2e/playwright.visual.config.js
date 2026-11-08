import { defineConfig } from '@playwright/test';

const port = process.env.E2E_VISUAL_PORT ?? '4180';

export default defineConfig({
  testDir: './specs/visual',
  fullyParallel: false,
  workers: 1,
  timeout: 60_000,
  reporter: [['list']],
  expect: { toHaveScreenshot: { maxDiffPixelRatio: 0.001, animations: 'disabled' } },
  snapshotPathTemplate: '{testDir}/__screenshots__/{projectName}/{testFilePath}/{arg}{ext}',
  use: { baseURL: `http://localhost:${port}`, locale: 'es-AR', reducedMotion: 'reduce' },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: {
    command: `VITE_ENABLE_CATALOG=true npm run build && npm run preview -- --port ${port} --strictPort`,
    cwd: '../frontend',
    url: `http://localhost:${port}/dev/catalog`,
    timeout: 180_000,
    reuseExistingServer: false,
  },
});
