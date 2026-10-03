import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { PreceptorDetalleCurso } from './PreceptorDetalleCurso';

const API = 'http://localhost:8080';

describe('PreceptorDetalleCurso', () => {
  it('shows a rejected course cycle with its reason and the option to fix it', async () => {
    server.use(
      http.get(`${API}/courses/10`, () => HttpResponse.json({ id: 10, name: 'Primeros auxilios', durationMonths: 4 })),
      http.get(`${API}/academic-offering-cycles`, () =>
        HttpResponse.json([
          {
            id: 22,
            status: 'REJECTED',
            termId: 7,
            termName: 'Ciclo lectivo 2027',
            courseId: 10,
            createdByName: 'Preceptor Demo',
            rejectionReason: 'El aula elegida no tiene camillas',
          },
        ]),
      ),
      http.get(`${API}/academic-offering-cycles/22/sections`, () => HttpResponse.json([])),
    );

    renderWithProviders(
      <Routes>
        <Route path="/preceptor/oferta-academica/cursos/:cursoId" element={<PreceptorDetalleCurso />} />
      </Routes>,
      { route: '/preceptor/oferta-academica/cursos/10' },
    );

    expect(await screen.findByText('El aula elegida no tiene camillas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Corregir y reenviar' })).toBeInTheDocument();
  });
});
