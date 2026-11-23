import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { useGrillaDeMaterias } from '@/features/evaluation/hooks/useGrillaDeMaterias';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { TabNotas } from './TabNotas';

vi.mock('@/features/evaluation/hooks/useGrillaDeMaterias', () => ({ useGrillaDeMaterias: vi.fn() }));

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

describe('TabNotas — load failure', () => {
  beforeEach(() => {
    vi.mocked(useGrillaDeMaterias).mockReset();
  });

  it('shows the error state inside the table and retries the load', async () => {
    const recargar = vi.fn();
    vi.mocked(useGrillaDeMaterias).mockReturnValue(grid({ fallo: true, recargar }));
    const user = userEvent.setup();
    renderWithProviders(<TabNotas carreraId={5} />);

    expect(screen.getByRole('alert')).toHaveTextContent('No pudimos cargar tus notas');
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(recargar).toHaveBeenCalledTimes(1);
  });

  it('does not show the error state when the grid loads fine', () => {
    vi.mocked(useGrillaDeMaterias).mockReturnValue(grid());
    renderWithProviders(<TabNotas carreraId={5} />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('TabNotas — corrected grades', () => {
  it('shows the previous score and a Corregida tag only for a corrected grade', async () => {
    vi.mocked(useGrillaDeMaterias).mockReturnValue(
      grid({
        pagina: [{ key: 'm1', subjectCode: 'AM1', subjectName: 'Análisis I', curricularYear: 1, estado: 'en-curso', inscripcion: { id: 9 } }],
        anios: [1],
      }),
    );
    server.use(
      http.get('http://localhost:8080/me/section-enrollments/9/grade-items', () =>
        HttpResponse.json([
          { id: 1, enrollmentId: 9, examBoardId: 1, type: 'EXAM', sequenceNumber: 1, score: 7, date: '2026-09-16T10:00:00', correction: { previousScore: 6, reason: 'Se sumó el ejercicio 4', correctedAt: '2026-09-22T15:40:00' } },
          { id: 2, enrollmentId: 9, examBoardId: 1, type: 'EXAM', sequenceNumber: 2, score: 8, date: '2026-10-16T10:00:00', correction: null },
        ]),
      ),
    );
    renderWithProviders(<TabNotas carreraId={5} />);

    expect(await screen.findByText('antes 6')).toBeInTheDocument();
    expect(screen.getAllByText('Corregida')).toHaveLength(1);
    expect(screen.getByText('8')).toBeInTheDocument();
  });
});

describe('TabNotas — requests per opening', () => {
  it('asks for the grade items once per enrolled subject of the page and nothing for the rest', async () => {
    const rows = Array.from({ length: 10 }, (_, index) => ({
      key: `m${index}`,
      subjectCode: `M${index}`,
      subjectName: `Materia ${index}`,
      curricularYear: 1,
      estado: index < 7 ? 'en-curso' : 'disponible',
      inscripcion: index < 7 ? { id: 100 + index } : null,
    }));
    vi.mocked(useGrillaDeMaterias).mockReturnValue(grid({ pagina: rows, anios: [1] }));
    const requested = [];
    server.use(
      http.get('http://localhost:8080/me/section-enrollments/:id/grade-items', ({ params }) => {
        requested.push(params.id);
        return HttpResponse.json([]);
      }),
    );
    renderWithProviders(<TabNotas carreraId={5} />);

    await screen.findByText('Materia 0');
    await vi.waitFor(() => expect(requested).toHaveLength(7));
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(new Set(requested).size).toBe(7);
    expect(requested).toHaveLength(7);
  });
});
