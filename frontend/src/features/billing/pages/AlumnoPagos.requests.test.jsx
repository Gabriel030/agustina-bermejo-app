import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { recordApiRequests } from '@/test/recordApiRequests';
import { settledRequests } from '@/test/apiRequestCount';
import { annualQuote } from '@/test/fixtures/annualUpfront';
import { AlumnoPagos } from './AlumnoPagos';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['ALUMNO'] } }),
}));

const API = 'http://localhost:8080';

describe('AlumnoPagos requests', () => {
  it('e31 requests every dataset once and the annual quotes right after the enrollments', async () => {
    server.use(
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          { id: 1, studentId: 1, programId: 2, programName: 'Profesorado de Educación Inicial', status: 'ENROLLED', enrollmentDate: '2026-03-01T10:00:00' },
        ]),
      ),
      http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([])),
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
      http.get(`${API}/me/program-enrollment-applications`, () => HttpResponse.json([])),
      http.get(`${API}/me/credit-balance`, () =>
        HttpResponse.json({
          currency: 'ARS',
          unallocatedCredit: { amount: '0.00', currency: 'ARS' },
          reservedByPendingRefunds: { amount: '0.00', currency: 'ARS' },
          availableCredit: { amount: '0.00', currency: 'ARS' },
        }),
      ),
      http.get(`${API}/me/program-enrollments/1/annual-upfront/quote`, () => HttpResponse.json(annualQuote())),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const recorder = recordApiRequests();
    renderWithAppCache(<AlumnoPagos />, { route: '/alumno/pagos' });
    await screen.findAllByText(/Profesorado de Educación Inicial/);
    const requests = await settledRequests(recorder);
    expect(requests.filter((request, index) => requests.indexOf(request) !== index)).toEqual([]);
    expect(requests).toContain('GET /me/program-enrollments/1/annual-upfront/quote');
    expect(requests).toContain('GET /me/program-enrollments/1/annual-upfront-requests');
    recorder.stop();
  });
});
