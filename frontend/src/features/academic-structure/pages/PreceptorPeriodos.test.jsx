import { describe, expect, it } from 'vitest';
import { errorBody } from '@/test/fixtures/errors';
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
      {['preceptor', 'administrativo', 'director'].map((role) => [
        <Route key={`${role}-list`} path={`/${role}/periodos`} element={<PreceptorPeriodos />} />,
        <Route key={`${role}-new`} path={`/${role}/periodos/nuevo`} element={<PreceptorPeriodoForm />} />,
        <Route key={`${role}-edit`} path={`/${role}/periodos/:termId/editar`} element={<PreceptorPeriodoForm />} />,
      ])}
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
        HttpResponse.json(errorBody('ACTIVE_TERM_ALREADY_EXISTS'), { status: 409 }),
      ),
    );
    renderApp('/preceptor/periodos');

    await user.click(await screen.findByRole('button', { name: 'Acciones de Anual 2026 2026' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Activar período' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText(/Ya hay un período activo de este año y tipo/)).toBeInTheDocument();
  });

  it('labels the enrollment pill by how enrollment is opened', async () => {
    server.use(
      http.get(`${API}/terms`, () =>
        HttpResponse.json([
          aTerm({ id: 1, description: 'Manual abierta', enrollmentOpen: true, enrollmentOverride: 'OPEN' }),
          aTerm({ id: 2, description: 'Manual cerrada', year: 2026, enrollmentOpen: false, enrollmentOverride: 'CLOSED' }),
          aTerm({ id: 3, description: 'Por ventana', year: 2025, enrollmentOpen: true, enrollmentOverride: null }),
        ]),
      ),
    );
    renderApp('/preceptor/periodos');

    expect(await screen.findByText('Abierta (manual)')).toBeInTheDocument();
    expect(screen.getByText('Cerrada')).toBeInTheDocument();
    expect(screen.getByText('Según ventana')).toBeInTheDocument();
  });

  it('offers to remove the manual opening only when the term has an enrollment override', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/terms`, () =>
        HttpResponse.json([
          aTerm({ id: 1, description: 'Manual abierta', enrollmentOpen: true, enrollmentOverride: 'OPEN' }),
          aTerm({ id: 2, description: 'Por ventana', year: 2026, enrollmentOverride: null }),
        ]),
      ),
    );
    renderApp('/preceptor/periodos');

    await user.click(await screen.findByRole('button', { name: 'Acciones de Manual abierta 2027' }));
    expect(await screen.findByRole('menuitem', { name: 'Quitar apertura manual' })).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await user.click(screen.getByRole('button', { name: 'Acciones de Por ventana 2026' }));
    expect(await screen.findByRole('menuitem', { name: 'Abrir inscripción ahora' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: 'Quitar apertura manual' })).not.toBeInTheDocument();
  });
});

describe.each(['administrativo', 'director'])('PreceptorPeriodos as %s', (role) => {
  it('keeps the create and edit navigation under the role prefix', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json(TERMS)),
      http.get(`${API}/terms/1`, () => HttpResponse.json(TERMS[0])),
    );
    renderApp(`/${role}/periodos`);

    await user.click(await screen.findByRole('button', { name: 'Acciones de Primer cuatrimestre 2027' }));
    await user.click(await screen.findByRole('menuitem', { name: 'Editar período' }));
    expect(await screen.findByRole('link', { name: /Volver a Períodos/ })).toHaveAttribute('href', `/${role}/periodos`);
  });

  it('opens the creation form under the role prefix', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/terms`, () => HttpResponse.json(TERMS)));
    renderApp(`/${role}/periodos`);

    await user.click(await screen.findByRole('button', { name: 'Nuevo período' }));
    expect(await screen.findByRole('link', { name: /Volver a Períodos/ })).toHaveAttribute('href', `/${role}/periodos`);
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
        return HttpResponse.json(errorBody('ACTIVE_TERM_ALREADY_EXISTS'), { status: 409 });
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
    await user.type(screen.getByLabelText(/Inicio de inscripción/), '2026-12-01');
    await user.type(screen.getByLabelText(/Fin de inscripción/), '2026-12-20');
    await user.click(screen.getByRole('button', { name: 'Crear período' }));

    expect(await screen.findByText(/Ya hay un período activo de este año y tipo/)).toBeInTheDocument();
    expect(body).toMatchObject({ type: 'INTENSIVE', description: 'Verano', startDate: '2027-01-05T00:00:00' });
  });

  it('marks the enrollment window as required and blocks submit without it', async () => {
    const user = userEvent.setup();
    let calls = 0;
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json([])),
      http.post(`${API}/terms`, () => {
        calls += 1;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    renderApp('/preceptor/periodos/nuevo');

    expect(await screen.findByText(/Obligatorio: define desde cuándo/)).toBeInTheDocument();
    expect(screen.getByText(/Obligatorio: define hasta cuándo/)).toBeInTheDocument();
    expect(screen.getAllByText('*')).toHaveLength(2);

    await user.click(screen.getByRole('button', { name: 'Crear período' }));
    expect(await screen.findByText('El inicio de la inscripción es obligatorio.')).toBeInTheDocument();
    expect(screen.getByText('El fin de la inscripción es obligatorio.')).toBeInTheDocument();
    expect(calls).toBe(0);
  });

  it('shows the server TERM_ENROLLMENT_WINDOW_REQUIRED error on the enrollment fields', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json([])),
      http.post(`${API}/terms`, () =>
        HttpResponse.json(errorBody('TERM_ENROLLMENT_WINDOW_REQUIRED'), { status: 400 }),
      ),
    );
    renderApp('/preceptor/periodos/nuevo');

    await user.click(await screen.findByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Intensivo de verano' }));
    await user.type(screen.getByLabelText('Descripción'), 'Verano');
    await user.type(screen.getByLabelText('Inicio de cursada'), '2027-01-05');
    await user.type(screen.getByLabelText('Fin de cursada'), '2027-02-20');
    await user.type(screen.getByLabelText(/Inicio de inscripción/), '2026-12-01');
    await user.type(screen.getByLabelText(/Fin de inscripción/), '2026-12-20');
    await user.click(screen.getByRole('button', { name: 'Crear período' }));

    expect(await screen.findAllByText('La ventana de inscripción es obligatoria: indicá el inicio y el fin.')).toHaveLength(2);
    expect(screen.queryByText('Los datos del período no son válidos. Revisá las fechas.')).not.toBeInTheDocument();
  });

  it('shows the server TERM_DATES_REQUIRED error on the course date fields', async () => {
    const user = userEvent.setup();
    server.use(
      http.get(`${API}/terms`, () => HttpResponse.json([])),
      http.post(`${API}/terms`, () => HttpResponse.json(errorBody('TERM_DATES_REQUIRED'), { status: 400 })),
    );
    renderApp('/preceptor/periodos/nuevo');

    await user.click(await screen.findByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Intensivo de verano' }));
    await user.type(screen.getByLabelText('Descripción'), 'Verano');
    await user.type(screen.getByLabelText('Inicio de cursada'), '2027-01-05');
    await user.type(screen.getByLabelText('Fin de cursada'), '2027-02-20');
    await user.type(screen.getByLabelText(/Inicio de inscripción/), '2026-12-01');
    await user.type(screen.getByLabelText(/Fin de inscripción/), '2026-12-20');
    await user.click(screen.getByRole('button', { name: 'Crear período' }));

    expect(await screen.findAllByText('Las fechas de cursada son obligatorias: indicá el inicio y el fin.')).toHaveLength(2);
    expect(screen.queryByText('Los datos del período no son válidos. Revisá las fechas.')).not.toBeInTheDocument();
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

  it('shows a retryable load error when the term to edit cannot be fetched', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/terms`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(TERMS);
      }),
    );
    const user = userEvent.setup();
    renderApp('/preceptor/periodos/1/editar');

    expect(await screen.findByText('No pudimos cargar el período')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { name: 'Editar período' })).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar el período')).not.toBeInTheDocument();
  });
});
