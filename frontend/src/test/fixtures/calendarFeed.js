import { http, HttpResponse } from 'msw';
import { withContract } from '@/test/contract/withContract';

const API = 'http://localhost:8080';

export const ISSUED_FEED_URL = 'http://localhost:8080/public/calendar/feed.ics?token=secret-token-123';

export function inactiveFeed() {
  return { active: false, createdAt: null, lastUsedAt: null };
}

export function activeFeed(overrides = {}) {
  return { active: true, createdAt: '2026-11-18T10:00:00', lastUsedAt: null, ...overrides };
}

export function calendarFeedHandler(feed = inactiveFeed) {
  return withContract(
    http.get(`${API}/me/calendar/feed`, () => HttpResponse.json(typeof feed === 'function' ? feed() : feed)),
    { apiBase: API, baseline: { 'GET /me/calendar/feed': true } },
  );
}

export function issuedTokenResponse(overrides = {}) {
  return { url: ISSUED_FEED_URL, token: 'secret-token-123', createdAt: '2026-11-21T10:00:00', ...overrides };
}
