import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DocenteMisComisiones } from './DocenteMisComisiones';

const API = 'http://localhost:8080';

const CICLO_2026 = { termStartDate: '2026-03-02T00:00:00', termEndDate: '2026-12-11T00:00:00' };

function config(id, status) {
  return { id, subjectSectionId: id, subjectName: `Materia ${id}`, sectionName: 'Comisión A', status, instructors: [], ...CICLO_2026 };
}

function tarjeta(label) {
  return screen.getByText(label).closest('div').parentElement;
}

describe('DocenteMisComisiones', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 3, 10, 0));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('no mezcla comisiones de ciclos anteriores en la bandeja ni en el KPI del período', async () => {
    server.use(
      http.get(`${API}/me/section-evaluation-configs`, () =>
        HttpResponse.json([config(40, 'DRAFT'), config(41, 'PENDING_APPROVAL'), config(42, 'APPROVED')]),
      ),
      http.get(`${API}/me/teaching-sections`, () =>
        HttpResponse.json([
          { id: 40, type: 'PROGRAM', subjectName: 'Materia 40', sectionName: 'Comisión A', ...CICLO_2026 },
          { id: 1, type: 'PROGRAM', subjectName: 'Pedagogía', sectionName: 'Comisión A', termStartDate: '2024-03-04T00:00:00', termEndDate: '2024-12-13T00:00:00' },
          { id: 1, type: 'COURSE', subjectName: 'Fundamentos de Programación', sectionName: 'Turno noche', ...CICLO_2026 },
        ]),
      ),
    );

    renderWithProviders(<DocenteMisComisiones />);

    expect(await screen.findByText('Bandeja de aprobación')).toBeInTheDocument();
    expect(within(tarjeta('Comisiones y cursos este período')).getByText('4')).toBeInTheDocument();
    expect(within(tarjeta('Pendientes de completar/aprobar')).getByText('2')).toBeInTheDocument();
    expect(within(tarjeta('Días para el vencimiento más próximo')).getByText('Vencido')).toBeInTheDocument();
    const cerradas = screen.getByText('Cerradas').closest('div').parentElement;
    expect(within(cerradas).getByText(/Pedagogía/)).toBeInTheDocument();
  });

  it('si la API falla muestra un error y no el estado vacío', async () => {
    server.use(
      http.get(`${API}/me/section-evaluation-configs`, () => new HttpResponse(null, { status: 500 })),
      http.get(`${API}/me/teaching-sections`, () => HttpResponse.json([])),
    );

    renderWithProviders(<DocenteMisComisiones />);

    expect(await screen.findByText('No pudimos cargar tus comisiones')).toBeInTheDocument();
    expect(screen.queryByText('Todavía no tenés comisiones asignadas')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
