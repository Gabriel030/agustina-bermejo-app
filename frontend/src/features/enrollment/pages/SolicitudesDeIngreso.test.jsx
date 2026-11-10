import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { reviewApplication } from '@/test/fixtures/programApplications';
import { SolicitudesDeIngreso } from '@/features/enrollment/pages/SolicitudesDeIngreso';

const API = 'http://localhost:8080';

function application(overrides = {}) {
  return reviewApplication(overrides);
}

const pendingRows = [
  application({ id: 52, studentFullName: 'Joaquín Benítez', requestedAt: '2026-11-06T10:00:00', programName: 'Profesorado de Inglés', documentation: { complete: false, missingTypes: ['ANALITICO_SECUNDARIO'], documents: [] } }),
  application({ id: 51 }),
];

function renderList(route = '/preceptor/solicitudes-de-ingreso') {
  return renderWithProviders(
    <Routes>
      <Route path="/preceptor/solicitudes-de-ingreso" element={<SolicitudesDeIngreso />} />
      <Route path="/preceptor/solicitudes-de-ingreso/:id" element={<div>Ficha de la solicitud</div>} />
    </Routes>,
    { route },
  );
}

function mockPending(rows = pendingRows) {
  server.use(http.get(`${API}/program-enrollment-applications/pending`, () => HttpResponse.json(rows)));
}

describe('SolicitudesDeIngreso', () => {
  it('lists pending applications oldest first with the count in the tab', async () => {
    mockPending();
    renderList();

    const rows = await screen.findAllByRole('row');
    expect(within(rows[1]).getByText('Valentina Suárez')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Joaquín Benítez')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Pendientes/ })).toHaveTextContent('2');
    expect(screen.getAllByText('Primer ingreso 2026').length).toBeGreaterThan(0);
    expect(screen.getByText('Falta analítico')).toBeInTheDocument();
    expect(screen.getByText('Completa')).toBeInTheDocument();
  });

  it('links each row to its review sheet', async () => {
    mockPending();
    renderList();

    const link = await screen.findByRole('link', { name: 'Revisar la solicitud de Valentina Suárez' });
    expect(link).toHaveAttribute('href', '/preceptor/solicitudes-de-ingreso/51');
    await userEvent.click(link);
    expect(await screen.findByText('Ficha de la solicitud')).toBeInTheDocument();
  });

  it('filters by documentation with the column filter', async () => {
    mockPending();
    renderList();
    await screen.findByText('Joaquín Benítez');

    await userEvent.click(screen.getByRole('button', { name: /documentación/i }));
    await userEvent.click(await screen.findByRole('checkbox', { name: /Completa/ }));

    await waitFor(() => expect(screen.queryByText('Valentina Suárez')).not.toBeInTheDocument());
    expect(screen.getByText('Joaquín Benítez')).toBeInTheDocument();
  });

  it('shows the empty state when nothing is pending', async () => {
    mockPending([]);
    renderList();

    expect(await screen.findByText('No hay solicitudes por revisar')).toBeInTheDocument();
  });

  it('shows an error with retry and recovers', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/program-enrollment-applications/pending`, () => {
        calls += 1;
        return calls === 1 ? HttpResponse.json(errorBody('INTERNAL_ERROR'), { status: 500 }) : HttpResponse.json(pendingRows);
      }),
    );
    renderList();

    await userEvent.click(await screen.findByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Joaquín Benítez')).toBeInTheDocument();
  });

  it('opens the history with search and status sent to the server', async () => {
    mockPending();
    const requests = [];
    server.use(
      http.get(`${API}/program-enrollment-applications/history`, ({ request }) => {
        requests.push(new URL(request.url).searchParams.toString());
        return HttpResponse.json({
          items: [application({ id: 40, status: 'REJECTED', decidedByName: 'Marcela Díaz', decidedAt: '2026-11-02T10:00:00' })],
          totalElements: 1,
        });
      }),
    );
    renderList('/preceptor/solicitudes-de-ingreso?tab=historial');

    expect(await screen.findByText('Rechazada')).toBeInTheDocument();
    expect(screen.getByText('Marcela Díaz')).toBeInTheDocument();
    expect(requests[0]).toBe('page=0&size=10');

    await userEvent.type(screen.getByPlaceholderText('Buscar por alumno o DNI'), 'valen');
    await waitFor(() => expect(requests.at(-1)).toBe('page=0&size=10&search=valen'));
  });

  it('distinguishes an empty history from a search without matches', async () => {
    mockPending();
    server.use(http.get(`${API}/program-enrollment-applications/history`, () => HttpResponse.json({ items: [], totalElements: 0 })));
    renderList('/preceptor/solicitudes-de-ingreso?tab=historial');

    expect(await screen.findByText('Todavía no hay solicitudes resueltas')).toBeInTheDocument();
    await userEvent.type(screen.getByPlaceholderText('Buscar por alumno o DNI'), 'zzz');
    expect(await screen.findByText('Ninguna solicitud coincide')).toBeInTheDocument();
  });

  it('has no accessibility violations', async () => {
    mockPending();
    const { container } = renderList();
    await screen.findByText('Joaquín Benítez');

    expect(await axe(container)).toHaveNoViolations();
  });
});
