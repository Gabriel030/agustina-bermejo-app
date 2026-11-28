import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
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

    expect(screen.getByRole('main')).toHaveClass('px-4', 'py-5', 'sm:py-6');
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

  it('keeps the theme toggle out of the drawer', async () => {
    renderShell();

    await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(within(screen.getByRole('dialog')).queryByRole('button', { name: /Tema/ })).not.toBeInTheDocument();
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
  it('uses the side menu by default', () => {
    renderShell();

    expect(screen.queryByRole('navigation', { name: 'Principal' })).not.toBeInTheDocument();
    expect(screen.getAllByRole('navigation').length).toBeGreaterThan(0);
  });

  it('replaces the side menu with the top bar when the role asks for it', () => {
    const { container } = renderWithProviders(
      <ThemeProvider>
        <RoleShell titulo="Director" items={ITEMS} topNav />
      </ThemeProvider>,
      { route: '/director/inicio' },
    );

    expect(screen.getByRole('navigation', { name: 'Principal' })).toBeInTheDocument();
    expect(container.firstChild).not.toHaveClass('lg:flex-row');
    expect(screen.getByRole('button', { name: 'Abrir menú' })).toBeInTheDocument();
  });

  describe('top bar width option', () => {
    function renderTopBar(topNav) {
      return renderWithProviders(
        <ThemeProvider>
          <RoleShell titulo="Preceptor" items={ITEMS} topNav={topNav} />
        </ThemeProvider>,
        { route: '/director/inicio' },
      );
    }

    it('standard top bar appears from lg and the drawer header disappears from lg', () => {
      renderTopBar(true);

      expect(screen.getByRole('navigation', { name: 'Principal' }).parentElement).toHaveClass('lg:flex');
      expect(screen.getByRole('button', { name: 'Abrir menú' })).toHaveClass('lg:hidden');
    });

    it('wide top bar appears from 1220px and keeps the drawer header until then', () => {
      renderTopBar('wide');

      const bar = screen.getByRole('navigation', { name: 'Principal' }).parentElement;
      expect(bar).toHaveClass('min-[1220px]:flex');
      expect(bar).not.toHaveClass('lg:flex');
      const trigger = screen.getByRole('button', { name: 'Abrir menú' });
      expect(trigger).toHaveClass('min-[1220px]:hidden');
      expect(trigger).not.toHaveClass('lg:hidden');
      expect(trigger.closest('.pt-4.px-4')).toHaveClass('min-[1220px]:hidden');
    });

    it('wide top bar collapses the brand text and the user name until 2xl', () => {
      renderTopBar('wide');

      const bar = screen.getByRole('navigation', { name: 'Principal' }).parentElement;
      expect(bar.querySelector('[class*="max-2xl"]')).not.toBeNull();
      expect(within(bar).getAllByText('Ana Perez').some((node) => node.className.includes('2xl:inline'))).toBe(true);
    });

    it('exposes a single main navigation', () => {
      renderTopBar('wide');

      expect(screen.getAllByRole('navigation', { name: 'Principal' })).toHaveLength(1);
    });
  });
});
