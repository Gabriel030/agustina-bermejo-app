import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DirectorEsquemasEvaluacion } from './DirectorEsquemasEvaluacion';

const API = 'http://localhost:8080';

describe('DirectorEsquemasEvaluacion load error', () => {
  it('shows the retry state instead of a blank table when the schemes fail to load', async () => {
    server.use(http.get(`${API}/evaluation-schemes`, () => HttpResponse.json({ code: 'BOOM' }, { status: 500 })));
    renderWithProviders(<DirectorEsquemasEvaluacion />);

    expect(await screen.findByText('No pudimos cargar los esquemas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('No hay esquemas todavía')).not.toBeInTheDocument();
  });
});
