import { afterEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { TabPlanDeEstudio } from './TabPlanDeEstudio';

const API = 'http://localhost:8080';

function subject(overrides) {
  return {
    programSubjectId: 1,
    subjectId: 10,
    subjectCode: 'MAT101',
    subjectName: 'Matemática I',
    credits: 3,
    curricularYear: 1,
    curricularTerm: 'FIRST',
    category: 'MANDATORY',
    electiveGroupId: null,
    state: 'APPROVED',
    finalScore: 8,
    attempts: 1,
    blockedBy: [],
    corequisites: [],
    ...overrides,
  };
}

function progress(subjects, overrides = {}) {
  return {
    programEnrollmentId: 5,
    programName: 'Tecnicatura',
    subjects,
    summary: {
      completionPercent: 50,
      accumulatedCredits: 3,
      requiredCredits: 6,
      remainingCredits: 3,
      mandatoryTotal: 2,
      mandatoryApproved: 1,
      counts: { approved: 1, inProgress: 0, regularPendingFinal: 0, failed: 0, available: 0, blocked: 1 },
      electiveGroups: [],
    },
    warnings: [],
    ...overrides,
  };
}

function useProgress(body, status = 200) {
  server.use(http.get(`${API}/me/program-enrollments/5/progress/subjects`, () => HttpResponse.json(body, { status })));
}

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

describe('TabPlanDeEstudio', () => {
  it('groups subjects by year with earned over total credits and the status label', async () => {
    useProgress(
      progress([
        subject(),
        subject({ programSubjectId: 2, subjectCode: 'MAT201', subjectName: 'Análisis', credits: 4, curricularYear: 2, state: 'IN_PROGRESS', finalScore: null }),
      ]),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    await screen.findByText('Matemática I');
    const table = screen.getByRole('table', { name: 'Plan de estudio por año' });
    expect(within(table).getByText('1° año')).toBeInTheDocument();
    expect(within(table).getByText('2° año')).toBeInTheDocument();
    const approvedRow = within(table).getByText('Matemática I').closest('tr');
    expect(within(approvedRow).getByText('3 / 3')).toBeInTheDocument();
    expect(within(approvedRow).getByText('Aprobada')).toBeInTheDocument();
    const inProgressRow = within(table).getByText('Análisis').closest('tr');
    expect(within(inProgressRow).getByText('0 / 4')).toBeInTheDocument();
    expect(within(inProgressRow).getByText('En curso')).toBeInTheDocument();
  });

  it('explains the blocking prerequisite in a tooltip when hovering the info button', async () => {
    useProgress(
      progress([
        subject({
          programSubjectId: 2,
          subjectName: 'Matemática II',
          state: 'BLOCKED',
          finalScore: null,
          blockedBy: [{ subjectId: 12, subjectCode: 'MAT100', subjectName: 'Álgebra', requirementType: 'APPROVED', standing: 'NOT_TAKEN' }],
        }),
      ]),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    await user.hover(await screen.findByRole('button', { name: 'Por qué Matemática II está bloqueada' }));

    expect((await screen.findAllByText('Aprobar Álgebra (MAT100) · sin cursar')).length).toBeGreaterThan(0);
  });

  it('shows informative corequisites next to the status', async () => {
    useProgress(
      progress([
        subject({ state: 'AVAILABLE', finalScore: null, corequisites: [{ subjectId: 13, subjectCode: 'LAB1', subjectName: 'Laboratorio', state: 'IN_PROGRESS' }] }),
      ]),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    expect(await screen.findByText('Se cursa junto con Laboratorio (LAB1) · en curso')).toBeInTheDocument();
  });

  it('filters rows by status and offers to clear a filter that matches nothing', async () => {
    useProgress(progress([subject()]));
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?estado=bloqueadas' });

    expect(await screen.findByText('Ninguna materia coincide')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Quitar filtro' }));

    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
  });

  it('shows the plan warnings and the elective groups', async () => {
    useProgress(
      progress([subject()], {
        warnings: [{ code: 'PLAN_CHANGED_AFTER_ENROLLMENT', message: 'texto del servidor' }],
        summary: {
          ...progress([]).summary,
          electiveGroups: [{ electiveGroupId: 1, name: 'Optativas', minRequired: 2, maxAllowed: 3, approvedCount: 1, fulfilled: false }],
        },
      }),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    expect(await screen.findByText('El plan de estudios cambió desde tu inscripción.')).toBeInTheDocument();
    expect(screen.getByText('Optativas')).toBeInTheDocument();
    expect(screen.getByText('pendiente')).toBeInTheDocument();
  });

  it('shows an empty state when the plan has no subjects', async () => {
    useProgress(progress([]));
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    expect(await screen.findByText('El plan todavía no tiene materias')).toBeInTheDocument();
  });

  it('shows the error state and retries the load', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/program-enrollments/5/progress/subjects`, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json({ code: 'INTERNAL_ERROR', requestId: 'req-1', retryable: false, violations: [] }, { status: 500 }) : HttpResponse.json(progress([subject()]));
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el plan de estudio');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
  });

  it('shows each subject as a card with its blocking reason on narrow screens', async () => {
    window.matchMedia = () => ({ matches: true, addEventListener() {}, removeEventListener() {} });
    useProgress(
      progress([
        subject({
          subjectName: 'Matemática II',
          state: 'BLOCKED',
          finalScore: null,
          blockedBy: [{ subjectId: 12, subjectCode: 'MAT100', subjectName: 'Álgebra', requirementType: 'APPROVED', standing: 'REGULAR' }],
        }),
      ]),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    const card = await screen.findByRole('group', { name: 'Matemática II, Bloqueada' });
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(within(card).getByText('Aprobar Álgebra (MAT100) · cursada regular')).toBeInTheDocument();
  });
});
