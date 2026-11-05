import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CursoDetalle } from './CursoDetalle';

import { toast } from 'sonner';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

const COURSE = { id: 5, name: 'Primeros auxilios', status: 'ACTIVE', visibleInCatalog: true, capacity: 20 };

function mount({ onDeactivate, onCatalog } = {}) {
  server.use(
    http.get(`${API}/courses/5`, () => HttpResponse.json(COURSE)),
    http.get(`${API}/courses/5/subjects`, () => HttpResponse.json([])),
    http.post(`${API}/courses/5/deactivate`, onDeactivate ?? (() => new HttpResponse(null, { status: 204 }))),
    http.put(`${API}/courses/5/catalog-visibility`, onCatalog ?? (() => HttpResponse.json(COURSE))),
  );
  renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/cursos/:id" element={<CursoDetalle />} />
    </Routes>,
    { route: '/administrativo/gestion-academica/cursos/5' },
  );
}

beforeEach(() => vi.clearAllMocks());

describe('CursoDetalle confirmations', () => {
  it('asks before deactivating, cancel does not call the API and confirm does', async () => {
    const onDeactivate = vi.fn(() => new HttpResponse(null, { status: 204 }));
    mount({ onDeactivate });
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: 'Desactivar' }));
    let dialog = await screen.findByRole('alertdialog', { name: 'Desactivar curso' });
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

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith('No se pudo cambiar el estado del curso.'));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });

  it('asks before retiring from the catalog and only calls the API on confirm', async () => {
    const onCatalog = vi.fn(() => HttpResponse.json(COURSE));
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
