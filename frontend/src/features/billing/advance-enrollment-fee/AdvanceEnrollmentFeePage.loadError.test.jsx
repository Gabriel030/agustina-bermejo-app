import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { Route, Routes } from 'react-router-dom';
import { AdvanceEnrollmentFeePage } from './AdvanceEnrollmentFeePage';

const API = 'http://localhost:8080';

describe('AdvanceEnrollmentFeePage load error', () => {
  it('shows the retry state instead of saying the fee is unavailable', async () => {
    server.use(
      http.get(`${API}/me/program-enrollments/3/enrollment-fee-status`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/me/program-enrollments`, () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/alumno/pagos/carrera/:enrollmentId/matricula-anticipada" element={<AdvanceEnrollmentFeePage />} />
      </Routes>,
      { route: '/alumno/pagos/carrera/3/matricula-anticipada' },
    );

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('La matrícula del año siguiente no está disponible por ahora.')).not.toBeInTheDocument();
  });
});
