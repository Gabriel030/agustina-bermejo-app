import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Route, Routes, Link } from 'react-router-dom';
import { screen, waitFor } from '@testing-library/react';
import { renderWithProviders } from '@/test/renderWithProviders';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@/context/ThemeContext';
import { RoleShell } from '@/layouts/RoleShell';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: ['DIRECTOR'] }, roles: ['DIRECTOR'], logout: vi.fn() }),
}));

function Broken() {
  throw new Error('boom');
}

function Healthy() {
  return (
    <div>
      Pantalla sana <Link to="/d/rota">ir a rota</Link>
    </div>
  );
}

describe('RoleShell error boundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  it('showsTheFallbackKeepsTheSidebarAndRecoversOnNavigation', async () => {
    renderWithProviders(
      <ThemeProvider>
          <Routes>
            <Route path="/d" element={<RoleShell titulo="Dirección" items={[{ to: '/d/sana', label: 'Sana' }]} />}>
              <Route path="rota" element={<Broken />} />
              <Route path="sana" element={<Healthy />} />
            </Route>
          </Routes>
      </ThemeProvider>,
      { route: '/d/rota' },
    );

    expect(await screen.findByText('Algo salió mal')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Sana' }).length).toBeGreaterThan(0);

    await userEvent.click(screen.getAllByRole('link', { name: 'Sana' })[0]);

    expect(await screen.findByText(/Pantalla sana/)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('Algo salió mal')).not.toBeInTheDocument());
  });
});
