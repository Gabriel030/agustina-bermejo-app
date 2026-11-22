import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { curriculumProgressHandlers } from '@/test/mocks/handlers';
import { axe } from '@/test/axe';
import { installFixedClock } from '@/test/fixedClock';
import { renderWithProviders } from '@/test/renderWithProviders';
import { renderWithAppCache } from '@/test/renderWithAppCache';
import { contractList, contractObject } from '@/test/fixtures/fromContract';
import { calendarHandler } from '@/test/fixtures/studentCalendar';
import { AlumnoInicio } from '@/features/workspace/pages/AlumnoInicio';
import { AlumnoMisCarreras } from '@/features/workspace/pages/AlumnoMisCarreras';
import { AlumnoMateriaDetalle } from '@/features/workspace/pages/AlumnoMateriaDetalle';
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

  it('studentProgramsHasNoAxeViolations', async () => {
    server.use(
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          { id: 5, studentId: 1, programId: 2, programName: 'Tecnicatura en Desarrollo de Software', status: 'ACTIVE', enrollmentDate: '2024-03-01T10:00:00' },
        ]),
      ),
      http.get(`${API}/me/program-enrollments/5/progress`, () =>
        HttpResponse.json(
          contractObject('/me/program-enrollments/{id}/progress', { programEnrollmentId: 5, creditosAcumulados: 20, creditosRequeridos: 60, obligatoriasPendientes: [], gruposElectivos: [], hasActiveCoursework: true }),
        ),
      ),
      http.get(`${API}/me/regular-certificates/eligibility`, () =>
        HttpResponse.json({ kind: 'PROGRAM', enrollmentId: 5, regular: true, reasons: [], attendanceControlled: true, billingMode: 'FULL', institutionallyRegular: true, paymentStanding: 'VERIFIED' }),
      ),
      ...curriculumProgressHandlers,
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const { container } = renderWithProviders(<AlumnoMisCarreras />);
    expect(await screen.findByText('Con cursada activa')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('studentSubjectDetailHasNoAxeViolations', async () => {
    server.use(
      http.get(`${API}/me/section-enrollments`, () =>
        HttpResponse.json(
          contractList('/me/section-enrollments', [
            { id: 7, sectionName: 'Comisión B', subjectName: 'Bases de Datos', status: 'IN_PROGRESS', instructorTitular: 'Marcos Giménez', schedule: 'Lun 19:00', curricularYear: 2, subjectCode: 'BD-204' },
          ]),
        ),
      ),
      http.get(`${API}/me/section-enrollments/7/attendance/risk`, () =>
        HttpResponse.json({ absencePercent: 0, calculable: false, libre: false, maxAbsencePercent: 25 }),
      ),
      ...curriculumProgressHandlers,
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const { container } = renderWithProviders(
      <Routes>
        <Route path="/alumno/materias/:id" element={<AlumnoMateriaDetalle />} />
      </Routes>,
      { route: '/alumno/materias/7' },
    );
    expect(await screen.findByRole('heading', { name: 'Bases de Datos' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });
});
