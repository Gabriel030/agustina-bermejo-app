import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorOfertaAcademica } from './PreceptorOfertaAcademica';

const API = 'http://localhost:8080';

describe('PreceptorOfertaAcademica requests', () => {
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
