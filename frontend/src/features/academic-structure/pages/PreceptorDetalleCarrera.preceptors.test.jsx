import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorDetalleCarrera } from './PreceptorDetalleCarrera';

const API = 'http://localhost:8080';

const CURRENT_SLOT = {
  classroomId: 3,
  classroomName: 'Aula 3',
  dayOfWeek: 'MONDAY',
  startTime: '18:30:00',
  endTime: '20:30:00',
};

function aCycle(extra = {}) {
  return {
    id: 15,
    status: 'APPROVED',
    termId: 7,
    termName: 'Ciclo lectivo 2027',
    programId: 6,
    programName: 'Gestión Ambiental',
    createdByName: 'Preceptor Demo',
    rejectionReason: null,
    ...extra,
  };
}

function aSection(extra = {}) {
  return {
    id: 91,
    name: 'Comisión A',
    subjectName: 'Química',
    status: 'ACTIVE',
    capacity: 30,
    shift: 'EVENING',
    instructors: [],
    pendingApproval: false,
    pendingModification: null,
    lastRejectionReason: null,
    slots: [CURRENT_SLOT],
    ...extra,
  };
}

function mockOffer({ cycle = aCycle(), sections = [aSection()] } = {}) {
  server.use(
    http.get(`${API}/programs/6`, () => HttpResponse.json({ id: 6, name: 'Gestión Ambiental', totalYears: 3 })),
    http.get(`${API}/academic-offering-cycles`, () => HttpResponse.json([cycle])),
    http.get(`${API}/academic-offering-cycles/:id/sections`, () => HttpResponse.json(sections)),
    http.get(`${API}/classrooms`, () =>
      HttpResponse.json([
        { id: 3, name: 'Aula 3', capacity: 40, status: 'ACTIVE' },
        { id: 4, name: 'Aula 4', capacity: 40, status: 'ACTIVE' },
      ]),
    ),
  );
}

function renderDetail(role = 'preceptor') {
  return renderWithProviders(
    <Routes>
      <Route path="/:role/oferta-academica/carreras/:programaId" element={<PreceptorDetalleCarrera />} />
    </Routes>,
    { route: `/${role}/oferta-academica/carreras/6` },
  );
}

const WITH_PENDING = { assignmentId: 5, personId: 31, personName: 'Marcela Quiroga', pendingRequestCount: 3 };

function mockReplace(requests, status = 200) {
  server.use(
    http.put(`${API}/preceptor-assignments/subject-sections/:id`, async ({ request }) => {
      requests.push(await request.json());
      if (status !== 200) return HttpResponse.json(errorBody('INTERNAL_ERROR'), { status });
      return HttpResponse.json([]);
    }),
  );
}

describe('section preceptors inline editing', () => {
  it('shows the section without preceptors and offers to assign one to the director', async () => {
    mockOffer();
    renderDetail('director');

    expect(await screen.findByText('Sin preceptor')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Asignar preceptor' })).toBeEnabled();
  });

  it('does not offer the editor to the preceptor role', async () => {
    mockOffer();
    renderDetail('preceptor');

    expect(await screen.findByText('Química')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Asignar preceptor' })).not.toBeInTheDocument();
  });

  it('replaces the preceptors of the section with the chosen ones', async () => {
    const requests = [];
    mockOffer();
    mockReplace(requests);
    const user = userEvent.setup();
    renderDetail('director');

    await user.click(await screen.findByRole('button', { name: 'Asignar preceptor' }));
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Buscar preceptor' }));
    await user.click(await screen.findByRole('option', { name: /Marcela Quiroga/ }));
    await user.click(screen.getByRole('button', { name: 'Listo' }));
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(requests).toEqual([{ personIds: [31] }]));
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument());
  });

  it('warns about pending requests and about the section being left without preceptor', async () => {
    mockOffer({ sections: [aSection({ preceptors: [WITH_PENDING] })] });
    const user = userEvent.setup();
    renderDetail('director');

    await user.click(await screen.findByRole('button', { name: 'Preceptores' }));
    await user.click(screen.getByRole('button', { name: /Quitar/ }));

    expect(await screen.findByText(/Queda sin preceptor\./)).toBeInTheDocument();
    expect(screen.getByText(/La comisión tiene 3 solicitudes pendientes;/)).toBeInTheDocument();
  });

  it('keeps the changes and offers a retry when saving fails', async () => {
    const requests = [];
    mockOffer({ sections: [aSection({ preceptors: [WITH_PENDING] })] });
    mockReplace(requests, 500);
    const user = userEvent.setup();
    renderDetail('director');

    await user.click(await screen.findByRole('button', { name: 'Preceptores' }));
    await user.click(screen.getByRole('button', { name: /Quitar/ }));
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByText('No pudimos guardar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('closes the editor with Escape and gives the focus back to the button', async () => {
    mockOffer({ sections: [aSection({ preceptors: [WITH_PENDING] })] });
    const user = userEvent.setup();
    renderDetail('director');

    await user.click(await screen.findByRole('button', { name: 'Preceptores' }));
    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.getByRole('button', { name: 'Preceptores' })).toHaveFocus());
    expect(screen.queryByRole('button', { name: 'Guardar' })).not.toBeInTheDocument();
  });

  it('locks the other sections while one is being edited', async () => {
    mockOffer({ sections: [aSection(), aSection({ id: 92, name: 'Comisión B' })] });
    const user = userEvent.setup();
    renderDetail('director');

    const buttons = await screen.findAllByRole('button', { name: 'Asignar preceptor' });
    await user.click(buttons[0]);

    expect(screen.getByRole('button', { name: 'Asignar preceptor' })).toBeDisabled();
  });
});
