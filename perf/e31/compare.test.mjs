import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildBaseline, compareToBaseline, formatComparison } from './compare.mjs';

const screen = (path, overrides = {}) => ({ path, apiCount: 8, duplicateCount: 1, cascadeDepth: 3, errors: 0, ...overrides });
const resultsWith = (screens, details = []) => ({ generatedAt: 't', roles: { Director: { screens, details } } });

const baseline = buildBaseline(resultsWith([screen('/director/alumnos'), screen('/director/financiero', { apiCount: 15 })]), 'test');

test('an identical run has no regressions', () => {
  const outcome = compareToBaseline(baseline, resultsWith([screen('/director/alumnos'), screen('/director/financiero', { apiCount: 15 })]));
  assert.deepEqual(outcome.regressions, []);
});

test('a screen with more requests, duplicates, levels or errors is a regression', () => {
  const worse = resultsWith([
    screen('/director/alumnos', { apiCount: 9, duplicateCount: 2, cascadeDepth: 4, errors: 1 }),
    screen('/director/financiero', { apiCount: 15 }),
  ]);
  const outcome = compareToBaseline(baseline, worse);
  assert.deepEqual(outcome.regressions.map((r) => r.metric), ['peticiones', 'duplicadas', 'niveles de cascada', 'errores HTTP']);
  assert.match(formatComparison(outcome), /REGRESIÓN Director \/director\/alumnos: peticiones 8 → 9/);
});

test('fewer requests is an improvement and not a regression', () => {
  const better = resultsWith([screen('/director/alumnos', { apiCount: 5 }), screen('/director/financiero', { apiCount: 15 })]);
  const outcome = compareToBaseline(baseline, better);
  assert.deepEqual(outcome.regressions, []);
  assert.equal(outcome.improvements.length, 1);
});

test('screens missing from either side are reported without failing', () => {
  const outcome = compareToBaseline(baseline, resultsWith([screen('/director/alumnos'), screen('/director/nueva')]));
  assert.deepEqual(outcome.notInBaseline, ['Director /director/nueva']);
  assert.deepEqual(outcome.notMeasured, ['Director /director/financiero']);
  assert.deepEqual(outcome.regressions, []);
});

test('measured details are keyed by route and unmeasured details are ignored', () => {
  const results = resultsWith([], [{ route: 'alumnos/:id', measured: true, ...screen('/director/alumnos/7') }, { route: 'x/:id', measured: false }]);
  assert.deepEqual(Object.keys(buildBaseline(results, 'test').screens), ['Director detalle alumnos/:id']);
});
