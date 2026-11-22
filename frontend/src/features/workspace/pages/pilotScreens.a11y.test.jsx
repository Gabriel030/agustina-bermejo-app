import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { axe } from '@/test/axe';
import { installFixedClock } from '@/test/fixedClock';
import { renderWithProviders } from '@/test/renderWithProviders';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { contractList, contractObject } from '@/test/fixtures/fromContract';
import { calendarHandler } from '@/test/fixtures/studentCalendar';
import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';
import { AlumnoCalendario } from '@/features/workspace/pages/AlumnoCalendario';
import { DirectorInicio } from '@/features/workspace/pages/StaffHome';
import { AuditLogPage } from '@/features/audit/pages/AuditLogPage';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['DIRECTOR'] } }),
}));

const API = 'http://localhost:8080';

installFixedClock({ now: '2026-11-21T12:00:00-03:00' });

const DIRECTOR_SOURCES = [
  '/holidays',
  '/terms',
  '/graduation-requests',
  '/academic-offering-cycles',
  '/section-evaluation-configs',
  '/director-approval-requests',
  '/commission-closures',
  '/exam-boards',
  '/authorization-requests',
  '/regular-certificate-requests',
  '/program-enrollment-applications/pending',
  '/attendance/justifications',
  '/attendance/change-requests',
];

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

describe('pilot screens accessibility (jsdom does not measure color contrast, covered by tokens.test.js)', () => {
  it('studentHomeHasNoAxeViolations', async () => {
    server.use(
      http.get(`${API}/me/enrollment-payment-status`, () => HttpResponse.json([])),
      http.get(`${API}/me/course-enrollments`, () => HttpResponse.json([])),
      http.get(`${API}/me/announcements`, () => HttpResponse.json([])),
    );
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/alumno/inicio" element={<AlumnoInicio />} />
      </Routes>,
      { route: '/alumno/inicio' },
    );
    expect(await screen.findByText('Todavía no hay novedades')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('staffHomeHasNoAxeViolations', async () => {
    server.use(
      http.get(`${API}/me/modules`, () => HttpResponse.json(contractObject('/me/modules', { billing: { mode: 'OFF' } }))),
      ...DIRECTOR_SOURCES.map((path) => http.get(`${API}${path}`, () => HttpResponse.json(contractList(path, [])))),
    );
    const { container } = renderWithAppCache(<DirectorInicio />, { route: '/director/inicio' });
    await waitFor(() => expect(container.querySelector('[aria-busy="true"]')).toBeNull());
    expect(await axe(container)).toHaveNoViolations();
  });

  it('studentCalendarHasNoAxeViolations', async () => {
    window.matchMedia = () => ({ matches: true, addEventListener() {}, removeEventListener() {} });
    server.use(calendarHandler());
    const { container } = renderWithProviders(<AlumnoCalendario />);
    expect((await screen.findAllByRole('button', { name: /Clase: / })).length).toBeGreaterThan(0);
    expect(await axe(container)).toHaveNoViolations();
  });

  it('auditLogHasNoAxeViolations', async () => {
    server.use(
      http.get(`${API}/audit-log`, () =>
        HttpResponse.json({
          items: [
            {
              id: 1,
              action: 'STUDENT_WITHDRAWN',
              actorPersonId: 7,
              actorName: 'Marcela Quiroga',
              actorRole: 'DIRECTOR',
              entityType: 'STUDENT',
              entityId: 1307,
              beforeValue: '{"status":"ACTIVE"}',
              afterValue: '{"status":"WITHDRAWN"}',
              reason: 'Baja voluntaria',
              occurredAt: '2026-11-17T15:42:00',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
        }),
      ),
    );
    const { container } = renderWithProviders(<AuditLogPage endpoint="/audit-log" deniedAudience="Dirección" />);
    expect(await screen.findByText('Baja voluntaria')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });
});
