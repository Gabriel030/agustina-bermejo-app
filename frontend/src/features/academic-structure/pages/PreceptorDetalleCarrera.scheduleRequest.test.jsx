import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorDetalleCarrera } from './PreceptorDetalleCarrera';

const { toastMock } = vi.hoisted(() => ({ toastMock: { success: vi.fn(), error: vi.fn() } }));
vi.mock('sonner', () => ({ toast: toastMock }));

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

async function openSheet(user) {
  await user.click(await screen.findByRole('button', { name: 'Modificar horario' }));
  return screen.findByRole('dialog');
}

describe('section schedule request', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
  });

  it('offers the action only for sections of an approved cycle', async () => {
    mockOffer({ cycle: aCycle({ status: 'DRAFT' }) });
    renderDetail();

    expect(await screen.findByText('Química')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Modificar horario' })).not.toBeInTheDocument();
  });

  it('does not offer the action to the director', async () => {
    mockOffer();
    renderDetail('director');

    expect(await screen.findByText('Química')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Modificar horario' })).not.toBeInTheDocument();
  });

  it('does not offer the action when the shift cannot be determined', async () => {
    mockOffer({ sections: [aSection({ shift: null, slots: [] })] });
    renderDetail();

    expect(await screen.findByText('Química')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Modificar horario' })).not.toBeInTheDocument();
  });

  it('does not offer the action in a closed cycle', async () => {
    mockOffer({ cycle: aCycle({ status: 'CLOSED' }) });
    const user = userEvent.setup();
    renderDetail();

    await user.click(await screen.findByRole('button', { name: 'Anteriores' }));
    expect(await screen.findByText('Química')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Modificar horario' })).not.toBeInTheDocument();
  });

  it('disables the action with a tooltip when a request is already pending', async () => {
    mockOffer({
      sections: [
        aSection({
          pendingApproval: true,
          pendingModification: {
            replacesSchedule: true,
            requestedAt: '2027-03-10T12:00:00Z',
            slots: [{ ...CURRENT_SLOT, classroomId: 4, classroomName: 'Aula 4' }],
          },
        }),
      ],
    });
    const user = userEvent.setup();
    renderDetail();

    expect(await screen.findByRole('button', { name: 'Modificar horario' })).toBeDisabled();
    expect(screen.getByText('Cambio de horario pendiente de aprobación')).toBeInTheDocument();
    expect(screen.getByText('Nuevo (pedido)')).toBeInTheDocument();
    await user.hover(screen.getByRole('button', { name: 'Modificar horario' }).parentElement);
    expect((await screen.findAllByText('Ya hay un pedido pendiente')).length).toBeGreaterThan(0);
  });

  it('shows the last rejection reason', async () => {
    mockOffer({ sections: [aSection({ lastRejectionReason: 'El aula está reservada' })] });
    renderDetail();

    expect(await screen.findByText('El Director rechazó el nuevo horario.')).toBeInTheDocument();
    expect(screen.getByText('Motivo: “El aula está reservada”')).toBeInTheDocument();
  });

  it('blocks continuing without changes and with fewer than three modules', async () => {
    mockOffer();
    const user = userEvent.setup();
    renderDetail();
    const dialog = within(await openSheet(user));

    expect(dialog.getByText('Nuevo horario de la comisión')).toBeInTheDocument();
    expect(dialog.getByText('Química · Comisión A')).toBeInTheDocument();
    expect(dialog.getByRole('button', { name: 'Continuar' })).toBeDisabled();
    expect(
      dialog.getByText('No hay cambios respecto del horario actual. Modificá una franja para continuar.'),
    ).toBeInTheDocument();

    await user.click(dialog.getByRole('button', { name: /Módulo 3,/ }));
    expect(dialog.getByText('Franja 1: necesita al menos 3 módulos seguidos.')).toBeInTheDocument();
    expect(dialog.getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });

  it('sends the new slots, refetches the offer and confirms with a toast', async () => {
    mockOffer();
    let sectionFetches = 0;
    let cycleFetches = 0;
    server.use(
      http.get(`${API}/academic-offering-cycles`, () => {
        cycleFetches += 1;
        return HttpResponse.json([aCycle()]);
      }),
      http.get(`${API}/academic-offering-cycles/:id/sections`, () => {
        sectionFetches += 1;
        return HttpResponse.json([aSection()]);
      }),
    );
    let body = null;
    server.use(
      http.post(`${API}/subject-sections/91/modification-request`, async ({ request }) => {
        body = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderDetail();
    const dialog = within(await openSheet(user));

    await user.selectOptions(dialog.getByLabelText('Día'), 'TUESDAY');
    await user.selectOptions(dialog.getByLabelText('Aula'), '4');
    await user.click(dialog.getByRole('button', { name: 'Continuar' }));

    expect(dialog.getByText('Revisá los cambios', { selector: 'h3' })).toHaveFocus();
    await user.click(dialog.getByRole('button', { name: 'Enviar pedido de aprobación' }));

    await vi.waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Pedido enviado al Director'));
    expect(body).toEqual({
      slots: [{ classroomId: 4, dayOfWeek: 'TUESDAY', startTime: '18:30', endTime: '20:30' }],
    });
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    await vi.waitFor(() => {
      expect(sectionFetches).toBe(2);
      expect(cycleFetches).toBe(2);
    });
  });

  it('does not treat a legacy slot as a change until the modules are chosen', async () => {
    mockOffer({ sections: [aSection({ slots: [{ ...CURRENT_SLOT, endTime: '20:50:00' }] })] });
    const user = userEvent.setup();
    renderDetail();
    const dialog = within(await openSheet(user));

    expect(dialog.getByText(/no coincide con los módulos del turno/)).toBeInTheDocument();
    expect(dialog.getByRole('button', { name: 'Continuar' })).toBeDisabled();
  });

  it('shows the server conflict in the review step and keeps the panel open', async () => {
    mockOffer();
    server.use(
      http.post(`${API}/subject-sections/91/modification-request`, () =>
        HttpResponse.json(errorBody('SECTION_SCHEDULE_CONFLICT'), { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderDetail();
    const dialog = within(await openSheet(user));

    await user.selectOptions(dialog.getByLabelText('Aula'), '4');
    await user.click(dialog.getByRole('button', { name: 'Continuar' }));
    await user.click(dialog.getByRole('button', { name: 'Enviar pedido de aprobación' }));

    expect(await dialog.findByRole('alert')).toHaveTextContent('La comisión se superpone con otra en el horario.');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(toastMock.success).not.toHaveBeenCalled();
  });
});
