import { http, HttpResponse } from 'msw';
import { API, IMPORT_BATCHES, SETTINGS, buildRecipient, buildWave, springPage } from './fixtures';

export function invitationWaveHandlers({ wave = buildWave('DRAFT'), recipients = [], settings = SETTINGS, batches = IMPORT_BATCHES } = {}) {
  return [
    http.get(`${API}/invitation-waves/settings`, () => HttpResponse.json(settings)),
    http.get(`${API}/invitation-waves/import-batches`, () => HttpResponse.json(batches)),
    http.get(`${API}/invitation-waves`, () => HttpResponse.json(springPage([wave]))),
    http.get(`${API}/invitation-waves/${wave.id}`, () => HttpResponse.json(wave)),
    http.get(`${API}/invitation-waves/${wave.id}/recipients`, ({ request }) => {
      const url = new URL(request.url);
      const status = url.searchParams.get('status');
      const page = Number(url.searchParams.get('page') ?? 0);
      const size = Number(url.searchParams.get('size') ?? 20);
      const filtered = status ? recipients.filter((recipient) => recipient.status === status) : recipients;
      return HttpResponse.json(springPage(filtered.slice(page * size, (page + 1) * size), { page, size, total: filtered.length }));
    }),
  ];
}

export function sampleRecipients() {
  return [
    buildRecipient(4001, { status: 'PENDING' }),
    buildRecipient(4002, { status: 'QUEUED' }),
    buildRecipient(4003, { status: 'SENT', attempts: 1, sentAt: '2026-11-22T15:10:00Z', lastAttemptAt: '2026-11-22T15:10:00Z' }),
    buildRecipient(4004, { status: 'FAILED', attempts: 2, lastErrorCode: 'PROVIDER_REJECTED', lastAttemptAt: '2026-11-22T16:20:00Z' }),
    buildRecipient(4005, { status: 'ACTIVATED', attempts: 1, sentAt: '2026-11-22T14:00:00Z', lastAttemptAt: '2026-11-22T14:00:00Z' }),
    buildRecipient(4006, { status: 'EXCLUDED', exclusionReason: 'NO_EMAIL', email: null }),
  ];
}

const TRANSITIONS = {
  start: { status: 'RUNNING' },
  pause: { status: 'PAUSED' },
  resume: { status: 'RUNNING' },
  cancel: { status: 'CANCELLED', cancelledAt: '2026-11-23T15:00:00Z' },
};

export function lifecycleHandlers(initial, { recipients = [], overrides = {} } = {}) {
  const state = { wave: initial, calls: [] };
  const post = (action) =>
    http.post(`${API}/invitation-waves/${initial.id}/${action}`, () => {
      state.calls.push(action);
      if (overrides[action]) return overrides[action]();
      state.wave = { ...state.wave, ...TRANSITIONS[action] };
      return HttpResponse.json(state.wave);
    });
  const handlers = [
    ...Object.keys(TRANSITIONS).map(post),
    http.post(`${API}/invitation-waves/${initial.id}/retry-failed`, () => {
      state.calls.push('retry-failed');
      if (overrides['retry-failed']) return overrides['retry-failed']();
      return HttpResponse.json({ requeued: 3 });
    }),
    http.get(`${API}/invitation-waves/${initial.id}`, () => HttpResponse.json(state.wave)),
    ...invitationWaveHandlers({ wave: initial, recipients }),
  ];
  return { state, handlers };
}
