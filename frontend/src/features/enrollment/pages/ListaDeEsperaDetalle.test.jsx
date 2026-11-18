import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ListaDeEsperaDetalle } from '@/features/enrollment/pages/ListaDeEsperaDetalle';

vi.mock('@/hooks/useActiveRole', async () => await import('@/test/activeRoleFromUrl'));

const API = 'http://localhost:8080';

const queue = {
  kind: 'PROGRAM_SECTION',
  id: 1,
  name: 'Programación II, Comisión A',
  termName: '1.er cuatrimestre 2027',
  capacity: 30,
  occupied: 29,
  free: 1,
  waiting: 3,
  nextExpiry: null,
  entries: [
    { position: 1, enrollmentId: 11, student: { id: 1, fullName: 'Matías Gómez', documentNumber: '42115730' }, joinedAt: '2027-02-03T09:41:00', verdict: { eligible: false, reason: 'SCHEDULE_OVERLAP' } },
    { position: 2, enrollmentId: 12, student: { id: 2, fullName: 'Lucía Fernández', documentNumber: '41208559' }, joinedAt: '2027-02-03T09:50:00', verdict: { eligible: true } },
    { position: 3, enrollmentId: 13, student: { id: 3, fullName: 'Camila Rojas', documentNumber: '40992318' }, joinedAt: '2027-02-04T10:03:00', verdict: { eligible: true } },
  ],
};

function renderDetail(role = 'administrativo') {
  server.use(http.get(`${API}/waitlists/PROGRAM_SECTION/1`, () => HttpResponse.json(queue)));
  return renderWithProviders(
    <>
      <Routes>
        <Route path={`/${role}/inscripciones/listas-de-espera/:kind/:id`} element={<ListaDeEsperaDetalle />} />
      </Routes>
      <Toaster />
    </>,
    { route: `/${role}/inscripciones/listas-de-espera/PROGRAM_SECTION/1` },
  );
}

describe('ListaDeEsperaDetalle', () => {
  it('shows queue ordered by position', async () => {
    renderDetail();

    await screen.findByText('Matías Gómez');
    const names = screen.getAllByText(/^(Matías Gómez|Lucía Fernández|Camila Rojas)$/).map((node) => node.textContent);
    expect(names).toEqual(['Matías Gómez', 'Lucía Fernández', 'Camila Rojas']);
  });

  it('marks the first eligible entry as next', async () => {
    renderDetail();

    await screen.findByText('Lucía Fernández');
    const row = screen.getByText('Lucía Fernández').closest('tr');
    expect(within(row).getByText('Próximo')).toBeInTheDocument();
  });

  it('shows the seat summary and the free seat notice', async () => {
    renderDetail();

    expect(await screen.findByText(/Hay 1 lugar libre y todavía no se promovió a nadie/)).toBeInTheDocument();
    expect(screen.getByText('Cupo')).toBeInTheDocument();
    expect(screen.getByText('Esperando')).toBeInTheDocument();
  });

  it('hides actions for preceptor', async () => {
    renderDetail('preceptor');

    expect(await screen.findByText(/Modo solo lectura/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Acciones para/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Promover/ })).not.toBeInTheDocument();
  });

  it('does not show the read only notice to administrative staff', async () => {
    renderDetail('administrativo');

    await screen.findByText('Matías Gómez');
    expect(screen.queryByText(/Modo solo lectura/)).not.toBeInTheDocument();
  });

  it('shows skipped reason tooltip', async () => {
    renderDetail();

    const pill = await screen.findByText('Salteado: horarios');
    await userEvent.hover(pill);

    const tooltip = await screen.findByRole('tooltip');
    expect(tooltip).toHaveTextContent('Conserva su lugar');
  });

  it('shows the history tab with who did what', async () => {
    server.use(
      http.get(`${API}/waitlists/PROGRAM_SECTION/1/history`, () =>
        HttpResponse.json({
          items: [{ at: '2027-02-12T16:20:00', action: 'ENROLLMENT_WITHDRAWN_BY_STAFF', actorName: 'Marcela Duarte', studentName: 'Nahuel Cabrera', reason: 'Pidió la baja por mail' }],
          page: 0,
          size: 20,
          totalElements: 1,
        }),
      ),
    );
    renderDetail();
    await screen.findByText('Matías Gómez');

    await userEvent.click(screen.getByRole('tab', { name: 'Historial' }));

    expect(await screen.findByText(/Baja registrada por el personal: Nahuel Cabrera/)).toBeInTheDocument();
    expect(screen.getByText('Marcela Duarte')).toBeInTheDocument();
    expect(screen.getByText('Motivo: Pidió la baja por mail')).toBeInTheDocument();
  });

  it('explains when the list does not exist', async () => {
    renderWithProviders(
      <Routes>
        <Route path="/administrativo/inscripciones/listas-de-espera/:kind/:id" element={<ListaDeEsperaDetalle />} />
      </Routes>,
      { route: '/administrativo/inscripciones/listas-de-espera/COURSE/999' },
    );

    expect(await screen.findByText('No encontramos lo que buscabas')).toBeInTheDocument();
  });

  it('requires reason before prioritizing', async () => {
    let body = null;
    server.use(
      http.post(`${API}/waitlists/PROGRAM_SECTION/entries/12/prioritize`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({});
      }),
    );
    renderDetail();
    await screen.findByText('Lucía Fernández');

    await userEvent.click(screen.getByRole('button', { name: 'Acciones para Lucía Fernández' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Mover a primer lugar/ }));
    const confirm = await screen.findByRole('button', { name: 'Mover' });
    expect(confirm).toBeDisabled();

    await userEvent.type(screen.getByRole('textbox'), 'corto');
    expect(confirm).toBeDisabled();
    expect(screen.getByText('5/500')).toBeInTheDocument();

    await userEvent.type(screen.getByRole('textbox'), ' y suficiente');
    expect(confirm).toBeEnabled();
    await userEvent.click(confirm);

    await waitFor(() => expect(body).toEqual({ reason: 'corto y suficiente' }));
  });

  it('promote disabled when not eligible', async () => {
    renderDetail();
    await screen.findByText('Matías Gómez');

    await userEvent.click(screen.getByRole('button', { name: 'Acciones para Matías Gómez' }));

    expect(await screen.findByRole('menuitem', { name: /Promover ahora/ })).toHaveAttribute('aria-disabled', 'true');
  });

  it('promote shows the seat change before confirming', async () => {
    renderDetail();
    await screen.findByText('Lucía Fernández');

    await userEvent.click(screen.getByRole('button', { name: 'Acciones para Lucía Fernández' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Promover ahora/ }));

    expect(await screen.findByText('29 / 30')).toBeInTheDocument();
    expect(screen.getByText('30 / 30')).toBeInTheDocument();
  });

  it('promotes without a body and confirms with a toast', async () => {
    let received = null;
    server.use(
      http.post(`${API}/waitlists/PROGRAM_SECTION/entries/12/promote`, async ({ request }) => {
        received = await request.text();
        return HttpResponse.json({});
      }),
    );
    renderDetail();
    await screen.findByText('Lucía Fernández');

    await userEvent.click(screen.getByRole('button', { name: 'Acciones para Lucía Fernández' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Promover ahora/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Promover' }));

    expect(await screen.findByText('Persona promovida.')).toBeInTheDocument();
    expect(received).toBe('');
  });

  it('removes with the reason sent to the backend', async () => {
    let body = null;
    server.use(
      http.post(`${API}/waitlists/PROGRAM_SECTION/entries/13/remove`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({});
      }),
    );
    renderDetail();
    await screen.findByText('Camila Rojas');

    await userEvent.click(screen.getByRole('button', { name: 'Acciones para Camila Rojas' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Quitar de la lista/ }));
    const confirm = await screen.findByRole('button', { name: 'Quitar' });
    expect(confirm).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox'), 'pidió la baja por mail');
    await userEvent.click(confirm);

    await waitFor(() => expect(body).toEqual({ reason: 'pidió la baja por mail' }));
    expect(await screen.findByText('Persona quitada de la lista.')).toBeInTheDocument();
  });

  it.each([
    ['promote', 'WAITLIST_NO_FREE_SEAT', 'No hay lugar libre para promover a esta persona.'],
    ['promote', 'WAITLIST_ENTRY_NOT_WAITING', 'La persona ya no está en la lista de espera.'],
  ])('shows the specific message when %s fails with %s', async (action, code, message) => {
    server.use(
      http.post(`${API}/waitlists/PROGRAM_SECTION/entries/12/${action}`, () => HttpResponse.json(errorBody(code), { status: 409 })),
    );
    renderDetail();
    await screen.findByText('Lucía Fernández');

    await userEvent.click(screen.getByRole('button', { name: 'Acciones para Lucía Fernández' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Promover ahora/ }));
    await userEvent.click(await screen.findByRole('button', { name: 'Promover' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
  });

  it('shows the pending receipt message when removing is blocked', async () => {
    server.use(
      http.post(`${API}/waitlists/PROGRAM_SECTION/entries/13/remove`, () =>
        HttpResponse.json(errorBody('WAITLIST_REMOVAL_BLOCKED_BY_PENDING_RECEIPT'), { status: 409 })),
    );
    renderDetail();
    await screen.findByText('Camila Rojas');

    await userEvent.click(screen.getByRole('button', { name: 'Acciones para Camila Rojas' }));
    await userEvent.click(await screen.findByRole('menuitem', { name: /Quitar de la lista/ }));
    await userEvent.type(await screen.findByRole('textbox'), 'motivo suficiente');
    await userEvent.click(screen.getByRole('button', { name: 'Quitar' }));

    expect(await screen.findByText('No se puede quitar: tiene un comprobante enviado sin resolver.')).toBeInTheDocument();
  });
});
