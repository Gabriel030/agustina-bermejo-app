import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCurso } from './DetalleCurso';

const API = 'http://localhost:8080';

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/cursos/:id" element={<DetalleCurso />} />
    </Routes>,
    { route: '/catalogo/cursos/1' },
  );
}

describe('DetalleCurso accessibility and block errors', () => {
  it('loadedCourseHasNoAxeViolationsAndASingleHeading', async () => {
    server.use(http.get(`${API}/courses/1/subjects`, () => HttpResponse.json([{ id: 1, name: 'Módulo 1', weeklyWorkload: 1 }])));
    const { container } = renderDetail();

    expect(await screen.findByRole('heading', { level: 1, name: 'Curso de prueba' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('heading', { level: 2, name: 'Temario' })).toBeInTheDocument();
    expect(await screen.findByText('1 h/semana')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('closedRegistrationDescribesTheDisabledButton', async () => {
    server.use(
      http.get(`${API}/courses/1`, () =>
        HttpResponse.json({ id: 1, name: 'Curso de prueba', durationMonths: 4, teachingMode: 'ONLINE', registrationOpen: false, full: false }),
      ),
    );
    const { container } = renderDetail();

    const button = await screen.findByRole('button', { name: 'Inscribirme' });
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription('La inscripción a este curso está cerrada.');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pricingErrorShowsRetryInsteadOfToDefine', async () => {
    server.use(http.get(`${API}/courses/1/pricing`, () => new HttpResponse(null, { status: 500 })));
    renderDetail();

    expect(await screen.findByText('No pudimos cargar los precios')).toBeInTheDocument();
    expect(screen.queryByText('A definir')).not.toBeInTheDocument();
  });

  it('syllabusErrorShowsRetry', async () => {
    server.use(http.get(`${API}/courses/1/subjects`, () => new HttpResponse(null, { status: 500 })));
    renderDetail();

    expect(await screen.findByText('No pudimos cargar el temario')).toBeInTheDocument();
    expect(screen.queryByText('Todavía no se cargó el temario de este curso.')).not.toBeInTheDocument();
  });

  it('mainErrorOffersRetryAndBackToCatalog', async () => {
    server.use(http.get(`${API}/courses/1`, () => new HttpResponse(null, { status: 500 })));
    renderDetail();

    expect(await screen.findByText('No pudimos cargar el curso')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver a el catálogo' })).toHaveAttribute('href', '/catalogo?tab=cursos');
  });
});
