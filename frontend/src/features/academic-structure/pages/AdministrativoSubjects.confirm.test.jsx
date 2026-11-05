import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AdministrativoSubjects } from './AdministrativoSubjects';

import { toast } from 'sonner';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const API = 'http://localhost:8080';

vi.setConfig({ testTimeout: 20000 });

const PUBLISHED = [{ id: 9, name: 'Enfermería', status: 'ACTIVE', visibleInCatalog: true }];

async function mountOffer(kind, tab, onRetire) {
  server.use(
    http.get(`${API}/subjects`, () => HttpResponse.json([])),
    http.get(`${API}/programs`, () => HttpResponse.json(kind === 'programs' ? PUBLISHED : [])),
    http.get(`${API}/courses`, () => HttpResponse.json(kind === 'courses' ? PUBLISHED : [])),
    http.put(`${API}/${kind}/9/catalog-visibility`, onRetire),
  );
  const user = userEvent.setup();
  renderWithProviders(<AdministrativoSubjects />, { route: '/administrativo/gestion-academica?section=oferta' });
  if (tab) await user.click(await screen.findByRole('tab', { name: tab }));
  return user;
}

beforeEach(() => vi.clearAllMocks());

describe.each([
  ['programs', null, 'Enfermería', 'No se pudo retirar la carrera del catálogo.'],
  ['courses', 'Cursos publicados', 'Enfermería', 'No se pudo retirar el curso del catálogo.'],
])('AdministrativoSubjects retire %s from the catalog', (kind, tab, name, failure) => {
  it('asks first, cancel does not call the API and confirm does', async () => {
    const onRetire = vi.fn(() => HttpResponse.json(PUBLISHED[0]));
    const user = await mountOffer(kind, tab, onRetire);

    await user.click(await screen.findByRole('button', { name: 'Retirar del catálogo' }));
    let dialog = await screen.findByRole('alertdialog', { name: 'Retirar del catálogo' });
    expect(within(dialog).getByText(new RegExp(name))).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar' }));
    await waitFor(() => expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument());
    expect(onRetire).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Retirar del catálogo' }));
    dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Retirar' }));
    await waitFor(() => expect(onRetire).toHaveBeenCalledTimes(1));
  });

  it('shows the error and keeps the dialog open when the request fails', async () => {
    const user = await mountOffer(kind, tab, () => HttpResponse.json(errorBody('CONFLICT'), { status: 409 }));

    await user.click(await screen.findByRole('button', { name: 'Retirar del catálogo' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Retirar' }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(failure));
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });
});
