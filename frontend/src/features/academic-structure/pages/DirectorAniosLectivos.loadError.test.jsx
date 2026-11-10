import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { errorBody } from '@/test/fixtures/errors';
import { DirectorAniosLectivos } from './DirectorAniosLectivos';

const API = 'http://localhost:8080';

describe('DirectorAniosLectivos load error', () => {
  it('shows the retry state instead of an empty program selector', async () => {
    server.use(http.get(`${API}/programs`, () => HttpResponse.json(errorBody('INTERNAL_ERROR', { retryable: true }), { status: 500 })));
    renderWithProviders(<DirectorAniosLectivos />);

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Carrera')).not.toBeInTheDocument();
  });
});
