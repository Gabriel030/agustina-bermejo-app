import { afterEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Breadcrumb } from '@/components/Breadcrumb';
import { restoreViewport, useCompactViewport } from '@/test/compactViewport';

afterEach(restoreViewport);

const fourLevels = [
  { label: 'Docente', to: '/docente' },
  { label: 'Mis comisiones', to: '/docente/comisiones' },
  { label: 'Comisión A', to: '/docente/comisiones/1' },
  { label: 'Avisos' },
];
const threeLevels = fourLevels.slice(1);

function renderBreadcrumb(items) {
  return render(
    <MemoryRouter>
      <Routes>
        <Route path="*" element={<Breadcrumb items={items} />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('Breadcrumb', () => {
  it('shows every level on desktop', () => {
    renderBreadcrumb(fourLevels);
    expect(screen.getByRole('navigation', { name: 'Ruta de navegación' })).toBeInTheDocument();
    for (const item of fourLevels) expect(screen.getByText(item.label)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mostrar niveles anteriores' })).not.toBeInTheDocument();
  });

  it('does not collapse three levels on mobile', () => {
    useCompactViewport();
    renderBreadcrumb(threeLevels);
    expect(screen.queryByRole('button', { name: 'Mostrar niveles anteriores' })).not.toBeInTheDocument();
    expect(screen.getByText('Mis comisiones')).toBeInTheDocument();
  });

  it('collapses four levels on mobile keeping parent and current page', () => {
    useCompactViewport();
    renderBreadcrumb(fourLevels);
    expect(screen.getByRole('button', { name: 'Mostrar niveles anteriores' })).toBeInTheDocument();
    expect(screen.getByText('Comisión A')).toBeInTheDocument();
    expect(screen.getByText('Avisos')).toBeInTheDocument();
    expect(screen.queryByText('Docente')).not.toBeInTheDocument();
  });

  it('lists hidden levels in the menu and navigates', async () => {
    useCompactViewport();
    const user = userEvent.setup();
    renderBreadcrumb(fourLevels);
    await user.click(screen.getByRole('button', { name: 'Mostrar niveles anteriores' }));
    expect(await screen.findByRole('menuitem', { name: 'Docente' })).toHaveAttribute('href', '/docente');
    expect(screen.getByRole('menuitem', { name: 'Mis comisiones' })).toBeInTheDocument();
    await user.click(screen.getByRole('menuitem', { name: 'Mis comisiones' }));
    expect(screen.queryByRole('menuitem', { name: 'Mis comisiones' })).not.toBeInTheDocument();
  });

  it('accepts JSX labels when collapsing', async () => {
    useCompactViewport();
    const user = userEvent.setup();
    renderBreadcrumb([
      { label: <span data-testid="skeleton-a">cargando</span>, to: '/a' },
      { label: 'B', to: '/b' },
      { label: 'C', to: '/c' },
      { label: <span data-testid="skeleton-d">actual</span> },
    ]);
    expect(screen.getByTestId('skeleton-d')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Mostrar niveles anteriores' }));
    expect(await screen.findByTestId('skeleton-a')).toBeInTheDocument();
  });
});
