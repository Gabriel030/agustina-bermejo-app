import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorOfertaAcademica } from './PreceptorOfertaAcademica';

const API = 'http://localhost:8080';

function useOffers({ programs = [], courses = [], cycles }) {
  const cycleRequests = [];
  server.use(
    http.get(`${API}/programs`, () => HttpResponse.json(programs)),
    http.get(`${API}/courses`, () => HttpResponse.json(courses)),
    http.get(`${API}/academic-offering-cycles`, ({ request }) => {
      cycleRequests.push(new URL(request.url).search);
      return cycles ? HttpResponse.json(cycles) : new HttpResponse(null, { status: 500 });
    }),
  );
  return cycleRequests;
}

describe('PreceptorOfertaAcademica requests', () => {
  it('e31 shows the most recently created active cycle of a program', async () => {
    useOffers({
      programs: [{ id: 1, name: 'Tecnicatura A', totalYears: 3 }],
      cycles: [
        { id: 10, programId: 1, courseId: null, status: 'APPROVED', createdAt: '2026-01-10T10:00:00Z' },
        { id: 11, programId: 1, courseId: null, status: 'DRAFT', createdAt: '2026-06-10T10:00:00Z' },
      ],
    });
    renderWithProviders(<PreceptorOfertaAcademica />, { route: '/director/oferta-academica' });
    expect(await screen.findByText('En construcción')).toBeInTheDocument();
    expect(screen.queryByText('Aprobado')).not.toBeInTheDocument();
  });

  it('e31 shows the most recently created active cycle of a course', async () => {
    const cycleRequests = useOffers({
      courses: [{ id: 5, name: 'Curso X', durationMonths: 6 }],
      cycles: [
        { id: 20, programId: null, courseId: 5, status: 'DRAFT', createdAt: '2026-06-10T10:00:00Z' },
        { id: 21, programId: null, courseId: 5, status: 'APPROVED', createdAt: '2026-01-10T10:00:00Z' },
        { id: 22, programId: null, courseId: 5, status: 'PENDING_APPROVAL', createdAt: '2026-08-10T10:00:00Z' },
      ],
    });
    renderWithProviders(<PreceptorOfertaAcademica />, { route: '/director/oferta-academica?tab=cursos' });
    expect(await screen.findByText('En revisión')).toBeInTheDocument();
    expect(cycleRequests).toEqual(['']);
  });

  it('e31 marks the cycle as unavailable when the single cycles request fails', async () => {
    useOffers({ programs: [{ id: 1, name: 'Tecnicatura A', totalYears: 3 }] });
    renderWithProviders(<PreceptorOfertaAcademica />, { route: '/director/oferta-academica' });
    expect(await screen.findByText('Ciclo no disponible')).toBeInTheDocument();
  });

  it('e31 loads every cycle badge with a single cycles request', async () => {
    const cycleRequests = [];
    server.use(
      http.get(`${API}/programs`, () => HttpResponse.json([
        { id: 1, name: 'Tecnicatura A', totalYears: 3 },
        { id: 2, name: 'Tecnicatura B', totalYears: 2 },
        { id: 3, name: 'Tecnicatura C', totalYears: 2 },
      ])),
      http.get(`${API}/courses`, () => HttpResponse.json([])),
      http.get(`${API}/academic-offering-cycles`, ({ request }) => {
        cycleRequests.push(new URL(request.url).search);
        return HttpResponse.json([
          { id: 10, programId: 1, courseId: null, status: 'APPROVED' },
          { id: 11, programId: 2, courseId: null, status: 'DRAFT' },
        ]);
      }),
    );
    renderWithProviders(<PreceptorOfertaAcademica />, { route: '/director/oferta-academica' });
    expect(await screen.findByText('Aprobado')).toBeInTheDocument();
    expect(screen.getByText('En construcción')).toBeInTheDocument();
    expect(cycleRequests).toEqual(['']);
  });
});
