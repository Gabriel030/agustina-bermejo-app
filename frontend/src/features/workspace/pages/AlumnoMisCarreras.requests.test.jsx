import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { recordApiRequests } from '@/test/recordApiRequests';
import { countOf, settledRequests } from '@/test/apiRequestCount';
import { AlumnoMisCarreras } from '@/features/workspace/pages/AlumnoMisCarreras';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['ALUMNO'] } }),
}));

const API = 'http://localhost:8080';

const MODULES_FULL = {
  billing: { mode: 'FULL' },
  fiscalInvoicing: { enabled: false },
  analytics: { enabled: false },
  paymentMethods: [],
  billingNotifications: { enabled: false },
  externalBilling: { enabled: false },
};
const REGULAR_STUDENT = {
  kind: 'PROGRAM',
  enrollmentId: 5,
  regular: true,
  reasons: [],
  attendanceControlled: true,
  billingMode: 'FULL',
  institutionallyRegular: true,
  paymentStanding: 'VERIFIED',
};

function mockStudent() {
  server.use(
    http.get(`${API}/me/program-enrollments`, () =>
      HttpResponse.json([
        {
          id: 5,
          studentId: 1,
          programId: 2,
          programName: 'Profesorado de Educación Inicial',
          status: 'ENROLLED',
          enrollmentDate: '2026-03-01T10:00:00',
        },
      ]),
    ),
    http.get(`${API}/me/program-enrollments/5/progress`, () =>
      HttpResponse.json({
        programEnrollmentId: 5,
        creditosAcumulados: 10,
        creditosRequeridos: 144,
        obligatoriasPendientes: [],
        gruposElectivos: [],
        elegibleParaCompletar: false,
        esRegular: true,
        promedioGeneral: 8.1,
      }),
    ),
    http.get(`${API}/me/regular-certificates/eligibility`, () => HttpResponse.json(REGULAR_STUDENT)),
    http.get(`${API}/me/modules`, () => HttpResponse.json(MODULES_FULL)),
    http.get(`${API}/*`, () => HttpResponse.json([])),
  );
}

describe('AlumnoMisCarreras requests', () => {
  it('e31 requests the enrollment lists once each when the screen mounts', async () => {
    mockStudent();
    const recorder = recordApiRequests();
    renderWithAppCache(<AlumnoMisCarreras />, { route: '/alumno/mis-carreras' });
    await screen.findAllByText(/Profesorado de Educación Inicial/);
    const requests = await settledRequests(recorder);
    expect(countOf(requests, 'GET /me/program-enrollments')).toBe(1);
    expect(countOf(requests, 'GET /me/section-enrollments')).toBe(1);
    expect(countOf(requests, 'GET /me/program-enrollments/5/progress')).toBe(1);
    expect(requests.filter((request, index) => requests.indexOf(request) !== index)).toEqual([]);
    recorder.stop();
  });

  it('e31 does not refetch the section enrollments when switching tabs', async () => {
    mockStudent();
    const recorder = recordApiRequests();
    renderWithAppCache(<AlumnoMisCarreras />, { route: '/alumno/mis-carreras' });
    await screen.findAllByText(/Profesorado de Educación Inicial/);
    await settledRequests(recorder);
    const tabs = screen.getAllByRole('tab');
    await userEvent.click(tabs[1]);
    await userEvent.click(tabs[0]);
    const requests = await settledRequests(recorder);
    expect(countOf(requests, 'GET /me/section-enrollments')).toBe(1);
    recorder.stop();
  });
});
