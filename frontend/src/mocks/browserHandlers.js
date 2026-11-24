import { http, HttpResponse } from 'msw';
import { handlers } from '@/test/mocks/handlers';
import { mockProfileFor } from '@/mocks/mockRoles';

const API = 'http://localhost:8080';

export function buildBrowserHandlers(roleKey) {
  return [
    http.get(`${API}/me`, () => HttpResponse.json(mockProfileFor(roleKey))),
    ...handlers,
    http.get(`${API}/*`, () => HttpResponse.json([])),
  ];
}
