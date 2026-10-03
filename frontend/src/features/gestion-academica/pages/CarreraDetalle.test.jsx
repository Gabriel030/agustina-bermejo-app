import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraDetalle } from './CarreraDetalle';

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

const PROGRAM = { id: 9, name: 'Enfermería', status: 'ACTIVE', totalYears: 3, visibleInCatalog: false };

function planSubject(extra) {
  return {
    programId: 9,
    category: 'MANDATORY',
    curricularTerm: 'FIRST',
    credits: 6,
    status: 'ACTIVE',
    electiveGroupId: null,
    ...extra,
  };
}

const PLAN = [
  planSubject({ id: 134, subjectId: 1, subjectCode: 'S001', subjectName: 'Anatomía', curricularYear: 1 }),
  planSubject({ id: 135, subjectId: 3, subjectCode: 'S003', subjectName: 'Fisiología', curricularYear: 2, curricularTerm: 'SECOND' }),
  planSubject({ id: 136, subjectId: 4, subjectCode: 'S004', subjectName: 'Bioética', curricularYear: 5 }),
];

const ACTIVE_SUBJECTS = [
  { id: 20, code: 'S020', name: 'Microbiología', status: 'ACTIVE' },
  { id: 21, code: 'S021', name: 'Inglés técnico', status: 'ACTIVE' },
];

function mockProgram({ plan = PLAN, programGroups = [], onAdd, onDeactivate } = {}) {
  server.use(
    http.get(`${API}/programs/9`, () => HttpResponse.json(PROGRAM)),
    http.get(`${API}/programs/9/subjects`, () => HttpResponse.json(plan)),
    http.get(`${API}/subjects`, () => HttpResponse.json(ACTIVE_SUBJECTS)),
    http.get(`${API}/elective-groups`, () => HttpResponse.json([{ id: 3, name: 'Optativas de idioma' }])),
    http.get(`${API}/programs/9/elective-groups`, () => HttpResponse.json(programGroups)),
    http.post(`${API}/programs/9/subjects`, onAdd ?? (() => HttpResponse.json({ id: 200 }, { status: 201 }))),
    http.post(`${API}/program-subjects/:id/deactivate`, onDeactivate ?? (() => new HttpResponse(null, { status: 204 }))),
  );
}

function mount() {
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/carreras/:id" element={<CarreraDetalle />} />
    </Routes>,
    { route: '/administrativo/gestion-academica/carreras/9' },
  );
}

function yearBlock(title) {
  return screen.getByRole('heading', { name: title }).closest('[data-year-block]');
}

async function openAddPanelFor(user, title) {
  await user.click(within(yearBlock(title)).getByRole('button', { name: /Agregar materia/ }));
  await user.click(await screen.findByRole('button', { name: /Microbiología/ }));
}

describe('CarreraDetalle — plan de estudios', () => {
  it('shows subjects loaded beyond the program duration so they can be fixed', async () => {
    mockProgram();
    mount();

    expect(await screen.findByText('Bioética')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /fuera de la duración/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Quitar Bioética del plan' })).toBeInTheDocument();
  });

  it('preloads the year of the block where "Agregar materia" was clicked, even after using another block', async () => {
    const bodies = [];
    mockProgram({
      onAdd: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ id: 200 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    mount();
    await screen.findByText('Anatomía');

    await openAddPanelFor(user, '1.° año');
    expect(screen.getByLabelText('Año curricular')).toHaveValue(1);
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    await openAddPanelFor(user, '2.° año');
    expect(screen.getByLabelText('Año curricular')).toHaveValue(2);
  });

  it('asks for the term of a mandatory subject instead of failing with a generic error', async () => {
    let posted = false;
    mockProgram({
      onAdd: () => {
        posted = true;
        return HttpResponse.json({ id: 200 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    mount();
    await screen.findByText('Anatomía');

    await openAddPanelFor(user, '1.° año');
    await user.type(screen.getByLabelText('Créditos *'), '6');
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    expect(await screen.findByText('Elegí el cuatrimestre de la materia obligatoria.')).toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it('adds an additional mandatory subject without year nor term', async () => {
    const bodies = [];
    mockProgram({
      onAdd: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ id: 200 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    mount();
    await screen.findByText('Anatomía');

    await openAddPanelFor(user, '1.° año');
    await user.selectOptions(screen.getByLabelText('Categoría *'), 'ADDITIONAL_MANDATORY');
    expect(screen.queryByLabelText('Año curricular')).not.toBeInTheDocument();
    await user.type(screen.getByLabelText('Créditos *'), '4');
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toEqual({ subjectId: 20, category: 'ADDITIONAL_MANDATORY', credits: 4 });
  });

  it('explains that electives need an elective group assigned to the program', async () => {
    mockProgram({ programGroups: [] });
    const user = userEvent.setup();
    mount();
    await screen.findByText('Anatomía');

    await openAddPanelFor(user, '1.° año');
    await user.selectOptions(screen.getByLabelText('Categoría *'), 'ELECTIVE');

    expect(screen.getByText(/no tiene grupos electivos asignados/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Agregar' })).toBeDisabled();
  });

  it('offers only the elective groups assigned to the program', async () => {
    const bodies = [];
    mockProgram({
      programGroups: [{ programId: 9, electiveGroupId: 3, minRequired: 1, maxAllowed: 2 }],
      onAdd: async ({ request }) => {
        bodies.push(await request.json());
        return HttpResponse.json({ id: 200 }, { status: 201 });
      },
    });
    const user = userEvent.setup();
    mount();
    await screen.findByText('Anatomía');

    await openAddPanelFor(user, '1.° año');
    await user.selectOptions(screen.getByLabelText('Categoría *'), 'ELECTIVE');
    await user.selectOptions(screen.getByLabelText('Grupo electivo *'), '3');
    await user.selectOptions(screen.getByLabelText('Cuatrimestre'), 'FIRST');
    await user.type(screen.getByLabelText('Créditos *'), '3');
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    await waitFor(() => expect(bodies).toHaveLength(1));
    expect(bodies[0]).toEqual({
      subjectId: 20,
      category: 'ELECTIVE',
      credits: 3,
      curricularYear: 1,
      curricularTerm: 'FIRST',
      electiveGroupId: 3,
    });
  });

  it('shows the specific reason when the backend rejects the year', async () => {
    mockProgram({
      onAdd: () => HttpResponse.json({ code: 'CURRICULAR_YEAR_OUT_OF_RANGE' }, { status: 400 }),
    });
    const user = userEvent.setup();
    mount();
    await screen.findByText('Anatomía');

    await openAddPanelFor(user, '1.° año');
    await user.selectOptions(screen.getByLabelText('Cuatrimestre'), 'FIRST');
    await user.type(screen.getByLabelText('Créditos *'), '6');
    await user.click(screen.getByRole('button', { name: 'Agregar' }));

    expect(await screen.findByText('El año tiene que estar entre 1 y la duración de la carrera.')).toBeInTheDocument();
  });

  it('removes a subject from the plan after confirming', async () => {
    const deactivated = [];
    mockProgram({
      onDeactivate: ({ params }) => {
        deactivated.push(params.id);
        return new HttpResponse(null, { status: 204 });
      },
    });
    const user = userEvent.setup();
    mount();
    await screen.findByText('Anatomía');

    await user.click(screen.getByRole('button', { name: 'Quitar Anatomía del plan' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Quitar del plan' }));

    await waitFor(() => expect(deactivated).toEqual(['134']));
  });
});
