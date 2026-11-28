import { afterEach, describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { axe } from '@/test/axe';
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

const algebra = { subjectId: 10, subjectCode: 'MAT101', subjectName: 'Matemática I', requirementType: 'APPROVED', standing: 'NOT_TAKEN', minGrade: null, approvedScore: null };

const subjects = [
  subject(),
  subject({
    programSubjectId: 2,
    subjectId: 20,
    subjectCode: 'ANA201',
    subjectName: 'Análisis',
    curricularYear: 2,
    state: 'BLOCKED',
    finalScore: null,
    blockedBy: [algebra],
    prerequisites: [algebra],
  }),
  subject({ programSubjectId: 3, subjectId: 30, subjectCode: 'FIS301', subjectName: 'Física', curricularYear: 3, state: 'AVAILABLE', finalScore: null }),
  subject({
    programSubjectId: 5,
    subjectId: 50,
    subjectCode: 'QUI101',
    subjectName: 'Química',
    approvalSource: 'EQUIVALENCE',
    finalScore: null,
    equivalence: {
      id: 9,
      originInstitution: 'Universidad Nacional',
      originSubjectName: 'Química General',
      recognizedOn: '2024-03-15',
      resolutionNumber: 'RES-123',
      originScore: null,
    },
  }),
  subject({
    programSubjectId: 6,
    subjectId: 60,
    subjectCode: 'BIO101',
    subjectName: 'Biología',
    approvalSource: 'EQUIVALENCE',
    finalScore: 7,
    equivalence: {
      id: 10,
      originInstitution: 'Instituto Superior',
      originSubjectName: 'Biología General',
      recognizedOn: '2024-03-16',
      resolutionNumber: 'RES-124',
      originScore: null,
    },
  }),
];

function progressBody(items, summaryOverrides = {}) {
  return {
    programEnrollmentId: 5,
    programName: 'Tecnicatura',
    subjects: items,
    summary: {
      completionPercent: 50,
      accumulatedCredits: 3,
      requiredCredits: 6,
      remainingCredits: 3,
      mandatoryTotal: 5,
      mandatoryApproved: 3,
      counts: { approved: 3, inProgress: 0, regularPendingFinal: 0, failed: 0, available: 1, blocked: 1 },
      electiveGroups: [],
      approvedByEquivalence: 2,
      creditsByEquivalence: 6,
      averageScore: null,
      accumulatedCreditsByCoursework: 3,
      ...summaryOverrides,
    },
    warnings: [],
  };
}

function useProgress(items = subjects) {
  server.use(http.get(`${API}/me/program-enrollments/5/progress/subjects`, () => HttpResponse.json(progressBody(items))));
}

const THEMES = ['light', 'dark'];

afterEach(() => {
  document.documentElement.removeAttribute('data-theme');
});

describe('TabPlanDeEstudio accessibility', () => {
  it.each(THEMES)('mapViewHasNoAxeViolationsIn%sTheme', async (theme) => {
    document.documentElement.setAttribute('data-theme', theme);
    useProgress();
    const { container } = renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    expect(await screen.findByRole('button', { name: /^Análisis/ })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it.each(THEMES)('listViewHasNoAxeViolationsIn%sTheme', async (theme) => {
    document.documentElement.setAttribute('data-theme', theme);
    useProgress();
    const { container } = renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    expect(await screen.findByRole('table', { name: 'Plan de estudio por año' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it.each(THEMES)('mapWithOpenDetailPanelHasNoAxeViolationsIn%sTheme', async (theme) => {
    document.documentElement.setAttribute('data-theme', theme);
    useProgress();
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    await user.click(await screen.findByRole('button', { name: /^Análisis/ }));

    expect(await screen.findByRole('dialog', { name: 'Análisis' })).toBeInTheDocument();
    expect(await axe(document.body)).toHaveNoViolations();
  });

  it('mapWithHighlightStripHasNoAxeViolations', async () => {
    useProgress();
    const user = userEvent.setup();
    const { container } = renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa&materia=2' });

    expect(await screen.findByRole('status')).toHaveTextContent('Resaltando correlativas de Análisis');
    await user.tab();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('detailPanelCloseButtonIsAtLeast44PixelsAndNamedCerrar', async () => {
    useProgress();
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    await user.click(await screen.findByRole('button', { name: /^Análisis/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Análisis' });
    const close = within(dialog).getByRole('button', { name: 'Cerrar' });

    expect(close).toHaveClass('size-11');
    expect(within(dialog).getAllByRole('button', { name: 'Cerrar' })).toHaveLength(1);
  });

  it('showsEqAcrossCardAndNeverTheInternalReasonOnAnySurface', async () => {
    useProgress();
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    const withoutScore = await screen.findByRole('button', { name: 'Química, Aprobada por equivalencia, sin nota' });
    const withScore = screen.getByRole('button', { name: 'Biología, Aprobada por equivalencia, nota 7' });
    expect(within(withoutScore).getByText('Eq.')).toBeInTheDocument();
    expect(within(withScore).queryByText('Eq.')).not.toBeInTheDocument();
    expect(within(withScore).getByText('7')).toBeInTheDocument();

    await user.click(withoutScore);
    const dialog = await screen.findByRole('dialog', { name: 'Química' });
    expect(within(dialog).getByText('Universidad Nacional')).toBeInTheDocument();
    expect(within(dialog).getByText('RES-123')).toBeInTheDocument();
    expect(document.body).not.toHaveTextContent(/motivo/i);
  });

  it('gridOfFiveYearsScrollsInsideItsOwnRegion', async () => {
    useProgress(
      [1, 2, 3, 4, 5].map((year) =>
        subject({ programSubjectId: year, subjectId: year * 10, subjectCode: `Y${year}`, subjectName: `Materia ${year}`, curricularYear: year }),
      ),
    );
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />, { route: '/?vista=mapa' });

    const region = await screen.findByRole('region', { name: 'Mapa del plan por año' });
    expect(region).toHaveAttribute('tabindex', '0');
    expect(region).toHaveClass('md:overflow-x-auto');
    expect(within(region).getAllByRole('heading', { level: 3 })).toHaveLength(5);
  });
});
