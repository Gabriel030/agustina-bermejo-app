import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { Home, Users } from 'lucide-react';
import { MobileMenu } from '@/components/MobileMenu';
import { ThemeProvider } from '@/context/ThemeContext';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Perez' }, logout: vi.fn() }),
}));

const ITEMS = [
  { to: '/director/inicio', label: 'Inicio', icon: Home },
  { to: '/director/alumnos', label: 'Alumnos', icon: Users },
  { to: '/director/personal', label: 'Personal', icon: Users },
];

const GROUPS = [
  { id: 'main', label: null, items: [ITEMS[0]] },
  { id: 'people', label: 'Personas', items: [ITEMS[1]] },
  { id: 'staff', label: 'Equipo', items: [ITEMS[2]] },
];

async function openDrawer() {
  render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/director/alumnos']}>
        <MobileMenu titulo="Director" items={ITEMS} groups={GROUPS} />
      </MemoryRouter>
    </ThemeProvider>,
  );
  await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }));
}

afterEach(() => {
  delete document.documentElement.dataset.design;
});

describe('MobileMenu', () => {
  it('starts with only the group of the current page expanded', async () => {
    document.documentElement.dataset.design = 'b';
    await openDrawer();

    expect(screen.getByRole('button', { name: 'Personas' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Equipo' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('link', { name: 'Personal' })).not.toBeInTheDocument();
  });

  it('expands a collapsed group when its heading is pressed', async () => {
    document.documentElement.dataset.design = 'b';
    await openDrawer();

    await userEvent.click(screen.getByRole('button', { name: 'Equipo' }));

    expect(screen.getByRole('link', { name: 'Personal' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Equipo' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('lists every item flat with design vl', async () => {
    document.documentElement.dataset.design = 'vl';
    await openDrawer();

    expect(screen.getByRole('link', { name: 'Personal' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Personas' })).not.toBeInTheDocument();
  });

  it('closes with Escape', async () => {
    document.documentElement.dataset.design = 'b';
    await openDrawer();

    await userEvent.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
