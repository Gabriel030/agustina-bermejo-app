import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraDetalle } from './CarreraDetalle';

import { toast } from 'sonner';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

const PROGRAM = { id: 9, name: 'Enfermería', status: 'ACTIVE', totalYears: 3, visibleInCatalog: true };

function mount({ onDeactivate, onCatalog } = {}) {
  server.use(
    http.get(`${API}/programs/9`, () => HttpResponse.json(PROGRAM)),
    http.get(`${API}/programs/9/subjects`, () => HttpResponse.json([])),
    http.get(`${API}/elective-groups`, () => HttpResponse.json([])),
    http.get(`${API}/programs/9/elective-groups`, () => HttpResponse.json([])),
    http.get(`${API}/subjects`, () => HttpResponse.json([])),
    http.get(`${API}/terms`, () => HttpResponse.json([])),
    http.post(`${API}/programs/9/deactivate`, onDeactivate ?? (() => new HttpResponse(null, { status: 204 }))),
    http.put(`${API}/programs/9/catalog-visibility`, onCatalog ?? (() => HttpResponse.json(PROGRAM))),
  );
  renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/carreras/:id" element={<CarreraDetalle />} />
    </Routes>,
    { route: '/administrativo/gestion-academica/carreras/9' },
  );
}

beforeEach(() => vi.clearAllMocks());

describe('CarreraDetalle confirmations', () => {
  it('asks before deactivating, cancel does not call the API and confirm does', async () => {
    const onDeactivate = vi.fn(() => new HttpResponse(null, { status: 204 }));
    mount({ onDeactivate });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Desactivar' }));
    let dialog = await screen.findByRole('alertdialog', { name: 'Desactivar carrera' });
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(onDeactivate).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Desactivar' }));
    dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Desactivar' }));
    await waitFor(() => expect(onDeactivate).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
  });

  it('keeps the dialog open and shows the error when deactivating fails', async () => {
    mount({ onDeactivate: () => HttpResponse.json(errorBody('CONFLICT'), { status: 409 }) });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Desactivar' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Desactivar' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('No se pudo cambiar el estado de la carrera.'));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });

  it('asks before retiring from the catalog and only calls the API on confirm', async () => {
    const onCatalog = vi.fn(() => HttpResponse.json(PROGRAM));
    mount({ onCatalog });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Retirar del catálogo' }));
    const dialog = await screen.findByRole('alertdialog', { name: 'Retirar del catálogo' });
    expect(onCatalog).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole('button', { name: 'Retirar' }));
    await waitFor(() => expect(onCatalog).toHaveBeenCalledTimes(1));
  });

  it('shows the error when retiring from the catalog fails', async () => {
    mount({ onCatalog: () => HttpResponse.json(errorBody('CONFLICT'), { status: 409 }) });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Retirar del catálogo' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Retirar' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('No se pudo cambiar la visibilidad en el catálogo.'));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });
});
