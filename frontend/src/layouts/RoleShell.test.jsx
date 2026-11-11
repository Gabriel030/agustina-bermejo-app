import { afterEach, describe, expect, it, vi } from 'vitest';
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

afterEach(() => {
  delete document.documentElement.dataset.design;
});

describe('RoleShell', () => {
  it('shell wrapper reads the spacing variables', () => {
    const { container } = renderShell();

    expect(container.firstChild).toHaveClass('p-(--shell-pad)', 'gap-(--shell-gap)');
  });

  it('main carries its own padding only for design b', () => {
    renderShell();

    expect(screen.getByRole('main')).toHaveClass('design-b:px-4', 'design-b:py-5');
  });

  it('mobile header shows the page title with design b', () => {
    document.documentElement.dataset.design = 'b';
    renderShell();

    const title = screen.getAllByText('Inicio').find((node) => node.className.includes('lg:hidden'));
    expect(title).toBeDefined();
  });

  it('mobile header shows the role with design vl', () => {
    document.documentElement.dataset.design = 'vl';
    renderShell();

    const title = screen.getAllByText('Director').find((node) => node.className.includes('lg:hidden'));
    expect(title).toBeDefined();
  });

  it('theme toggle moves into the drawer on mobile with design b', async () => {
    document.documentElement.dataset.design = 'b';
    renderShell();

    await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }));

    expect(await screen.findByRole('button', { name: 'Tema oscuro' })).toBeInTheDocument();
  });

  it('drawer has no theme entry with design vl', async () => {
    document.documentElement.dataset.design = 'vl';
    renderShell();

    await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }));

    await waitFor(() => expect(screen.getByRole('dialog')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: 'Tema oscuro' })).not.toBeInTheDocument();
  });

  it.each([
    ['b', true],
    ['vl', false],
  ])('focus on the heading after navigating with design %s is %s', async (design, expected) => {
    document.documentElement.dataset.design = design;
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
    await waitFor(() => expect(heading === document.activeElement).toBe(expected));
  });
});
