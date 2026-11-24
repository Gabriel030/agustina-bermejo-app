import { describe, expect, it } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { axe } from '@/test/axe';
import { CurriculumMap } from './CurriculumMap';

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

const prerequisiteOfAnalysis = { subjectId: 10, subjectCode: 'MAT101', subjectName: 'Matemática I', requirementType: 'APPROVED', standing: 'NOT_TAKEN' };

const plan = [
  subject({ programSubjectId: 1, state: 'IN_PROGRESS', finalScore: null, currentEnrollmentId: 77 }),
  subject({
    programSubjectId: 2,
    subjectId: 20,
    subjectCode: 'ANA201',
    subjectName: 'Análisis',
    curricularYear: 2,
    state: 'BLOCKED',
    finalScore: null,
    blockedBy: [prerequisiteOfAnalysis],
    prerequisites: [prerequisiteOfAnalysis],
  }),
  subject({ programSubjectId: 3, subjectId: 30, subjectCode: 'FIS301', subjectName: 'Física', curricularYear: 3, state: 'AVAILABLE', finalScore: null }),
  subject({
    programSubjectId: 5,
    subjectId: 50,
    subjectCode: 'QUI101',
    subjectName: 'Química',
    state: 'APPROVED',
    approvalSource: 'EQUIVALENCE',
    finalScore: null,
    equivalence: {
      id: 9,
      originInstitution: 'Universidad Nacional',
      originSubjectName: 'Química General',
      recognizedOn: '2024-03-15',
      resolutionNumber: 'RES-123',
      reason: 'motivo interno',
    },
  }),
];

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="search">{location.search}</div>;
}

function renderMap(route = '/') {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <Routes>
        <Route
          path="*"
          element={
            <>
              <CurriculumMap subjects={plan} statusFilter="todas" onStatusFilterChange={() => {}} />
              <LocationProbe />
            </>
          }
        />
      </Routes>
    </MemoryRouter>,
  );
}

const card = (name) => screen.getByRole('button', { name: new RegExp(`^${name}`) });

describe('CurriculumMap highlight and detail', () => {
  it('opens the detail and highlights related subjects with a text cue when selecting a card', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Análisis'));

    expect(await screen.findByRole('dialog', { name: 'Análisis' })).toBeInTheDocument();
    expect(screen.getByTestId('search')).toHaveTextContent('?materia=2');
    await user.keyboard('{Escape}');
    expect(card('Matemática I')).toHaveAccessibleName(/correlativa$/);
    expect(card('Análisis')).toHaveAttribute('aria-pressed', 'true');
    expect(card('Física')).not.toHaveAccessibleName(/correlativa|desbloquea/);
    expect(card('Física').className).not.toMatch(/opacity/);
  });

  it('marks dependents as unlocked by the selected subject', async () => {
    const user = userEvent.setup();
    renderMap('/?materia=1');
    expect(card('Análisis')).toHaveAccessibleName(/desbloquea$/);
    await user.click(card('Matemática I'));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
  });

  it('keeps the highlight after closing the panel and offers the status strip', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Análisis'));
    await user.click(await screen.findByRole('button', { name: 'Cerrar' }));

    const strip = await screen.findByRole('status');
    expect(strip).toHaveTextContent('Resaltando correlativas de Análisis');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(card('Matemática I')).toHaveAccessibleName(/correlativa$/);
    await user.click(within(strip).getByRole('button', { name: 'Ver detalle' }));
    expect(await screen.findByRole('dialog', { name: 'Análisis' })).toBeInTheDocument();
  });

  it('closes the panel first on Escape and clears the highlight on the second one', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Análisis'));
    await screen.findByRole('dialog');

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(card('Análisis')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(card('Análisis')).toHaveFocus();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(card('Análisis')).toHaveAttribute('aria-pressed', 'false');
    expect(card('Matemática I')).not.toHaveAccessibleName(/correlativa/);
    expect(screen.getByTestId('search')).toHaveTextContent(/^$/);
    expect(card('Análisis')).toHaveFocus();
  });

  it('clears selection and returns focus to the card with Quitar', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Física'));
    await user.keyboard('{Escape}');
    await user.click(await screen.findByRole('button', { name: 'Quitar' }));

    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(card('Física')).toHaveFocus();
    expect(card('Física')).toHaveAttribute('aria-pressed', 'false');
  });

  it('restores selection and highlight with the panel closed from the URL', async () => {
    renderMap('/?materia=2');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(card('Análisis')).toHaveAttribute('aria-pressed', 'true');
    expect(card('Matemática I')).toHaveAccessibleName(/correlativa$/);
    expect(screen.getByRole('status')).toHaveTextContent('Resaltando correlativas de Análisis');
  });

  it('ignores an unknown subject in the URL', () => {
    renderMap('/?materia=999');
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('returns focus to the card that opened the detail when closing it', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Física'));
    await user.click(await screen.findByRole('button', { name: 'Cerrar' }));
    expect(card('Física')).toHaveFocus();
  });

  it('declares every card as a dialog opener', () => {
    renderMap();
    expect(card('Física')).toHaveAttribute('aria-haspopup', 'dialog');
  });
});

describe('SubjectDetailSheet content', () => {
  it('lists what is missing with the action and what the subject unlocks', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Análisis'));
    const dialog = await screen.findByRole('dialog', { name: 'Análisis' });
    expect(within(dialog).getByRole('heading', { name: 'Qué te falta' })).toBeInTheDocument();
    expect(within(dialog).getByText(/^Aprobar Matemática I \(MAT101\)/)).toBeInTheDocument();
    expect(within(dialog).getByText('No es correlativa de otras materias.')).toBeInTheDocument();
  });

  it('lists the dependents inverted from the prerequisites and links to the current course', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Matemática I'));
    const dialog = await screen.findByRole('dialog', { name: 'Matemática I' });
    expect(within(dialog).getByRole('heading', { name: 'Qué desbloquea' })).toBeInTheDocument();
    expect(within(dialog).getByText('Análisis')).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Ver mi cursada' })).toHaveAttribute('href', '/alumno/mis-carreras/materias/77');
  });

  it('explains availability and offers no course link without an enrollment', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Física'));
    const dialog = await screen.findByRole('dialog', { name: 'Física' });
    expect(within(dialog).getByRole('heading', { name: 'Disponible' })).toBeInTheDocument();
    expect(within(dialog).getByText('Podés inscribirte cuando abra la inscripción')).toBeInTheDocument();
    expect(within(dialog).queryByRole('link')).not.toBeInTheDocument();
  });

  it('shows the equivalence origin and never the internal reason', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Química'));
    const dialog = await screen.findByRole('dialog', { name: 'Química' });
    expect(within(dialog).getByText('Universidad Nacional')).toBeInTheDocument();
    expect(within(dialog).getByText('Química General')).toBeInTheDocument();
    expect(within(dialog).getByText('15/03/2024')).toBeInTheDocument();
    expect(within(dialog).getByText('RES-123')).toBeInTheDocument();
    expect(within(dialog).queryByText(/motivo interno/)).not.toBeInTheDocument();
  });
});

describe('SubjectDetailSheet accessibility', () => {
  it('has no axe violations with the panel open', async () => {
    const user = userEvent.setup();
    renderMap();
    await user.click(card('Análisis'));
    await screen.findByRole('dialog');
    expect(await axe(document.body)).toHaveNoViolations();
  });
});
