import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { recordApiRequests } from '@/test/recordApiRequests';
import { settledRequests } from '@/test/apiRequestCount';
import { studentHomeHandlers } from '@/test/fixtures/studentHome';
import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez' } }),
}));

const API = 'http://localhost:8080';

describe('AlumnoInicio requests', () => {
  it('e31 requests each dataset once when the screen mounts', async () => {
    server.use(
      http.get(`${API}/me/modules`, () =>
        HttpResponse.json({
          billing: { mode: 'FULL' },
          fiscalInvoicing: { enabled: false },
          analytics: { enabled: false },
          paymentMethods: [],
          billingNotifications: { enabled: false },
          externalBilling: { enabled: false },
        }),
      ),
      ...studentHomeHandlers(),
      http.get(`${API}/me/announcements`, () => HttpResponse.json([])),
      http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
    );
    const recorder = recordApiRequests();
    renderWithAppCache(<AlumnoInicio />, { route: '/alumno/inicio' });
    await screen.findByText('Todavía no hay novedades');
    const requests = await settledRequests(recorder);
    expect(requests).toContain('GET /me/program-enrollment-applications');
    expect(requests.filter((request, index) => requests.indexOf(request) !== index)).toEqual([]);
    recorder.stop();
  });
});
