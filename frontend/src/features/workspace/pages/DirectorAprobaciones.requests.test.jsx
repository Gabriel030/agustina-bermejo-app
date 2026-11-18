import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { recordApiRequests } from '@/test/recordApiRequests';
import { settledRequests } from '@/test/apiRequestCount';
import { DirectorAprobaciones } from '@/features/workspace/pages/DirectorAprobaciones';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['DIRECTOR'] } }),
}));

const API = 'http://localhost:8080';

describe('DirectorAprobaciones requests', () => {
  it('e31 requests each pending tray once when the screen mounts', async () => {
    server.use(
      http.get(`${API}/section-evaluation-configs`, () => HttpResponse.json([])),
      http.get(`${API}/graduation-requests`, () => HttpResponse.json([])),
      http.get(`${API}/director-approval-requests`, () => HttpResponse.json([])),
      http.get(`${API}/authorization-requests`, () => HttpResponse.json({ items: [], page: 0, size: 50, totalElements: 0 })),
    );
    const recorder = recordApiRequests();
    renderWithAppCache(<DirectorAprobaciones />, { route: '/director/aprobaciones' });
    await screen.findByText('Nada de facturación esperando tu decisión');
    const requests = await settledRequests(recorder);
    expect(requests.filter((request) => request.startsWith('GET /graduation-requests'))).toHaveLength(1);
    expect(requests.filter((request) => request.startsWith('GET /section-evaluation-configs'))).toHaveLength(1);
    expect(requests.filter((request) => request.startsWith('GET /director-approval-requests'))).toHaveLength(1);
    expect(requests.filter((request) => request.startsWith('GET /authorization-requests'))).toHaveLength(1);
    expect(requests.filter((request) => request.startsWith('GET /academic-offering-cycles'))).toHaveLength(1);
    recorder.stop();
  });
});
