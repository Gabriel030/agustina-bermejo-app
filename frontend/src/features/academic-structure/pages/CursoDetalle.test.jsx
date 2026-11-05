import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CursoDetalle } from './CursoDetalle';

const API = 'http://localhost:8080';

const COURSE = { id: 5, name: 'Primeros auxilios', status: 'ACTIVE', visibleInCatalog: false, capacity: 20 };

function renderPage() {
  renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/cursos/:id" element={<CursoDetalle />} />
    </Routes>,
    { route: '/administrativo/gestion-academica/cursos/5' },
  );
}

describe('CursoDetalle load failure', () => {
  it('shows a load error with retry instead of not found when the course request fails', async () => {
    server.use(
      http.get(`${API}/courses/5`, () => new HttpResponse(null, { status: 500 })),
      http.get(`${API}/courses/5/subjects`, () => HttpResponse.json([])),
    );
    renderPage();
    await screen.findByText('No pudimos cargar el curso');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Curso no encontrado')).not.toBeInTheDocument();
  });

  it('shows not found with a back link on 404', async () => {
    server.use(
      http.get(`${API}/courses/5`, () => new HttpResponse(null, { status: 404 })),
      http.get(`${API}/courses/5/subjects`, () => HttpResponse.json([])),
    );
    renderPage();
    await screen.findByText('No encontramos lo que buscabas');
    expect(screen.getByRole('link', { name: 'Volver a cursos' })).toBeInTheDocument();
  });

  it('shows an error instead of an empty syllabus and hides add module when modules fail', async () => {
    server.use(
      http.get(`${API}/courses/5`, () => HttpResponse.json(COURSE)),
      http.get(`${API}/courses/5/subjects`, () => new HttpResponse(null, { status: 500 })),
    );
    renderPage();
    await screen.findByText('No pudimos cargar el temario');
    expect(screen.queryByText('Sin módulos')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Agregar módulo/ })).not.toBeInTheDocument();
  });
});
