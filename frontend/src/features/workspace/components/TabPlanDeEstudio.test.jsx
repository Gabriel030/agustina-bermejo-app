import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { useGrillaDeMaterias } from '@/hooks/useGrillaDeMaterias';
import { TabPlanDeEstudio } from './TabPlanDeEstudio';

vi.mock('@/hooks/useGrillaDeMaterias', () => ({ useGrillaDeMaterias: vi.fn() }));

function grid(overrides) {
  return {
    pagina: [],
    page: 1,
    setPage: vi.fn(),
    totalPages: 1,
    cargando: false,
    fallo: false,
    recargar: vi.fn(),
    estado: 'todas',
    cambiarEstado: vi.fn(),
    anio: 'todos',
    cambiarAnio: vi.fn(),
    anios: [],
    toggleOrden: vi.fn(),
    estadoDe: () => null,
    hayOrdenExplicito: false,
    ...overrides,
  };
}

describe('TabPlanDeEstudio — load failure', () => {
  beforeEach(() => {
    vi.mocked(useGrillaDeMaterias).mockReset();
  });

  it('shows the error state inside the table and retries the load', async () => {
    const recargar = vi.fn();
    vi.mocked(useGrillaDeMaterias).mockReturnValue(grid({ fallo: true, recargar }));
    const user = userEvent.setup();
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos cargar el plan de estudio');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(recargar).toHaveBeenCalledTimes(1);
  });

  it('does not show the error state when the grid loads fine', () => {
    vi.mocked(useGrillaDeMaterias).mockReturnValue(grid());
    renderWithProviders(<TabPlanDeEstudio carreraId={5} />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
