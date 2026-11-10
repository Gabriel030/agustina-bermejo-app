import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { FinancieroTab } from './FinancieroTab';

const API = 'http://localhost:8080';

describe('FinancieroTab load error', () => {
  it('shows the retry state instead of saying the student has no enrollments', async () => {
    server.use(
      http.get(`${API}/students/9/program-enrollments`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/students/9/course-enrollments`, () => HttpResponse.json([])),
      http.get(`${API}/students/9/refunds`, () => HttpResponse.json([])),
      http.get(`${API}/students/9/refund-eligibility`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
    );
    renderWithProviders(<FinancieroTab studentId={9} puedeHabilitar roleBase="director" />);

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Sin inscripciones')).not.toBeInTheDocument();
  });

  it('shows the retry state instead of saying there is no enrollment fee configured', async () => {
    server.use(
      http.get(`${API}/students/9/program-enrollments`, () =>
        HttpResponse.json([{ id: 4, programId: 2, programName: 'Tecnicatura', status: 'ENROLLED' }])),
      http.get(`${API}/students/9/course-enrollments`, () => HttpResponse.json([])),
      http.get(`${API}/program-enrollments/4/enrollment-fee-status`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/program-enrollments/4/enrollment-fee-exemptions`, () => HttpResponse.json([])),
      http.get(`${API}/program-enrollments/4/student-discounts`, () => HttpResponse.json([])),
      http.get(`${API}/students/9/refunds`, () => HttpResponse.json([])),
      http.get(`${API}/students/9/refund-eligibility`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
    );
    renderWithProviders(<FinancieroTab studentId={9} puedeHabilitar roleBase="director" />);

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Sin matrícula configurada')).not.toBeInTheDocument();
  });
});
