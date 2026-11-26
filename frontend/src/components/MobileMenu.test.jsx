import { describe, expect, it, vi } from 'vitest';
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

const SECTIONED_GROUPS = [
  {
    id: 'academic',
    label: 'Académico',
    items: [ITEMS[1], ITEMS[2]],
    sections: [
      { id: 'one', label: 'Primera sección', items: [ITEMS[1]] },
      { id: 'two', label: 'Segunda sección', items: [ITEMS[2]] },
    ],
  },
];

async function openDrawer({ withGroups = true, groups = GROUPS } = {}) {
  render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/director/alumnos']}>
        <MobileMenu titulo="Director" items={ITEMS} groups={withGroups ? groups : undefined} />
      </MemoryRouter>
    </ThemeProvider>,
  );
  await userEvent.click(screen.getByRole('button', { name: 'Abrir menú' }));
}

describe('MobileMenu', () => {
  it('shows each section of a grid group as a subtitle with its items below', async () => {
    await openDrawer({ groups: SECTIONED_GROUPS });


    expect(screen.getByRole('button', { name: 'Académico' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByText('Primera sección')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Segunda sección' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Personal' })).toBeInTheDocument();
  });

  it('starts with only the group of the current page expanded', async () => {
    await openDrawer();

    expect(screen.getByRole('button', { name: 'Personas' })).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getByRole('button', { name: 'Equipo' })).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByRole('link', { name: 'Personal' })).not.toBeInTheDocument();
  });

  it('expands a collapsed group when its heading is pressed', async () => {
    await openDrawer();

    await userEvent.click(screen.getByRole('button', { name: 'Equipo' }));

    expect(screen.getByRole('link', { name: 'Personal' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Equipo' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('lists every item flat when the menu has no groups', async () => {
    await openDrawer({ withGroups: false });

    expect(screen.getByRole('link', { name: 'Personal' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Personas' })).not.toBeInTheDocument();
  });

  it('closes with Escape', async () => {
    await openDrawer();

    await userEvent.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
});
