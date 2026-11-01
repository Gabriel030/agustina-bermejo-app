import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorPeriodos } from './PreceptorPeriodos';
import { PreceptorPeriodoForm } from './PreceptorPeriodoForm';

const API = 'http://localhost:8080';

function aTerm(extra = {}) {
  return {
    id: 1,
    year: 2027,
    description: 'Primer cuatrimestre',
    type: 'FIRST_SEMESTER',
    status: 'ACTIVE',
    startDate: '2027-03-01T00:00:00',
    endDate: '2027-07-02T23:59:59',
    enrollmentStart: '2027-02-01T00:00:00',
    enrollmentEnd: '2027-02-28T23:59:59',
    enrollmentOpen: false,
    ...extra,
  };
}

const TERMS = [
  aTerm(),
  aTerm({ id: 2, description: 'Anual 2026', type: 'ANNUAL', year: 2026, status: 'INACTIVE', enrollmentOpen: true, startDate: '2026-03-02T00:00:00', endDate: '2026-11-27T23:59:59' }),
];

function renderApp(route) {
  return renderWithProviders(
    <Routes>
      <Route path="/preceptor/periodos" element={<PreceptorPeriodos />} />
      <Route path="/preceptor/periodos/nuevo" element={<PreceptorPeriodoForm />} />
      <Route path="/preceptor/periodos/:termId/editar" element={<PreceptorPeriodoForm />} />
    </Routes>,
    { route },
  );
}

describe('PreceptorPeriodos', () => {
  it('lists terms with Spanish type labels and status pills', async () => {
    server.use(http.get(`${API}/terms`, () => HttpResponse.json(TERMS)));
    renderApp('/preceptor/periodos');

    expect((await screen.findAllByText('Primer cuatrimestre')).length).toBeGreaterThan(0);
    expect(screen.getAllByText('Anual').length).toBeGreaterThan(0);
    expect(screen.getByText('Activo')).toBeInTheDocument();
    expect(screen.getByText('Inactivo')).toBeInTheDocument();
    expect(screen.getByText('01/03/2027 → 02/07/2027')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Filtrar por Tipo' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Filtrar por Estado' })).toBeInTheDocument();
  });

  it('shows an empty state with a call to create the first term', async () => {
    server.use(http.get(`${API}/terms`, () => HttpResponse.json([])));
    renderApp('/preceptor/periodos');

    expect(await screen.findByText('Todavía no hay períodos lectivos')).toBeInTheDocument();
  });

  it('deactivates a term after confirming in the dialog', async () => {
    const user = userEvent.setup();
    let called = null;
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json(TERMS)),
      http.post(`${API}/terms/1/deactivate`, () => {
        called = 'deactivate';
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp('/preceptor/periodos');

    await user.click(await screen.findByRole('button', { name: 'Acciones de Primer cuatrimestre 2027' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Desactivar período' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/deja de estar disponible/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar' }));

    await waitFor(() => expect(called).toBe('deactivate'));
  });

  it('explains the conflict when activating a term whose year and type already have an active one', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json(TERMS)),
      http.post(`${API}/terms/2/activate`, () =>
        HttpResponse.json({ code: 'ACTIVE_TERM_ALREADY_EXISTS', violations: [] }, { status: 409 }),
      ),
    );
    renderApp('/preceptor/periodos');

    await user.click(await screen.findByRole('button', { name: 'Acciones de Anual 2026 2026' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Activar período' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText(/Ya hay un período activo de este año y tipo/)).toBeInTheDocument();
  });
});

describe('PreceptorPeriodoForm', () => {
  it('creates a term and shows the Spanish message of the 409 for duplicated active terms', async () => {
    const user = userEvent.setup();
    let body = null;
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json([])),
      http.post(`${API}/terms`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ code: 'ACTIVE_TERM_ALREADY_EXISTS', violations: [] }, { status: 409 });
      }),
    );
    renderApp('/preceptor/periodos/nuevo');

    await user.click(await screen.findByRole('button', { name: 'Crear período' }));
    expect(await screen.findByText('Elegí un tipo de período.')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Intensivo de verano' }));
    await user.type(screen.getByLabelText('Descripción'), 'Verano');
    await user.type(screen.getByLabelText('Inicio de cursada'), '2027-01-05');
    await user.type(screen.getByLabelText('Fin de cursada'), '2027-02-20');
    await user.type(screen.getByLabelText('Inicio de inscripción'), '2026-12-01');
    await user.type(screen.getByLabelText('Fin de inscripción'), '2026-12-20');
    await user.click(screen.getByRole('button', { name: 'Crear período' }));

    expect(await screen.findByText(/Ya hay un período activo de este año y tipo/)).toBeInTheDocument();
    expect(body).toMatchObject({ type: 'INTENSIVE', description: 'Verano', startDate: '2027-01-05T00:00:00' });
  });

  it('shows the Hoy, Nuevo and Diferencia review before saving an edit', async () => {
    const user = userEvent.setup();
    let body = null;
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json(TERMS)),
      http.put(`${API}/terms/1`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(aTerm({ endDate: '2027-07-09T23:59:59' }));
      }),
    );
    renderApp('/preceptor/periodos/1/editar');

    expect(await screen.findByLabelText('Descripción')).toHaveValue('Primer cuatrimestre');
    expect(screen.getByDisplayValue('2027')).toHaveAttribute('readonly');
    const end = screen.getByLabelText('Fin de cursada');
    await user.clear(end);
    await user.type(end, '2027-07-09');
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));

    const row = await screen.findByRole('group', { name: 'Fin de cursada' });
    expect(within(row).getByText('02/07/2027')).toBeInTheDocument();
    expect(within(row).getByText('09/07/2027')).toBeInTheDocument();
    expect(within(row).getByText('+ 7 días')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Confirmar cambios' }));
    await waitFor(() => expect(body).toMatchObject({ endDate: '2027-07-09T23:59:59' }));
    expect(body).not.toHaveProperty('type');
  });
});
