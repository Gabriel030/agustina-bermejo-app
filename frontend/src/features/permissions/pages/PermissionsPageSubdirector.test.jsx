import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { permissionCatalog, permissionGridReadOnly } from '@/test/fixtures/permissions';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PermissionsPage } from './PermissionsPage';

const API = 'http://localhost:8080';

function serveReadOnlyGrid() {
  server.use(
    http.get(`${API}/permissions/catalog`, () => HttpResponse.json(permissionCatalog())),
    http.get(`${API}/permissions/grid`, () => HttpResponse.json(permissionGridReadOnly())),
  );
}

describe('PermissionsPage for a subdirector (read-only grid)', () => {
  it('shows the notice that only the Director can change permissions', async () => {
    serveReadOnlyGrid();
    renderWithProviders(<PermissionsPage />);

    expect(await screen.findByText('Estás viendo la grilla en modo lectura.')).toBeInTheDocument();
    expect(screen.getByText(/Solo el Director puede modificar los permisos\./)).toBeInTheDocument();
  });

  it('offers no edit controls and hides the discrepancies tab', async () => {
    serveReadOnlyGrid();
    renderWithProviders(<PermissionsPage />);

    await screen.findByText('Estás viendo la grilla en modo lectura.');
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: /Discrepancias/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Revisar y guardar' })).not.toBeInTheDocument();
  });
});
