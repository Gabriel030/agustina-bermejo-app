import { http, HttpResponse } from 'msw';
import { handlers } from '@/test/mocks/handlers';
import { mockProfileFor } from '@/mocks/mockRoles';

const API = 'http://localhost:8080';

export function buildBrowserHandlers(roleKey) {
  return [
    http.get(`${API}/me`, () => HttpResponse.json(mockProfileFor(roleKey))),
    ...handlers,
    http.get(`${API}/*`, ({ request }) => {
      console.warn(`[msw] GET sin handler, responde []: ${new URL(request.url).pathname}`);
      return HttpResponse.json([]);
    }),
    http.all(`${API}/*`, ({ request }) => {
      console.warn(`[msw] ${request.method} sin handler, responde 404: ${new URL(request.url).pathname}`);
      return HttpResponse.json(
        { code: 'MOCK_NOT_IMPLEMENTED', requestId: 'req-mock-0001', retryable: false, violations: [] },
        { status: 404 },
      );
    }),
  ];
}
