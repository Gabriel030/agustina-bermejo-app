import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, Route, Routes } from 'react-router-dom';
import { Home } from 'lucide-react';
import { renderWithProviders } from '@/test/renderWithProviders';
import { ThemeProvider } from '@/context/ThemeContext';
import { RoleShell } from '@/layouts/RoleShell';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Perez', roles: ['DIRECTOR'] }, roles: ['DIRECTOR'], logout: vi.fn() }),
}));

const ITEMS = [{ to: '/director/inicio', label: 'Inicio', icon: Home }];

function renderShell(route = '/director/inicio') {
  return renderWithProviders(
    <ThemeProvider>
      <RoleShell titulo="Director" items={ITEMS} />
    </ThemeProvider>,
    { route },
  );
}

describe('RoleShell', () => {
  it('shell wrapper reads the spacing variables', () => {
    const { container } = renderShell();

    expect(container.firstChild).toHaveClass('p-(--shell-pad)', 'gap-(--shell-gap)');
  });

  it('main carries its own padding', () => {
    renderShell();

    expect(screen.getByRole('main')).toHaveClass('px-4', 'py-5', 'sm:px-8', 'sm:py-6');
  });

  it('main reserves scroll padding so focused fields stay clear of the sticky action bar', () => {
    renderShell();

    expect(screen.getByRole('main')).toHaveClass('scroll-pb-32');
  });

  it('mobile header shows the page title', () => {
    renderShell();

    const title = screen.getAllByText('Inicio').find((node) => node.className.includes('lg:hidden'));
    expect(title).toBeDefined();
  });

  it('mobile header uses the singular closure title on the closure sheet', () => {
    const items = [{ to: '/administrativo/cierres-de-comision', label: 'Cierres de comisión', icon: Home }];
    renderWithProviders(
      <ThemeProvider>
        <RoleShell titulo="Administrativo" items={items} />
      </ThemeProvider>,
      { route: '/administrativo/cierre-de-comision/5' },
    );

    const title = screen.getAllByText('Cierre de comisión').find((node) => node.className.includes('lg:hidden'));
    expect(title).toBeDefined();
  });

  it('theme toggle moves into the drawer on mobile', async () => {
    renderShell();

    await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }));

    expect(await screen.findByRole('button', { name: 'Tema oscuro' })).toBeInTheDocument();
  });

  it('moves focus to the page heading after navigating', async () => {
    renderWithProviders(
      <ThemeProvider>
        <Routes>
          <Route element={<RoleShell titulo="Director" items={ITEMS} />}>
            <Route path="/director/inicio" element={<Link to="/director/alumnos">Ir</Link>} />
            <Route path="/director/alumnos" element={<h1>Alumnos</h1>} />
          </Route>
        </Routes>
      </ThemeProvider>,
      { route: '/director/inicio' },
    );

    await userEvent.click(screen.getByRole('link', { name: 'Ir' }));

    const heading = await screen.findByRole('heading', { name: 'Alumnos' });
    await waitFor(() => expect(heading).toHaveFocus());
  });
});
