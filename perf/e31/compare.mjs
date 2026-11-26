import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const METRICS = [
  ['requests', 'apiCount', 'peticiones'],
  ['duplicates', 'duplicateCount', 'duplicadas'],
  ['cascadeDepth', 'cascadeDepth', 'niveles de cascada'],
  ['errors', 'errors', 'errores HTTP'],
];

export function screenEntries(results) {
  const entries = {};
  for (const [role, data] of Object.entries(results.roles ?? {})) {
    for (const screen of data.screens ?? []) entries[`${role} ${screen.path}`] = screen;
    for (const detail of data.details ?? []) if (detail.measured) entries[`${role} detalle ${detail.route}`] = detail;
  }
  return entries;
}

export function buildBaseline(results, source) {
  const screens = {};
  for (const [key, screen] of Object.entries(screenEntries(results))) {
    screens[key] = Object.fromEntries(METRICS.map(([name, field]) => [name, screen[field]]));
  }
  return { meta: { source, generatedAt: results.generatedAt }, screens };
}

export function compareToBaseline(baseline, results) {
  const current = screenEntries(results);
  const regressions = [];
  const improvements = [];
  for (const [key, expected] of Object.entries(baseline.screens)) {
    const screen = current[key];
    if (!screen) continue;
    for (const [name, field, label] of METRICS) {
      if (screen[field] > expected[name]) regressions.push({ screen: key, metric: label, baseline: expected[name], current: screen[field] });
      else if (screen[field] < expected[name]) improvements.push({ screen: key, metric: label, baseline: expected[name], current: screen[field] });
    }
  }
  return {
    regressions,
    improvements,
    notInBaseline: Object.keys(current).filter((key) => !baseline.screens[key]),
    notMeasured: Object.keys(baseline.screens).filter((key) => !current[key]),
  };
}

export function formatComparison(outcome) {
  const lines = [];
  outcome.regressions.forEach((r) => lines.push(`REGRESIÓN ${r.screen}: ${r.metric} ${r.baseline} → ${r.current}`));
  outcome.improvements.forEach((r) => lines.push(`mejora ${r.screen}: ${r.metric} ${r.baseline} → ${r.current}`));
  outcome.notInBaseline.forEach((key) => lines.push(`sin línea base ${key}`));
  outcome.notMeasured.forEach((key) => lines.push(`no medida en esta corrida ${key}`));
  lines.push(outcome.regressions.length ? `${outcome.regressions.length} regresiones` : 'sin regresiones');
  return lines.join('\n');
}

function main(argv) {
  const update = argv.includes('--update');
  const latestPath = argv.find((arg) => !arg.startsWith('--')) ?? fileURLToPath(new URL('./out/latest.json', import.meta.url));
  const baselinePath = fileURLToPath(new URL('./baseline.json', import.meta.url));
  const results = JSON.parse(readFileSync(latestPath, 'utf8'));
  if (update) {
    const baseline = JSON.parse(readFileSync(baselinePath, 'utf8'));
    const merged = buildBaseline(results, `perf/e31 npm run measure, ${results.generatedAt}`);
    writeFileSync(baselinePath, `${JSON.stringify({ ...merged, screens: { ...baseline.screens, ...merged.screens } }, null, 2)}\n`);
    console.log(`línea base actualizada: ${Object.keys(merged.screens).length} pantallas`);
    return 0;
  }
  const outcome = compareToBaseline(JSON.parse(readFileSync(baselinePath, 'utf8')), results);
  console.log(formatComparison(outcome));
  return outcome.regressions.length ? 1 : 0;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) process.exit(main(process.argv.slice(2)));
