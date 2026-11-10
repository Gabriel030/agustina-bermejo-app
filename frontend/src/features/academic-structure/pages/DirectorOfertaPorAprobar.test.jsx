import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorOfertaPorAprobar } from '@/features/academic-structure/pages/DirectorOfertaPorAprobar';

const API = 'http://localhost:8080';

const cycles = [
  { id: 1, status: 'PENDING_APPROVAL', termName: '1.er cuatrimestre 2027', programId: 3, programName: 'Tecnicatura en Administración', submittedByName: 'Marina Fernández', submittedAt: '2026-11-03T10:00:00', sectionCount: 9 },
  { id: 3, status: 'PENDING_APPROVAL', termName: 'Verano 2027', courseId: 5, courseName: 'Curso de Excel Avanzado', submittedByName: 'Lucas Giménez', submittedAt: '2026-11-06T10:00:00', sectionCount: 2 },
];

describe('DirectorOfertaPorAprobar', () => {
  it('lists the pending cycles asking only for PENDING_APPROVAL', async () => {
    let requestedStatus = null;
    server.use(
      http.get(`${API}/academic-offering-cycles`, ({ request }) => {
        requestedStatus = new URL(request.url).searchParams.get('status');
        return HttpResponse.json(cycles);
      }),
    );
    renderWithProviders(<DirectorOfertaPorAprobar />);

    expect(await screen.findByText('Tecnicatura en Administración')).toBeInTheDocument();
    expect(screen.getByText('Curso de Excel Avanzado')).toBeInTheDocument();
    expect(screen.getByText('Marina Fernández')).toBeInTheDocument();
    expect(screen.getByText('Curso')).toBeInTheDocument();
    expect(requestedStatus).toBe('PENDING_APPROVAL');
  });

  it('shows an empty state when nothing is pending', async () => {
    server.use(http.get(`${API}/academic-offering-cycles`, () => HttpResponse.json([])));
    renderWithProviders(<DirectorOfertaPorAprobar />);

    expect(await screen.findByText('No hay ciclos esperando tu decisión')).toBeInTheDocument();
  });

  it('shows a retryable error when the list fails', async () => {
    server.use(http.get(`${API}/academic-offering-cycles`, () => new HttpResponse(null, { status: 500 })));
    renderWithProviders(<DirectorOfertaPorAprobar />);

    expect(await screen.findByText('No pudimos cargar la oferta por aprobar.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
