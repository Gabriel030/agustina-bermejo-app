import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { SolicitudesAsistencia } from '@/features/attendance/pages/SolicitudesAsistencia';

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

const API = 'http://localhost:8080';

const APPROVED_CORRECTION = {
  id: 41,
  attendanceId: 7,
  createdAt: '2026-11-20T09:00:00',
  classDate: '2026-11-19',
  previousStatus: 'ABSENT',
  requestedStatus: 'PRESENT',
  reason: 'El alumno llegó y quedó sin marcar.',
  requestedByName: 'Lucía Ferreyra',
  sectionName: 'Comisión A',
  studentName: 'Mateo Giménez',
  subjectName: 'Historia del Arte',
  status: 'APPROVED',
  decidedAt: '2026-11-24T10:00:00',
  decidedByName: 'Paula Ríos',
  rejectionReason: null,
  revertible: true,
  revertibleUntil: '2026-12-01',
};

const REJECTED_CORRECTION = {
  ...APPROVED_CORRECTION,
  id: 42,
  studentName: 'Camila Duarte',
  status: 'REJECTED',
  rejectionReason: 'La clase ya estaba firmada.',
};

const APPROVED_JUSTIFICATION = {
  ...APPROVED_CORRECTION,
  id: 43,
  studentName: 'Valentina Suárez',
  requestedStatus: 'JUSTIFIED',
};

const REJECTED_JUSTIFICATION = {
  ...REJECTED_CORRECTION,
  id: 44,
  studentName: 'Joaquín Paz',
  requestedStatus: 'JUSTIFIED',
};

function page(items, totalElements = items.length) {
  return { items, page: 0, size: 20, totalElements };
}

function mockResolved(items, totalElements) {
  const calls = { resultParams: [], sizes: [], revertBody: null, revertedId: null };
  let current = items;
  server.use(
    http.get(`${API}/attendance/change-requests`, () => HttpResponse.json([])),
    http.get(`${API}/attendance/justifications`, () => HttpResponse.json({ items: [], totalElements: 0 })),
    http.get(`${API}/attendance/change-requests/resolved`, ({ request }) => {
      const url = new URL(request.url);
      calls.resultParams.push(url.searchParams.get('result'));
      calls.sizes.push(Number(url.searchParams.get('size')));
      return HttpResponse.json(page(current, totalElements ?? current.length));
    }),
    http.post(`${API}/attendance/change-requests/:id/revert`, async ({ params, request }) => {
      calls.revertedId = params.id;
      calls.revertBody = await request.json();
      current = [];
      return HttpResponse.json({ ...APPROVED_CORRECTION, status: 'PENDING' });
    }),
  );
  return calls;
}

describe('SolicitudesAsistencia resolved tab', () => {
  beforeEach(() => {
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('opens the resolved tab from the query string and shows the resolution line', async () => {
    mockResolved([APPROVED_CORRECTION]);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    expect(await screen.findByText('Mateo Giménez')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Resueltas' })).toHaveAttribute('data-state', 'active');
    expect(screen.getByText('Aprobada por Paula Ríos · 24/11/2026')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprobar' })).not.toBeInTheDocument();
  });

  it('shows the rejection reason on rejected requests', async () => {
    mockResolved([REJECTED_CORRECTION]);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    expect(await screen.findByText('Rechazada por Paula Ríos · 24/11/2026')).toBeInTheDocument();
    expect(screen.getByText('La clase ya estaba firmada.')).toBeInTheDocument();
  });

  it('switches between pending and resolved and filters by result', async () => {
    const calls = mockResolved([APPROVED_CORRECTION]);
    renderWithProviders(<SolicitudesAsistencia />);

    await screen.findByText('Sin solicitudes pendientes');
    await userEvent.click(screen.getByRole('tab', { name: 'Resueltas' }));
    await screen.findByText('Mateo Giménez');

    await userEvent.click(screen.getByRole('button', { name: 'Rechazadas' }));
    await waitFor(() => expect(calls.resultParams).toContain('REJECTED'));
    expect(screen.getByRole('button', { name: 'Rechazadas' })).toHaveAttribute('aria-pressed', 'true');
    expect(calls.resultParams[0]).toBeNull();
  });

  it('shows the empty state of the resolved tab and of a filtered result', async () => {
    mockResolved([]);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    expect(await screen.findByText('Todavía no hay solicitudes resueltas')).toBeInTheDocument();
    expect(screen.getByText('Las solicitudes que apruebes o rechacen aparecen acá.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Aprobadas' }));
    expect(await screen.findByText('Ninguna solicitud aprobada')).toBeInTheDocument();
    expect(screen.getByText('Probá con otro resultado.')).toBeInTheDocument();
  });

  it('shows how many resolved requests are displayed and loads more', async () => {
    const calls = mockResolved([APPROVED_CORRECTION], 35);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    expect(await screen.findByText('Mostrando 1 de 35 solicitudes')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Ver más' }));
    await waitFor(() => expect(calls.sizes).toContain(40));
  });

  it('offers to revert only when the backend says so and shows its deadline', async () => {
    mockResolved([
      { ...APPROVED_CORRECTION, id: 51, studentName: 'En plazo', revertible: true, revertibleUntil: '2026-12-03' },
      { ...APPROVED_CORRECTION, id: 52, studentName: 'Vencida', revertible: false, revertibleUntil: '2026-11-26' },
    ]);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    const inTime = (await screen.findByText('En plazo')).closest('[data-testid]');
    const expired = screen.getByText('Vencida').closest('[data-testid]');

    expect(within(inTime).getByRole('button', { name: 'Revertir, hasta el fin del día 03/12/2026' })).toBeInTheDocument();
    expect(within(inTime).getByText('Revertible hasta el fin del día 03/12/2026')).toBeInTheDocument();
    expect(within(expired).queryByRole('button', { name: /Revertir/ })).not.toBeInTheDocument();
    expect(within(expired).getByText('Pasaron más de 7 días: ya no se puede revertir.')).toBeInTheDocument();
  });

  it.each([
    ['approved correction', APPROVED_CORRECTION, 'Revertir aprobación', 'La asistencia de Mateo Giménez vuelve a «Ausente» y la solicitud vuelve a Pendientes. Queda registrado quién la revirtió y por qué.', 'Explicá por qué se revierte la aprobación'],
    ['approved justification', APPROVED_JUSTIFICATION, 'Revertir justificación aprobada', 'La inasistencia de Valentina Suárez vuelve a «Ausente» y la justificación vuelve a Pendientes. Queda registrado quién la revirtió y por qué.', 'Explicá por qué se revierte la aprobación'],
    ['rejected correction', REJECTED_CORRECTION, 'Revertir rechazo', 'La solicitud vuelve a Pendientes para que la decidas de nuevo. La asistencia no cambia. Queda registrado quién la revirtió y por qué.', 'Explicá por qué se revierte el rechazo'],
    ['rejected justification', REJECTED_JUSTIFICATION, 'Revertir rechazo de justificación', 'La justificación vuelve a Pendientes para que la decidas de nuevo. La inasistencia sigue como Ausente. Queda registrado quién la revirtió y por qué.', 'Explicá por qué se revierte el rechazo'],
  ])('opens the revert dialog for %s with its own copy', async (_name, request, title, description, placeholder) => {
    mockResolved([request]);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    await userEvent.click(await screen.findByRole('button', { name: /^Revertir, hasta/ }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(title)).toBeInTheDocument();
    expect(within(dialog).getByText(description)).toBeInTheDocument();
    expect(within(dialog).getByPlaceholderText(placeholder)).toBeInTheDocument();
  });

  it('requires a reason of at least 10 characters before reverting', async () => {
    const calls = mockResolved([APPROVED_CORRECTION]);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    await userEvent.click(await screen.findByRole('button', { name: /^Revertir, hasta/ }));
    const dialog = await screen.findByRole('dialog');

    await userEvent.click(within(dialog).getByRole('button', { name: 'Revertir' }));
    expect(await within(dialog).findByText('El motivo de la reversión es obligatorio.')).toBeInTheDocument();

    await userEvent.type(within(dialog).getByRole('textbox'), 'corto');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Revertir' }));
    expect(await within(dialog).findByText('El motivo debe tener al menos 10 caracteres.')).toBeInTheDocument();
    expect(calls.revertBody).toBeNull();
  });

  it('reverts a decision, notifies and refreshes the list', async () => {
    const calls = mockResolved([APPROVED_CORRECTION]);
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    await userEvent.click(await screen.findByRole('button', { name: /^Revertir, hasta/ }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByRole('textbox'), 'Se firmó por error.');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Revertir' }));

    await waitFor(() => expect(toastMock.success).toHaveBeenCalledWith('Decisión revertida. La solicitud volvió a Pendientes.'));
    expect(calls.revertedId).toBe('41');
    expect(calls.revertBody).toEqual({ reason: 'Se firmó por error.' });
    expect(await screen.findByText('Todavía no hay solicitudes resueltas')).toBeInTheDocument();
  });

  it('shows the domain error and refreshes when the revert window expired', async () => {
    const calls = mockResolved([APPROVED_CORRECTION]);
    server.use(
      http.post(`${API}/attendance/change-requests/:id/revert`, () =>
        HttpResponse.json(errorBody('ATTENDANCE_DECISION_REVERT_WINDOW_EXPIRED'), { status: 409 }),
      ),
    );
    renderWithProviders(<SolicitudesAsistencia />, { route: '/?tab=resueltas' });

    await userEvent.click(await screen.findByRole('button', { name: /^Revertir, hasta/ }));
    const dialog = await screen.findByRole('dialog');
    await userEvent.type(within(dialog).getByRole('textbox'), 'Se firmó por error.');
    const fetchesBefore = calls.resultParams.length;
    await userEvent.click(within(dialog).getByRole('button', { name: 'Revertir' }));

    await waitFor(() =>
      expect(toastMock.error).toHaveBeenCalledWith(expect.stringContaining('Ya pasó el plazo para revertir')),
    );
    await waitFor(() => expect(calls.resultParams.length).toBeGreaterThan(fetchesBefore));
    expect(toastMock.success).not.toHaveBeenCalled();
  });
});
