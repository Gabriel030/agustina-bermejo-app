import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes, useLocation } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { installFixedClock } from '@/test/fixedClock';
import { createDeferred } from '@/test/deferred';
import { REQUESTS_FIXTURE, attendanceChangeRequest } from '@/test/fixtures/attendanceRequests';
import { DocenteTomarAsistencia } from '@/features/attendance/pages/DocenteTomarAsistencia';

const API = 'http://localhost:8080';

const SECTIONS = [
  { type: 'PROGRAM', id: 5, subjectName: 'Programación II', sectionName: 'Comisión A', termStartDate: '2026-03-01T00:00:00Z', termEndDate: '2026-12-15T00:00:00Z' },
  { type: 'PROGRAM', id: 6, subjectName: 'Bases de Datos', sectionName: 'Comisión B', termStartDate: '2026-03-01T00:00:00Z', termEndDate: '2026-12-15T00:00:00Z' },
];

const HISTORY = {
  classDates: ['2026-11-04'],
  rows: [
    {
      studentId: 1,
      studentName: 'Valentina Suárez',
      attendancePercent: 80,
      cellsByDate: {
        '2026-11-04': { attendanceId: 901, status: 'ABSENT', justificationDeadline: null, justifiable: false, rejection: null },
      },
    },
  ],
};

function mockApi({ requests = REQUESTS_FIXTURE } = {}) {
  server.use(
    http.get(`${API}/me/teaching-sections`, () => HttpResponse.json(SECTIONS)),
    http.get(`${API}/me/attendance-change-requests`, () => HttpResponse.json(requests)),
    http.get(`${API}/program-sections/5/attendance/history`, () => HttpResponse.json(HISTORY)),
    http.get(`${API}/program-sections/5/roster`, () => HttpResponse.json([])),
    http.get(`${API}/program-sections/5/class-sessions/:date`, () => new HttpResponse(null, { status: 404 })),
  );
}

function LocationProbe() {
  const location = useLocation();
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>;
}

function renderPage(route) {
  return renderWithProviders(
    <>
      <Routes>
        <Route path="/docente/tomar-asistencia" element={<DocenteTomarAsistencia />} />
      </Routes>
      <LocationProbe />
    </>,
    { route },
  );
}

describe('DocenteTomarAsistencia pedidos tab', () => {
  installFixedClock();

  it('shows the pending count on the Mis pedidos tab', async () => {
    mockApi();
    renderPage('/docente/tomar-asistencia');

    const tab = await screen.findByRole('tab', { name: /Mis pedidos/ });
    expect(await within(tab).findByText('1')).toBeInTheDocument();
  });

  it('lists the requests newest first and keeps both top tabs visible', async () => {
    mockApi();
    renderPage('/docente/tomar-asistencia?tab=pedidos');

    await screen.findByText('Joaquín Benítez');
    const rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('Valentina Suárez')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Asistencia' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Mis pedidos/ })).toHaveAttribute('aria-selected', 'true');
  });

  it('expands a rejected request showing the rejection reason and a re-request link to the history', async () => {
    const user = userEvent.setup();
    mockApi();
    renderPage('/docente/tomar-asistencia?tab=pedidos');

    const row = (await screen.findByText('Joaquín Benítez')).closest('tr');
    await user.click(within(row).getByRole('button', { name: 'Ver detalle del pedido' }));

    expect(await screen.findByText('La asistencia ya fue cerrada en el acta del 31/10.')).toBeInTheDocument();
    expect(screen.getByText('Llegó 20 minutos tarde.')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: /Volver a pedir/ });
    expect(link).toHaveAttribute('href', expect.stringContaining('tab=historial'));
    expect(link).toHaveAttribute('href', expect.stringContaining('seccion=PROGRAM-6'));
    expect(link).toHaveAttribute('href', expect.stringContaining('fecha=2026-10-30'));
    expect(link).toHaveAttribute('href', expect.stringContaining('pedido=40'));
  });

  it('shows a fallback when a rejected request has no rejection reason', async () => {
    const user = userEvent.setup();
    mockApi();
    renderPage('/docente/tomar-asistencia?tab=pedidos');

    const row = (await screen.findByText('Lucía Herrera')).closest('tr');
    await user.click(within(row).getByRole('button', { name: 'Ver detalle del pedido' }));

    expect(await screen.findByText(/Se rechazó sin motivo registrado/)).toBeInTheDocument();
  });

  it('opens the request from the pedido param expanded and without a re-request link when pending', async () => {
    mockApi();
    renderPage('/docente/tomar-asistencia?tab=pedidos&pedido=41');

    expect(await screen.findByText('Esperando la respuesta de preceptoría.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Volver a pedir/ })).not.toBeInTheDocument();
  });

  it('shows the empty state when the teacher has no requests', async () => {
    mockApi({ requests: [] });
    renderPage('/docente/tomar-asistencia?tab=pedidos');

    expect(await screen.findByText('Todavía no pediste ningún cambio')).toBeInTheDocument();
  });

  it('shows a loading state while the requests are pending', async () => {
    const gate = createDeferred();
    mockApi();
    server.use(
      http.get(`${API}/me/attendance-change-requests`, async () => {
        await gate.promise;
        return HttpResponse.json(REQUESTS_FIXTURE);
      }),
    );
    renderPage('/docente/tomar-asistencia?tab=pedidos');

    expect(await screen.findByLabelText('Cargando')).toBeInTheDocument();
    gate.resolve();
    expect(await screen.findByText('Joaquín Benítez')).toBeInTheDocument();
  });

  it('shows an error with retry and recovers when retrying', async () => {
    const user = userEvent.setup();
    mockApi();
    let calls = 0;
    server.use(
      http.get(`${API}/me/attendance-change-requests`, () => {
        calls += 1;
        return calls <= 2 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(REQUESTS_FIXTURE);
      }),
    );
    renderPage('/docente/tomar-asistencia?tab=pedidos');

    expect(await screen.findByText(/No pudimos cargar tus pedidos/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Joaquín Benítez')).toBeInTheDocument();
  });

  it('switches tabs updating the url', async () => {
    const user = userEvent.setup();
    mockApi();
    renderPage('/docente/tomar-asistencia');

    await user.click(await screen.findByRole('tab', { name: /Mis pedidos/ }));
    expect(screen.getByTestId('location')).toHaveTextContent('/docente/tomar-asistencia?tab=pedidos');
    await user.click(screen.getByRole('tab', { name: 'Asistencia' }));
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/docente\/tomar-asistencia$/);
  });
});

describe('DocenteTomarAsistencia deep link to the history', () => {
  installFixedClock();

  it('opens the history of the commission with a banner naming the rejected request', async () => {
    mockApi({
      requests: [
        attendanceChangeRequest({ id: 40, attendanceId: 901, status: 'REJECTED', rejectionReason: 'Ya se cerró el acta.' }),
      ],
    });
    renderPage('/docente/tomar-asistencia?tab=historial&seccion=PROGRAM-5&fecha=2026-11-04&pedido=40');

    expect(await screen.findByText(/Pedido #40 rechazado/)).toBeInTheDocument();
    expect(screen.getByText(/Ya se cerró el acta\./)).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Volver a Mis pedidos' })).toBeInTheDocument();
  });

  it('replaces the request button with a pending tag and a link to the request', async () => {
    const user = userEvent.setup();
    mockApi({ requests: [attendanceChangeRequest({ id: 41, attendanceId: 901, status: 'PENDING' })] });
    renderPage('/docente/tomar-asistencia?tab=historial&seccion=PROGRAM-5');

    await user.click(await screen.findByTitle('Ausente'));

    expect(await screen.findByText('Pedido pendiente')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver pedido #41' })).toHaveAttribute('href', '/docente/tomar-asistencia?tab=pedidos&pedido=41');
    expect(screen.queryByRole('button', { name: 'Solicitar modificación' })).not.toBeInTheDocument();
  });

  it('keeps the request button when the only request for that attendance is already resolved', async () => {
    const user = userEvent.setup();
    mockApi({ requests: [attendanceChangeRequest({ id: 41, attendanceId: 901, status: 'REJECTED', rejectionReason: 'No.' })] });
    renderPage('/docente/tomar-asistencia?tab=historial&seccion=PROGRAM-5');

    await user.click(await screen.findByTitle('Ausente'));

    expect(await screen.findByRole('button', { name: 'Solicitar modificación' })).toBeInTheDocument();
    expect(screen.queryByText('Pedido pendiente')).not.toBeInTheDocument();
  });
});
