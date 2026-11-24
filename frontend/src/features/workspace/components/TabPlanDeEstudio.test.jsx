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
    prerequisites: [],
    currentEnrollmentId: null,
    corequisites: [],
    approvalSource: 'COURSEWORK',
    equivalence: null,
    prerequisitesWaived: false,
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
      approvedByEquivalence: 0,
      creditsByEquivalence: 0,
      averageScore: null,
      accumulatedCreditsByCoursework: 3,
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

  it('keeps only approved subjects when the URL asks for approved ones', async () => {
    useProgress(progress([subject(), subject({ programSubjectId: 2, subjectName: 'Análisis', state: 'AVAILABLE', finalScore: null })]));
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?estado=aprobadas' });

    expect(await screen.findByText('Matemática I')).toBeInTheDocument();
    expect(screen.queryByText('Análisis')).not.toBeInTheDocument();
  });

  it('offers the available filter and keeps only available subjects', async () => {
    useProgress(progress([subject(), subject({ programSubjectId: 2, subjectName: 'Análisis', state: 'AVAILABLE', finalScore: null })]));
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?estado=disponibles' });

    expect(await screen.findByText('Análisis')).toBeInTheDocument();
    expect(screen.queryByText('Matemática I')).not.toBeInTheDocument();
  });

  it('falls back to all subjects when the status in the URL is unknown', async () => {
    useProgress(progress([subject(), subject({ programSubjectId: 2, subjectName: 'Análisis', state: 'AVAILABLE', finalScore: null })]));
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?estado=inventado' });

    expect(await screen.findByText('Análisis')).toBeInTheDocument();
    expect(screen.getByText('Matemática I')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Filtrar por estado' })).toHaveTextContent('Todas');
  });

  it('shows a dash instead of 0 / 0 credits when the plan defines no credits', async () => {
    const base = progress([subject({ credits: 0, finalScore: null, state: 'AVAILABLE' })]);
    useProgress({ ...base, summary: { ...base.summary, requiredCredits: 0, accumulatedCredits: 0, completionPercent: null } });
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    const row = (await screen.findByText('Matemática I')).closest('tr');
    expect(within(row).queryByText('0 / 0')).not.toBeInTheDocument();
    expect(within(row).getAllByText('—').length).toBeGreaterThan(0);
  });

  it('does not retry when the plan progress is forbidden', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/me/program-enrollments/5/progress/subjects`, () => {
        calls += 1;
        return HttpResponse.json({ code: 'FORBIDDEN', requestId: 'req-1', retryable: false, violations: [] }, { status: 403 });
      }),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    await screen.findByRole('alert');
    expect(calls).toBe(1);
  });
});

describe('TabPlanDeEstudio map view', () => {
  const threeYearPlan = () =>
    progress([
      subject({ programSubjectId: 1, subjectName: 'Matemática I', finalScore: 8.5 }),
      subject({ programSubjectId: 2, subjectCode: 'ANA201', subjectName: 'Análisis', curricularYear: 2, state: 'IN_PROGRESS', finalScore: null }),
      subject({ programSubjectId: 3, subjectCode: 'FIS301', subjectName: 'Física', curricularYear: 3, curricularTerm: 'ANNUAL', state: 'AVAILABLE', finalScore: null }),
      subject({ programSubjectId: 4, subjectCode: 'ELE001', subjectName: 'Taller', curricularYear: null, category: 'ELECTIVE', state: 'BLOCKED', finalScore: null }),
    ]);

  it('renders one column per year with the state as text and electives at the end', async () => {
    useProgress(threeYearPlan());
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    const region = await screen.findByRole('region', { name: 'Mapa del plan por año' });
    const headings = [...region.querySelectorAll('h2'), ...screen.getAllByRole('heading', { name: 'Electivas' })];
    expect(headings.map((heading) => heading.textContent)).toEqual(['1° año', '2° año', '3° año', 'Electivas']);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    const approved = screen.getByRole('button', { name: 'Matemática I, Aprobada, nota 8,5' });
    expect(within(approved).getByText('Aprobada')).toBeInTheDocument();
    expect(within(approved).getByText('8,5')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Física, Disponible' })).toHaveTextContent('Anual');
    expect(screen.getByRole('button', { name: 'Física, Disponible' })).toHaveTextContent('Podés inscribirte cuando abra la inscripción');
  });

  it('marks equivalence approvals with the chip and Eq. instead of the score', async () => {
    useProgress(
      progress([
        subject({ approvalSource: 'EQUIVALENCE', finalScore: null }),
        subject({ programSubjectId: 2, subjectCode: 'ANA201', subjectName: 'Análisis' }),
      ]),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    const equivalent = await screen.findByRole('button', { name: 'Matemática I, Aprobada por equivalencia, sin nota' });
    expect(within(equivalent).getByText('Equivalencia')).toBeInTheDocument();
    expect(within(equivalent).getByText('Eq.')).toBeInTheDocument();
    const regular = screen.getByRole('button', { name: 'Análisis, Aprobada, nota 8' });
    expect(within(regular).queryByText('Equivalencia')).not.toBeInTheDocument();
  });

  it('scrolls horizontally inside the region only from four years', async () => {
    useProgress(
      progress([1, 2, 3].map((year) => subject({ programSubjectId: year, subjectCode: `M${year}`, subjectName: `Materia ${year}`, curricularYear: year }))),
    );
    const { unmount } = renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });
    expect(await screen.findByRole('region', { name: 'Mapa del plan por año' })).not.toHaveAttribute('tabindex');
    unmount();

    useProgress(
      progress([1, 2, 3, 4].map((year) => subject({ programSubjectId: year, subjectCode: `M${year}`, subjectName: `Materia ${year}`, curricularYear: year }))),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });
    expect(await screen.findByRole('region', { name: 'Mapa del plan por año' })).toHaveAttribute('tabindex', '0');
  });

  it('toggles card selection with aria-pressed', async () => {
    useProgress(threeYearPlan());
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    const card = await screen.findByRole('button', { name: 'Matemática I, Aprobada, nota 8,5' });
    expect(card).toHaveAttribute('aria-pressed', 'false');
    expect(card).toHaveAttribute('aria-haspopup', 'dialog');
    await user.click(card);
    expect(card).toHaveAttribute('aria-pressed', 'true');
    await user.click(card);
    expect(card).toHaveAttribute('aria-pressed', 'false');
  });

  it('shares the estado filter between map chips and the list select', async () => {
    useProgress(threeYearPlan());
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    await user.click(await screen.findByRole('button', { name: /Aprobadas/ }));
    expect(screen.getByRole('button', { name: /Aprobadas/ })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('button', { name: /^Análisis/ })).not.toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'Lista' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.getByText('Matemática I')).toBeInTheDocument();
    expect(screen.queryByText('Análisis')).not.toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Filtrar por estado' })).toHaveTextContent('Aprobadas');
  });

  it('switches back to the list and keeps the legend only there', async () => {
    useProgress(threeYearPlan());
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    await screen.findByRole('button', { name: 'Matemática I, Aprobada, nota 8,5' });
    expect(screen.queryByRole('list', { name: 'Referencia de estados' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('radio', { name: 'Lista' }));
    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Referencia de estados' })).toBeInTheDocument();
  });

  it('shows the empty filter state and clears it', async () => {
    useProgress(threeYearPlan());
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa&estado=desaprobadas' });

    expect(await screen.findByText('Ninguna materia coincide')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Quitar filtro' }));
    expect(await screen.findByRole('button', { name: 'Matemática I, Aprobada, nota 8,5' })).toBeInTheDocument();
  });

  it('shows the empty plan message', async () => {
    useProgress(progress([]));
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    expect(await screen.findByText('El plan todavía no tiene materias')).toBeInTheDocument();
  });

  it('shows a skeleton while loading', async () => {
    server.use(http.get(`${API}/me/program-enrollments/5/progress/subjects`, () => new Promise(() => {})));
    const { container } = renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    expect(container.querySelectorAll('.skeleton-shimmer').length).toBeGreaterThan(0);
    expect(screen.queryByRole('region', { name: 'Mapa del plan por año' })).not.toBeInTheDocument();
  });

  it('offers retry on error and explains a forbidden response', async () => {
    useProgress({ code: 'INTERNAL_ERROR', requestId: 'req-1', retryable: false, violations: [] }, 500);
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el plan de estudio');
    useProgress(threeYearPlan());
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByRole('button', { name: 'Matemática I, Aprobada, nota 8,5' })).toBeInTheDocument();
  });

  it('says there is no access on a forbidden response', async () => {
    useProgress({ code: 'FORBIDDEN', requestId: 'req-2', retryable: false, violations: [] }, 403);
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
  });
});
