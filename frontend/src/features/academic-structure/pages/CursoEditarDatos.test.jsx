import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CursoEditarDatos } from './CursoEditarDatos';

const API = 'http://localhost:8080';

const COURSE = {
  id: 5,
  name: 'Primeros auxilios',
  capacity: 20,
  durationMonths: 3,
  teachingMode: 'ONLINE',
  schedule: 'Lunes 18hs',
  status: 'ACTIVE',
};

function renderPage() {
  renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/cursos/:id/editar" element={<CursoEditarDatos />} />
    </Routes>,
    { route: '/administrativo/gestion-academica/cursos/5/editar' },
  );
}

describe('CursoEditarDatos', () => {
  it('does not mount the form nor allow saving when the course fails to load, and retries', async () => {
    let puts = 0;
    let fail = true;
    server.use(
      http.get(`${API}/courses/5`, () => (fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(COURSE))),
      http.put(`${API}/courses/5`, () => {
        puts += 1;
        return HttpResponse.json(COURSE);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await screen.findByText('No pudimos cargar el curso');
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
    expect(puts).toBe(0);

    fail = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    await screen.findByDisplayValue('Primeros auxilios');
    expect(screen.getByDisplayValue('Lunes 18hs')).toBeInTheDocument();
  });

  it('shows not found without retry when the course does not exist', async () => {
    server.use(http.get(`${API}/courses/5`, () => new HttpResponse(null, { status: 404 })));
    renderPage();
    await screen.findByText('No encontramos lo que buscabas');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
  });
});
