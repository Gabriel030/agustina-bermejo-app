import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoPagos } from './AlumnoPagos';

const API = 'http://localhost:8080';

describe('AlumnoPagos load error', () => {
  it('shows the retry state instead of saying there are no active enrollments', async () => {
    server.use(
      http.get(`${API}/me/course-enrollments`, () => HttpResponse.json({ code: 'BOOM' }, { status: 500 })),
      http.get(`${API}/me/program-enrollments`, () => HttpResponse.json([])),
    );
    renderWithProviders(<AlumnoPagos />);

    expect(await screen.findByText('No pudimos cargar tus inscripciones')).toBeInTheDocument();
    expect(screen.queryByText('No tenés inscripciones activas.')).not.toBeInTheDocument();
  });
});
