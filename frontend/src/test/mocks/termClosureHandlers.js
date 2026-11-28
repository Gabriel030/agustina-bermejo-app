import { http, HttpResponse } from 'msw';
import { pendingTermClosures } from '@/test/fixtures/termClosures';

const API = 'http://localhost:8080';

export const termClosureHandlers = [
  http.get(`${API}/commission-closures/pending-term-closures`, () => HttpResponse.json(pendingTermClosures())),
];
