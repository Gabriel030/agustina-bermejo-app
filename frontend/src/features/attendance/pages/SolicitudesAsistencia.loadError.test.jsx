import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { SolicitudesAsistencia } from './SolicitudesAsistencia';

const API = 'http://localhost:8080';

describe('SolicitudesAsistencia load error', () => {
  it('shows the retry state instead of saying there are no pending requests', async () => {
    server.use(
      http.get(`${API}/attendance/change-requests`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })),
      http.get(`${API}/attendance/justifications`, () => HttpResponse.json({ items: [], totalElements: 0 })),
    );
    renderWithProviders(<SolicitudesAsistencia />);

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Sin solicitudes pendientes')).not.toBeInTheDocument();
  });
});
