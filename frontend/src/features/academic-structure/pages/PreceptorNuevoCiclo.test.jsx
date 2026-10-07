import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorNuevoCiclo } from './PreceptorNuevoCiclo';

const API = 'http://localhost:8080';

function aCycle(extra = {}) {
  return {
    id: 16,
    status: 'DRAFT',
    termId: 7,
    termName: 'Ciclo lectivo 2027',
    programId: 9,
    programName: 'Enfermería',
    courseId: null,
    courseName: null,
    createdByName: 'Preceptor Demo',
    rejectionReason: null,
    ...extra,
  };
}

const EXISTING_SECTION = {
  id: 206,
  name: 'A',
  subjectName: 'Anatomía',
  capacity: 20,
  status: 'INACTIVE',
  pendingApproval: false,
  instructors: [{ name: 'Mariana Gómez', role: 'LEAD' }],
  slots: [{ dayOfWeek: 'SATURDAY', startTime: '08:00:00', endTime: '12:00:00', classroomId: 3, classroomName: 'Laboratorio 2' }],
};

function mockCatalog() {
  server.use(
    http.get(`${API}/terms`, () => HttpResponse.json([{ id: 7, description: 'Ciclo lectivo 2027' }])),
    http.get(`${API}/programs`, () => HttpResponse.json([{ id: 9, name: 'Enfermería' }])),
    http.get(`${API}/courses`, () => HttpResponse.json([])),
    http.get(`${API}/programs/9/subjects`, () =>
      HttpResponse.json([{ id: 1, subjectId: 120, subjectName: 'Anatomía' }]),
    ),
    http.get(`${API}/instructors`, () => HttpResponse.json([{ id: 5, name: 'Mariana Gómez' }])),
    http.get(`${API}/classrooms`, () =>
      HttpResponse.json([
        { id: 3, name: 'Laboratorio 2', capacity: 20 },
        { id: 4, name: 'Sala de Prácticas', capacity: 18 },
      ]),
    ),
  );
}

function mockExistingCycle(cycle = aCycle(), sections = [EXISTING_SECTION]) {
  server.use(
    http.get(`${API}/academic-offering-cycles/${cycle.id}`, () => HttpResponse.json(cycle)),
    http.get(`${API}/academic-offering-cycles/${cycle.id}/sections`, () => HttpResponse.json(sections)),
  );
}

function renderWizard(route = '/preceptor/oferta-academica/nuevo-ciclo') {
  return renderWithProviders(
    <Routes>
      <Route path="/preceptor/oferta-academica/nuevo-ciclo" element={<PreceptorNuevoCiclo />} />
    </Routes>,
    { route },
  );
}

async function openSectionForm(user) {
  await user.click(await screen.findByRole('button', { name: /Agregar comisión para Anatomía/ }));
  await user.type(screen.getByLabelText('Nombre / turno'), 'B');
  await user.selectOptions(screen.getByLabelText('Docente titular'), '5');
}

describe('PreceptorNuevoCiclo', () => {
  it('resumes a draft cycle from the URL showing the sections already created', async () => {
    mockCatalog();
    mockExistingCycle();
    renderWizard('/preceptor/oferta-academica/nuevo-ciclo?cicloId=16');

    expect(await screen.findByText('Mariana Gómez', { exact: false })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver resumen (1 comisión)' })).toBeEnabled();
  });

  it('shows the summary with weekdays in Spanish and goes back to the sections step', async () => {
    mockCatalog();
    mockExistingCycle();
    const user = userEvent.setup();
    renderWizard('/preceptor/oferta-academica/nuevo-ciclo?cicloId=16');

    await user.click(await screen.findByRole('button', { name: 'Ver resumen (1 comisión)' }));
    expect(await screen.findByText(/Sábado 08:00–12:00/)).toBeInTheDocument();
    expect(screen.queryByText(/SATURDAY/)).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Volver a comisiones' }));
    expect(await screen.findByRole('button', { name: 'Ver resumen (1 comisión)' })).toBeInTheDocument();
  });

  it('uses the plural without accent when there are several sections', async () => {
    mockCatalog();
    mockExistingCycle(aCycle(), [EXISTING_SECTION, { ...EXISTING_SECTION, id: 207, name: 'B' }]);
    renderWizard('/preceptor/oferta-academica/nuevo-ciclo?cicloId=16');

    expect(await screen.findByRole('button', { name: 'Ver resumen (2 comisiones)' })).toBeInTheDocument();
    expect(screen.queryByText(/comisiónes/)).not.toBeInTheDocument();
  });

  it('shows the rejection reason while fixing a rejected cycle', async () => {
    mockCatalog();
    mockExistingCycle(aCycle({ status: 'REJECTED', rejectionReason: 'Falta la comisión del turno noche' }));
    renderWizard('/preceptor/oferta-academica/nuevo-ciclo?cicloId=16');

    expect(await screen.findByText('Falta la comisión del turno noche')).toBeInTheDocument();
  });

  it('does not let a cycle already sent for approval be edited', async () => {
    mockCatalog();
    mockExistingCycle(aCycle({ status: 'APPROVED' }));
    renderWizard('/preceptor/oferta-academica/nuevo-ciclo?cicloId=16');

    expect(await screen.findByText('Este ciclo ya fue enviado a aprobación y no se puede modificar.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Agregar comisión/ })).not.toBeInTheDocument();
  });

  it('warns when the section capacity exceeds the classroom capacity without calling the API', async () => {
    mockCatalog();
    mockExistingCycle();
    let posted = false;
    server.use(
      http.post(`${API}/academic-offering-cycles/16/sections`, () => {
        posted = true;
        return HttpResponse.json(300, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWizard('/preceptor/oferta-academica/nuevo-ciclo?cicloId=16');

    await openSectionForm(user);
    await user.type(screen.getByLabelText('Cupo'), '500');
    await user.selectOptions(screen.getByLabelText('Aula'), '4');
    await user.click(screen.getByRole('button', { name: 'Agregar comisión' }));

    expect(await screen.findByText('El cupo supera la capacidad del aula (18 lugares).')).toBeInTheDocument();
    expect(posted).toBe(false);
  });

  it('explains a classroom conflict reported by the API', async () => {
    mockCatalog();
    mockExistingCycle();
    server.use(
      http.post(`${API}/academic-offering-cycles/16/sections`, () =>
        HttpResponse.json({ code: 'SECTION_SCHEDULE_CONFLICT' }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderWizard('/preceptor/oferta-academica/nuevo-ciclo?cicloId=16');

    await openSectionForm(user);
    await user.type(screen.getByLabelText('Cupo'), '15');
    await user.selectOptions(screen.getByLabelText('Aula'), '3');
    await user.click(screen.getByRole('button', { name: 'Agregar comisión' }));

    expect(
      await screen.findByText('El aula ya está ocupada ese día y en ese horario durante este período.'),
    ).toBeInTheDocument();
  });

  it('explains how to resume when a cycle for that period is already in progress', async () => {
    mockCatalog();
    server.use(
      http.post(`${API}/academic-offering-cycles`, () =>
        HttpResponse.json({ code: 'CYCLE_ALREADY_IN_PROGRESS' }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderWizard();

    await screen.findByRole('option', { name: 'Ciclo lectivo 2027' });
    await user.selectOptions(screen.getByLabelText('Período académico'), '7');
    await screen.findByRole('option', { name: 'Enfermería' });
    await user.selectOptions(screen.getByLabelText('Carrera'), '9');
    await user.click(screen.getByRole('button', { name: 'Crear borrador y continuar' }));

    expect(
      await screen.findByText(/Ya hay un ciclo en construcción o en revisión para este período/),
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ir a la carrera' })).toHaveAttribute(
      'href',
      '/preceptor/oferta-academica/carreras/9',
    );
  });
});
