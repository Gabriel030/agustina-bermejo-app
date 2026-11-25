import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fireEvent } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { restoreViewport, useCompactViewport } from '@/test/compactViewport';
import { errorBody } from '@/test/fixtures/errors';
import { activeSubjectEquivalences, outOfPlanSubjectEquivalence, voidedSubjectEquivalence } from '@/test/fixtures/subjectEquivalences';
import { EQUIVALENCE_ENROLLMENT_IDS } from '@/test/mocks/subjectEquivalenceHandlers';
import { EquivalenceActionsProvider } from './EquivalenceActionsProvider';
import { EquivalenceMobileBar } from './EquivalenceMobileBar';
import { EquivalencesSection } from './EquivalencesSection';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const API = 'http://localhost:8080';
const ENROLLMENT = EQUIVALENCE_ENROLLMENT_IDS.mixed;
const THEMES = ['light', 'dark'];

function planSubject(overrides) {
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

const analysisPrerequisite = { subjectId: 20, subjectCode: 'ANA201', subjectName: 'Análisis', requirementType: 'APPROVED', standing: 'NOT_TAKEN', minGrade: null, approvedScore: null };

const progress = {
  programEnrollmentId: ENROLLMENT,
  programName: 'Tecnicatura de prueba',
  summary: {
    completionPercent: 25,
    accumulatedCredits: 3,
    requiredCredits: 12,
    remainingCredits: 9,
    mandatoryTotal: 4,
    mandatoryApproved: 1,
    counts: { approved: 1, inProgress: 0, regularPendingFinal: 0, failed: 0, available: 1, blocked: 1 },
    electiveGroups: [],
    approvedByEquivalence: 0,
    creditsByEquivalence: 0,
    averageScore: 8,
    accumulatedCreditsByCoursework: 3,
  },
  subjects: [
    planSubject(),
    planSubject({ programSubjectId: 2, subjectId: 20, subjectCode: 'ANA201', subjectName: 'Análisis', state: 'AVAILABLE', finalScore: null }),
    planSubject({
      programSubjectId: 3,
      subjectId: 30,
      subjectCode: 'FIS301',
      subjectName: 'Física',
      state: 'BLOCKED',
      finalScore: null,
      blockedBy: [analysisPrerequisite],
      prerequisites: [analysisPrerequisite],
    }),
  ],
  warnings: [],
};

const failure = (status, code) => HttpResponse.json(errorBody(code), { status });

beforeEach(() => {
  auth.roles = ['ADMINISTRATIVO'];
  server.use(
    http.get(`${API}/program-enrollments/:id/progress/subjects`, () => HttpResponse.json(progress)),
    http.get(`${API}/students/:id/documents`, () => HttpResponse.json([{ id: 51, type: 'ANALITICO_SECUNDARIO', uploadedAt: '2026-03-10T09:00:00' }])),
  );
});

afterEach(() => {
  document.documentElement.removeAttribute('data-theme');
  restoreViewport();
});

function renderSection(sectionProps = {}) {
  return renderWithProviders(
    <EquivalenceActionsProvider studentId={9} programEnrollmentId={ENROLLMENT}>
      <EquivalencesSection programEnrollmentId={ENROLLMENT} {...sectionProps} />
      <EquivalenceMobileBar programEnrollmentId={ENROLLMENT} />
    </EquivalenceActionsProvider>,
  );
}

function setTheme(theme) {
  document.documentElement.setAttribute('data-theme', theme);
}

async function openRecordDialog(user) {
  await user.click(await screen.findByRole('button', { name: 'Cargar equivalencia' }));
  return screen.findByRole('dialog', { name: 'Cargar equivalencia' });
}

async function fillValidForm(user) {
  await screen.findByRole('option', { name: 'Análisis (ANA201)' });
  await user.selectOptions(screen.getByLabelText('Materia del plan'), '2');
  await user.type(screen.getByLabelText('Institución de origen'), 'Universidad Nacional de Cuyo');
  await user.type(screen.getByLabelText('Materia de origen'), 'Análisis Matemático');
  await user.type(screen.getByLabelText(/^Nota de origen/), '6');
  fireEvent.change(screen.getByLabelText('Fecha de reconocimiento'), { target: { value: '2026-09-15' } });
  await user.type(screen.getByLabelText('Resolución'), 'RES-2026-200');
  await user.type(screen.getByLabelText('Motivo'), 'Contenidos y carga horaria equivalentes.');
}

describe('EquivalencesSection accessibility', () => {
  it.each(THEMES)('sectionWithAllStatusesHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    const { container } = renderSection();

    expect(await screen.findByRole('region', { name: 'Equivalencias' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it.each(THEMES)('emptySectionHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    server.use(http.get(`${API}/program-enrollments/:id/equivalences`, () => HttpResponse.json([])));
    const { container } = renderSection();

    expect(await screen.findByText('Sin equivalencias')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it.each(THEMES)('errorSectionHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    server.use(http.get(`${API}/program-enrollments/:id/equivalences`, () => failure(500, 'INTERNAL_ERROR')));
    const { container } = renderSection();

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it.each(THEMES)('forbiddenSectionHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    server.use(http.get(`${API}/program-enrollments/:id/equivalences`, () => failure(403, 'FORBIDDEN')));
    const { container } = renderSection();

    expect(await screen.findByText('No tenés acceso a esta pantalla')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it.each(THEMES)('readOnlySectionForPreceptorHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    auth.roles = ['PRECEPTOR'];
    const { container } = renderSection({ readOnlyNotice: true });

    expect(await screen.findByText('Solo lectura')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it.each(THEMES)('detailSheetHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    const user = userEvent.setup();
    renderSection();

    await user.click((await screen.findAllByRole('button', { name: /^Ver detalle de/ }))[0]);

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(await axe(document.body)).toHaveNoViolations();
  });
});

describe('EquivalenceRecordDialog accessibility', () => {
  it.each(THEMES)('stepOneHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    const user = userEvent.setup();
    renderSection();
    await openRecordDialog(user);

    expect(await screen.findByLabelText(/^Documento del legajo/)).toBeInTheDocument();
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it.each(THEMES)('stepOneWithValidationSummaryHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);

    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));

    expect(await within(dialog).findByRole('alert')).toBeInTheDocument();
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it.each(THEMES)('stepTwoComparisonHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillValidForm(user);

    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));

    expect(await within(dialog).findByRole('table')).toBeInTheDocument();
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it.each([
    ['empty', () => HttpResponse.json([])],
    ['forbidden', () => failure(403, 'FORBIDDEN')],
    ['failed', () => failure(500, 'INTERNAL_ERROR')],
  ])('documentPickerIn%sStateHasNoAxeViolations', async (_state, resolver) => {
    server.use(http.get(`${API}/students/:id/documents`, resolver));
    const user = userEvent.setup();
    renderSection();
    await openRecordDialog(user);

    await screen.findByLabelText(/^Documento del legajo/);
    await screen.findByText(/legajo|documentos/i, { selector: 'p, span, div' });
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it('comparisonTableKeepsFourFixedColumnsAndWrapsCellsSoItNeverOverflowsAt390Pixels', async () => {
    const user = userEvent.setup();
    renderSection();
    const dialog = await openRecordDialog(user);
    await fillValidForm(user);

    await user.click(within(dialog).getByRole('button', { name: 'Revisar' }));

    const table = await within(dialog).findByRole('table');
    expect(table).toHaveClass('table-fixed');
    expect(table.querySelectorAll('col')).toHaveLength(4);
    within(table).getAllByRole('cell').forEach((cell) => expect(cell).toHaveClass('whitespace-normal'));
  });
});

describe('EquivalenceVoidDialog accessibility', () => {
  it.each(THEMES)('voidDialogHasNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    const user = userEvent.setup();
    renderSection();
    await user.click((await screen.findAllByRole('button', { name: /^Ver detalle de/ }))[0]);
    await user.click(await screen.findByRole('button', { name: 'Anular equivalencia' }));

    expect(await screen.findByRole('dialog', { name: 'Anular equivalencia' })).toBeInTheDocument();
    expect(await axe(document.body)).toHaveNoViolations();
  });
});

describe('EquivalenceMobileBar accessibility', () => {
  it.each(THEMES)('stickyBarAndBottomSheetHaveNoAxeViolationsIn%sTheme', async (theme) => {
    setTheme(theme);
    useCompactViewport();
    const user = userEvent.setup();
    const { container } = renderSection();

    const bar = await screen.findByRole('region', { name: 'Acciones de equivalencias' });
    expect(await axe(container)).toHaveNoViolations();

    await user.click(within(bar).getByRole('button', { name: 'Cargar equivalencia' }));
    const sheet = await screen.findByRole('dialog', { name: 'Cargar equivalencia' });
    expect(sheet).toHaveAttribute('data-side', 'bottom');
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it('exposesFixturesCoveringActiveVoidedAndOutOfPlanRows', () => {
    expect(activeSubjectEquivalences().length).toBeGreaterThan(0);
    expect(voidedSubjectEquivalence().status).toBe('VOIDED');
    expect(outOfPlanSubjectEquivalence().inCurrentPlan).toBe(false);
  });
});
