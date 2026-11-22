import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { curriculumProgressHandlers } from '@/test/mocks/handlers';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoMisCarreras } from '@/features/workspace/pages/AlumnoMisCarreras';

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

describe('AlumnoMisCarreras', () => {
  it('keeps showing a graduated program with its finished state instead of the "not enrolled" empty state', async () => {
    server.use(
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          {
            id: 5,
            studentId: 1,
            programId: 2,
            programName: 'Profesorado de Educación Inicial',
            status: 'COMPLETED',
            enrollmentDate: '2022-03-01T10:00:00',
            completionDate: '2026-10-03T03:54:00',
          },
        ]),
      ),
      http.get(`${API}/me/program-enrollments/5/progress`, () =>
        HttpResponse.json({
          programEnrollmentId: 5,
          creditosAcumulados: 144,
          creditosRequeridos: 144,
          obligatoriasPendientes: [],
          gruposElectivos: [],
          elegibleParaCompletar: true,
          esRegular: false,
          promedioGeneral: 8.1,
        }),
      ),
      http.get(`${API}/me/regular-certificates/eligibility`, () => HttpResponse.json(REGULAR_STUDENT)),
      http.get(`${API}/me/modules`, () => HttpResponse.json(MODULES_FULL)),
      ...curriculumProgressHandlers,
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );

    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByText('Carrera finalizada')).toBeInTheDocument();
    expect(screen.getAllByText(/Profesorado de Educación Inicial/).length).toBeGreaterThan(0);
    expect(screen.queryByText('Todavía no estás matriculado en ninguna carrera')).not.toBeInTheDocument();
  });

  function mockEnrollment(esRegular) {
    server.use(
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          {
            id: 5,
            studentId: 1,
            programId: 2,
            programName: 'Profesorado de Educación Inicial',
            status: 'ACTIVE',
            enrollmentDate: '2022-03-01T10:00:00',
            completionDate: '2026-10-03T03:54:00',
          },
        ]),
      ),
      http.get(`${API}/me/program-enrollments/5/progress`, () =>
        HttpResponse.json({
          programEnrollmentId: 5,
          creditosAcumulados: 40,
          creditosRequeridos: 144,
          obligatoriasPendientes: [],
          gruposElectivos: [],
          elegibleParaCompletar: false,
          esRegular,
          hasActiveCoursework: esRegular,
          promedioGeneral: 8.1,
        }),
      ),
      http.get(`${API}/me/regular-certificates/eligibility`, () => HttpResponse.json(REGULAR_STUDENT)),
      http.get(`${API}/me/modules`, () => HttpResponse.json(MODULES_FULL)),
      ...curriculumProgressHandlers,
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
  }

  it('labels a program with active coursework as such', async () => {
    mockEnrollment(true);
    renderWithProviders(<AlumnoMisCarreras />);
    expect(await screen.findByText('Con cursada activa')).toBeInTheDocument();
    expect(screen.queryByText('Sin cursada activa')).not.toBeInTheDocument();
  });

  it('labels a program without active coursework without claiming the student is not attending', async () => {
    mockEnrollment(false);
    renderWithProviders(<AlumnoMisCarreras />);
    expect(await screen.findByText('Sin cursada activa')).toBeInTheDocument();
    expect(screen.queryByText('No estás cursando')).not.toBeInTheDocument();
  });

  it('does not show an invented weekly hours column in the study plan', async () => {
    mockEnrollment(true);
    renderWithProviders(<AlumnoMisCarreras />, { route: '/?tab=plan-de-estudio' });
    expect(await screen.findByRole('columnheader', { name: 'Créditos' })).toBeInTheDocument();
    expect(screen.queryByText('Hs. semanales')).not.toBeInTheDocument();
  });

  it('offers only the approved-subjects download and no legacy regular certificate button', async () => {
    mockEnrollment(false);
    const requested = [];
    server.use(
      http.get(`${API}/me/program-enrollments/5/graduation-request`, () =>
        HttpResponse.json({
          programEnrollmentId: 5,
          programName: 'Profesorado de Educación Inicial',
          graduated: false,
          eligible: false,
          accumulatedCredits: 10,
          requiredCredits: 144,
          pendingMandatorySubjects: [],
          unfulfilledElectiveGroups: [],
          request: null,
        }),
      ),
      http.get(`${API}/me/regular-certificates`, () =>
        HttpResponse.json({
          kind: 'PROGRAM',
          enrollmentId: 5,
          state: 'AVAILABLE',
          staffConfirmationRequired: true,
          certificate: null,
          pendingRequest: null,
          unavailableType: null,
    rejectionReason: null,
          reasons: [],
          history: [],
        }),
      ),
      http.get(`${API}/me/program-enrollments/5/certificates/:kind`, ({ params }) => {
        requested.push(params.kind);
        return new HttpResponse(new Uint8Array([37, 80, 68, 70]), {
          headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment; filename="constancia.pdf"' },
        });
      }),
    );
    URL.createObjectURL = vi.fn(() => 'blob:certificate');
    URL.revokeObjectURL = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<AlumnoMisCarreras />, { route: '/alumno/mis-carreras?tab=tramites' });

    await user.click(await screen.findByRole('button', { name: 'Descargar' }));

    await waitFor(() => expect(requested).toEqual(['approved-subjects']));
    expect(screen.queryByRole('button', { name: 'Constancia de alumno regular' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Constancia de materias aprobadas' })).not.toBeInTheDocument();
  });

  it('shows six tabs with Trámites in place of Egreso', async () => {
    mockEnrollment(false);
    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByRole('tab', { name: 'Trámites' })).toBeInTheDocument();
    expect(within(screen.getAllByRole('tablist')[0]).getAllByRole('tab')).toHaveLength(6);
    expect(screen.queryByRole('tab', { name: 'Egreso' })).not.toBeInTheDocument();
  });
});

describe('AlumnoMisCarreras — load failure', () => {
  it('shows the error state and reloads the programs on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/program-enrollments`, () => {
        calls += 1;
        return new HttpResponse(null, { status: 500 });
      }),
      http.get(`${API}/me/regular-certificates/eligibility`, () => HttpResponse.json(REGULAR_STUDENT)),
      http.get(`${API}/me/modules`, () => HttpResponse.json(MODULES_FULL)),
      ...curriculumProgressHandlers,
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar tus carreras');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(calls).toBe(2));
  });
});

describe('AlumnoMisCarreras — no permission', () => {
  it('shows the access denied state without a retry button on 403', async () => {
    server.use(
      http.get(`${API}/me/program-enrollments`, () => new HttpResponse(null, { status: 403 })),
      http.get(`${API}/me/modules`, () => HttpResponse.json(MODULES_FULL)),
      ...curriculumProgressHandlers,
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No tenés acceso a esta pantalla');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });
});

describe('AlumnoMisCarreras — regularity', () => {
  function mockRegularity(regularity) {
    const requested = [];
    server.use(
      http.get(`${API}/me/regular-certificates/eligibility`, ({ request }) => {
        requested.push(new URL(request.url).searchParams.get('enrollmentId'));
        return HttpResponse.json({
          kind: 'PROGRAM',
          enrollmentId: 5,
          regular: true,
          reasons: [],
          attendanceControlled: true,
          billingMode: 'FULL',
          institutionallyRegular: true,
          paymentStanding: 'VERIFIED',
          ...regularity,
        });
      }),
      http.get(`${API}/me/program-enrollments`, () =>
        HttpResponse.json([
          {
            id: 5,
            studentId: 1,
            programId: 2,
            programName: 'Profesorado de Educación Inicial',
            status: 'ACTIVE',
            enrollmentDate: '2022-03-01T10:00:00',
            completionDate: null,
          },
        ]),
      ),
      http.get(`${API}/me/program-enrollments/5/progress`, () =>
        HttpResponse.json({
          programEnrollmentId: 5,
          creditosAcumulados: 40,
          creditosRequeridos: 144,
          obligatoriasPendientes: [],
          gruposElectivos: [],
          elegibleParaCompletar: false,
          esRegular: true,
          hasActiveCoursework: true,
          promedioGeneral: 8.1,
        }),
      ),
      http.get(`${API}/me/regular-certificates/eligibility`, () => HttpResponse.json(REGULAR_STUDENT)),
      http.get(`${API}/me/modules`, () => HttpResponse.json(MODULES_FULL)),
      ...curriculumProgressHandlers,
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    return requested;
  }

  it('shows the student as regular once, with the payment reason', async () => {
    const requested = mockRegularity({});
    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByText('Alumno regular')).toBeInTheDocument();
    expect(screen.getAllByText('Alumno regular')).toHaveLength(1);
    expect(screen.getByText('Pagos al día')).toBeInTheDocument();
    expect(requested).toEqual(['5']);
  });

  it('marks the regularity as to be confirmed when payments are pending review', async () => {
    mockRegularity({ paymentStanding: 'TO_CONFIRM' });
    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByText('Alumno regular · a confirmar')).toBeInTheDocument();
    expect(screen.getByText('Pagos a confirmar')).toBeInTheDocument();
  });

  it('explains why the student is not regular', async () => {
    mockRegularity({
      institutionallyRegular: false,
      regular: false,
      reasons: ['FREE_IN_ACTIVE_SECTION'],
      paymentStanding: 'OVERDUE',
    });
    renderWithProviders(<AlumnoMisCarreras />);

    expect(await screen.findByText('No es alumno regular')).toBeInTheDocument();
    expect(screen.getByText('Libre por inasistencias en una cursada activa · Pagos vencidos')).toBeInTheDocument();
  });

  describe('progress cards', () => {
    function enrollment(id, status, programName) {
      return { id, studentId: 1, programId: id, programName, status, enrollmentDate: '2022-03-01T10:00:00', completionDate: null };
    }

    function curriculumFor(id, accumulatedCredits, requiredCredits, approved) {
      return {
        programEnrollmentId: id,
        programName: `Carrera ${id}`,
        subjects: [],
        warnings: [],
        summary: {
          completionPercent: 25,
          accumulatedCredits,
          requiredCredits,
          remainingCredits: requiredCredits - accumulatedCredits,
          mandatoryTotal: 4,
          mandatoryApproved: approved,
          counts: { approved, inProgress: 0, regularPendingFinal: 0, failed: 0, available: 0, blocked: 0 },
          electiveGroups: [],
        },
      };
    }

    function mockEnrollments(list) {
      server.use(
        http.get(`${API}/me/program-enrollments`, () => HttpResponse.json(list)),
        http.get(`${API}/me/program-enrollments/:id/progress`, ({ params }) =>
          HttpResponse.json({ programEnrollmentId: Number(params.id), creditosAcumulados: 0, creditosRequeridos: 0, obligatoriasPendientes: [], gruposElectivos: [], elegibleParaCompletar: false, esRegular: true, hasActiveCoursework: true, promedioGeneral: 8 }),
        ),
        http.get(`${API}/me/program-enrollments/:id/progress/subjects`, ({ params }) =>
          HttpResponse.json(curriculumFor(Number(params.id), Number(params.id) * 10, 100, Number(params.id))),
        ),
        http.get(`${API}/me/regular-certificates/eligibility`, () => HttpResponse.json(REGULAR_STUDENT)),
        http.get(`${API}/me/modules`, () => HttpResponse.json(MODULES_FULL)),
        ...curriculumProgressHandlers,
        http.get(`${API}/*`, () => HttpResponse.json([])),
      );
    }

    it('skips a withdrawn program and shows the cards of the first active one', async () => {
      mockEnrollments([enrollment(3, 'WITHDRAWN', 'Carrera retirada'), enrollment(4, 'ACTIVE', 'Carrera activa')]);
      renderWithProviders(<AlumnoMisCarreras />);

      expect(await screen.findByText('40')).toBeInTheDocument();
      expect(screen.queryByText('30')).not.toBeInTheDocument();
    });

    it('shows the cards of the program chosen by id when there are several', async () => {
      mockEnrollments([enrollment(1, 'ACTIVE', 'Primera'), enrollment(2, 'ACTIVE', 'Segunda')]);
      renderWithProviders(<AlumnoMisCarreras />, { route: '/?carrera=2' });

      expect(await screen.findByText('20')).toBeInTheDocument();
      expect(screen.queryByText('10')).not.toBeInTheDocument();
    });
  });
});
