import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { roles } from './screens.mjs';
import { buildReport } from './report.mjs';

const FRONT = process.env.E31_FRONT ?? 'http://localhost:5173';
const API = process.env.E31_API ?? 'http://localhost:8461';
const PASSWORD = process.env.E31_PASSWORD ?? 'Test1234';
const ONLY = process.env.E31_ROLES?.split(',');
const OUT = process.env.E31_OUT ?? fileURLToPath(new URL('./out/', import.meta.url));
const SETTLE_MS = 1500;
const MAX_WAIT_MS = 45000;
const NETWORK = { offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8 };

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function newThrottledPage(browser, username) {
  const context = await browser.newContext({ locale: 'es-AR', viewport: { width: 1366, height: 800 } });
  await context.addInitScript(() => {
    window.__lcp = 0;
    new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) window.__lcp = entry.startTime;
    }).observe({ type: 'largest-contentful-paint', buffered: true });
  });
  const page = await context.newPage();
  await login(page, username);
  const cdp = await context.newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.setCacheDisabled', { cacheDisabled: true });
  await cdp.send('Network.emulateNetworkConditions', NETWORK);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  const requests = new Map();
  cdp.on('Network.requestWillBeSent', (e) => {
    if (e.redirectResponse) return;
    requests.set(e.requestId, { method: e.request.method, url: e.request.url, type: e.type, start: e.timestamp * 1000, end: null, bytes: 0, status: null });
  });
  cdp.on('Network.responseReceived', (e) => {
    const r = requests.get(e.requestId);
    if (r) r.status = e.response.status;
  });
  cdp.on('Network.loadingFinished', (e) => {
    const r = requests.get(e.requestId);
    if (r) { r.end = e.timestamp * 1000; r.bytes = e.encodedDataLength; }
  });
  cdp.on('Network.loadingFailed', (e) => {
    const r = requests.get(e.requestId);
    if (r) r.end = e.timestamp * 1000;
  });
  return { context, page, requests };
}

async function waitForIdle(page, requests) {
  const deadline = Date.now() + MAX_WAIT_MS;
  let quietSince = Date.now();
  while (Date.now() < deadline) {
    const pending = [...requests.values()].some((r) => r.end === null && r.url.startsWith(API));
    if (pending) quietSince = Date.now();
    else if (Date.now() - quietSince >= SETTLE_MS) return true;
    await sleep(100);
  }
  return false;
}

function summarize(requests, t0) {
  const api = [...requests.values()].filter((r) => r.url.startsWith(API) && r.type !== 'Preflight' && r.method !== 'OPTIONS');
  api.sort((a, b) => a.start - b.start);
  const depth = new Map();
  const after = new Map();
  api.forEach((r, index) => {
    let best = 0;
    let predecessor = null;
    api.forEach((other, j) => {
      if (j !== index && other.end !== null && other.end <= r.start && (depth.get(j) ?? 1) > best) {
        best = depth.get(j);
        predecessor = other;
      } else if (j !== index && other.end !== null && other.end <= r.start && best === 0) {
        best = 1;
        predecessor = other;
      }
    });
    depth.set(index, best + 1);
    after.set(index, predecessor);
  });
  const path = (u) => u.replace(API, '');
  const counts = new Map();
  api.forEach((r) => counts.set(`${r.method} ${path(r.url)}`, (counts.get(`${r.method} ${path(r.url)}`) ?? 0) + 1));
  const duplicates = [...counts].filter(([, n]) => n > 1).map(([key, n]) => ({ request: key, times: n }));
  const cascades = api
    .map((r, i) => ({ r, i }))
    .filter(({ i }) => depth.get(i) >= 2)
    .map(({ r, i }) => ({ request: `${r.method} ${path(r.url)}`, level: depth.get(i), after: `${after.get(i).method} ${path(after.get(i).url)}` }));
  const statics = [...requests.values()].filter((r) => !r.url.startsWith(API) && r.type !== 'Preflight');
  return {
    apiCount: api.length,
    duplicateCount: duplicates.reduce((sum, d) => sum + d.times - 1, 0),
    cascadeDepth: Math.max(1, ...[...depth.values()]),
    parallelFirstWave: api.filter((_, i) => depth.get(i) === 1).length,
    cascadeCount: cascades.length,
    apiBytes: api.reduce((sum, r) => sum + r.bytes, 0),
    staticCount: statics.length,
    staticBytes: statics.reduce((sum, r) => sum + r.bytes, 0),
    errors: api.filter((r) => r.status >= 400).length,
    duplicates,
    cascades,
    requests: api.map((r) => ({ request: `${r.method} ${path(r.url)}`, status: r.status, bytes: r.bytes, startMs: Math.round(r.start - t0), durationMs: r.end === null ? null : Math.round(r.end - r.start) })),
  };
}

async function login(page, username) {
  await page.goto(`${FRONT}/login`);
  await page.getByPlaceholder('DNI o email').fill(username);
  await page.getByPlaceholder('••••••••').fill(PASSWORD);
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30000 });
  await page.waitForLoadState('networkidle');
}

async function measureScreen(browser, username, path) {
  const { context, page, requests } = await newThrottledPage(browser, username);
  const started = Date.now();
  let loadError = null;
  await page.goto(`${FRONT}${path}`, { waitUntil: 'commit', timeout: MAX_WAIT_MS }).catch((e) => { loadError = e.message; });
  await page.waitForLoadState('load', { timeout: MAX_WAIT_MS }).catch(() => {});
  const idle = await waitForIdle(page, requests);
  const fullMs = Date.now() - started - SETTLE_MS;
  const lcp = await page.evaluate(() => window.__lcp).catch(() => 0);
  const finalPath = new URL(page.url()).pathname;
  const first = Math.min(...[...requests.values()].map((r) => r.start));
  const result = { path, finalPath, redirected: finalPath !== path, usefulContentMs: Math.round(lcp), fullScreenMs: fullMs, networkIdle: idle, loadError, ...summarize(requests, first) };
  await context.close();
  return result;
}

async function measureAction(browser, username, prefix, action) {
  const { context, page, requests } = await newThrottledPage(browser, username);
  await page.goto(`${FRONT}${prefix}/${action.from}`, { waitUntil: 'load', timeout: MAX_WAIT_MS }).catch(() => {});
  await waitForIdle(page, requests);
  const target = page.locator(action.click).first();
  const visible = await target.waitFor({ state: 'visible', timeout: 15000 }).then(() => true).catch(() => false);
  if (!visible) { await context.close(); return { name: action.name, from: action.from, measured: false, reason: 'elemento no visible (sin datos o selector distinto)' }; }
  const before = page.url();
  const startCount = requests.size;
  const marker = Date.now();
  await target.click();
  let lastActivity = Date.now();
  const deadline = marker + MAX_WAIT_MS;
  while (Date.now() < deadline) {
    const pending = [...requests.values()].some((r) => r.end === null && r.url.startsWith(API));
    if (pending || page.url() !== before) lastActivity = Date.now();
    if (Date.now() - lastActivity >= SETTLE_MS) break;
    await sleep(50);
  }
  const resultMs = lastActivity - marker;
  const urlChangeMs = null;
  const fresh = [...requests.values()].slice(startCount);
  const api = fresh.filter((r) => r.url.startsWith(API) && r.method !== 'OPTIONS');
  await context.close();
  return { name: action.name, from: action.from, measured: true, navigated: page.url() !== before, urlChangeMs, clickToResultMs: resultMs, apiCount: api.length, apiBytes: api.reduce((s, r) => s + r.bytes, 0) };
}

const browser = await chromium.launch({ headless: true });
const results = { generatedAt: new Date().toISOString(), network: '4G simulada: 150 ms RTT, 1,6 Mbps bajada, 750 kbps subida', cpu: 'x4', roles: {} };
for (const [roleName, role] of Object.entries(roles)) {
  if (ONLY && !ONLY.includes(roleName)) continue;
  console.log(`[${roleName}] login ${role.login}`);
  const screens = [];
  for (const screen of role.screens) {
    const r = await measureScreen(browser, role.login, `${role.prefix}/${screen}`);
    console.log(`  ${r.path}: ${r.apiCount} peticiones, ${r.duplicateCount} dup, cascada ${r.cascadeDepth}, ${r.fullScreenMs} ms${r.redirected ? ` -> ${r.finalPath}` : ''}`);
    screens.push(r);
  }
  const actions = [];
  for (const action of role.actions) actions.push(await measureAction(browser, role.login, role.prefix, action));
  results.roles[roleName] = { login: role.login, screens, actions };
}
await browser.close();
mkdirSync(OUT, { recursive: true });
writeFileSync(`${OUT}/latest.json`, JSON.stringify(results, null, 2));
writeFileSync(fileURLToPath(new URL('./report.md', import.meta.url)), buildReport(results));
console.log('listo: perf/e31/report.md');
