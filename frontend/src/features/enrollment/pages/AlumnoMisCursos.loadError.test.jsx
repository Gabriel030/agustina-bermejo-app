import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AlumnoMisCursos } from './AlumnoMisCursos';

const API = 'http://localhost:8080';

describe('AlumnoMisCursos load error', () => {
  it('shows the retry state instead of the empty list when the enrollments fail to load', async () => {
    server.use(http.get(`${API}/me/course-enrollments`, () => HttpResponse.json({ code: 'BOOM' }, { status: 500 })));
    renderWithProviders(<AlumnoMisCursos />);

    expect(await screen.findByText('No pudimos cargar tus cursos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('No estás cursando ningún curso')).not.toBeInTheDocument();
  });
});
