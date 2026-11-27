import { test } from 'node:test';
import assert from 'node:assert/strict';
import { activeRequests, stalledRequests } from './inflight.mjs';

const API = 'http://api';
const NOW = 100000;
const request = (overrides) => ({ method: 'GET', url: `${API}/x`, seenAt: NOW - 100, end: null, ...overrides });
const options = { api: API, hungMs: 10000, now: NOW };

test('a request started before the click never counts as in flight', () => {
  const requests = new Map([['old', request({ seenAt: NOW - 100 })]]);
  assert.equal(activeRequests(requests, { ...options, ignored: new Set(['old']) }).length, 0);
});

test('a request started after the click counts as in flight', () => {
  const requests = new Map([['old', request()], ['fresh', request({ url: `${API}/y` })]]);
  const active = activeRequests(requests, { ...options, ignored: new Set(['old']) });
  assert.deepEqual(active.map(([id]) => id), ['fresh']);
});

test('a request that never finishes stops blocking once it is older than the hung threshold', () => {
  const requests = new Map([['stuck', request({ seenAt: NOW - 20000 })]]);
  assert.equal(activeRequests(requests, options).length, 0);
  assert.deepEqual(stalledRequests(requests, options), ['GET /x']);
});

test('finished and non api requests are ignored', () => {
  const requests = new Map([['done', request({ end: NOW })], ['static', request({ url: 'http://front/a.js' })]]);
  assert.equal(activeRequests(requests, options).length, 0);
  assert.deepEqual(stalledRequests(requests, options), []);
});
