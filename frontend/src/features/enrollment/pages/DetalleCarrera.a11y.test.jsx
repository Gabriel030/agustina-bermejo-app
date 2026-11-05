import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { axe } from '@/test/axe';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DetalleCarrera } from './DetalleCarrera';

const API = 'http://localhost:8080';

function renderDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/catalogo/carreras/:id" element={<DetalleCarrera />} />
    </Routes>,
    { route: '/catalogo/carreras/1' },
  );
}

function plan(subjects) {
  return http.get(`${API}/programs/1/subjects`, () => HttpResponse.json(subjects));
}

describe('DetalleCarrera accessibility and block errors', () => {
  it('loadedProgramHasNoAxeViolationsAndASingleHeading', async () => {
    server.use(plan([{ id: 1, subjectName: 'Álgebra', curricularYear: 1, credits: 8 }]));
    const { container } = renderDetail();

    expect(await screen.findByRole('heading', { level: 1, name: 'Carrera de prueba' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('heading', { level: 2, name: 'Plan de estudio' })).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('disabledEnrollButtonIsDescribedByItsReason', async () => {
    server.use(http.get(`${API}/programs/1/cohorts`, () => HttpResponse.json([])));
    const { container } = renderDetail();

    const button = await screen.findByRole('button', { name: 'Inscribirme' });
    const reason = await screen.findByText('Todavía no hay cohortes abiertas para esta carrera.');
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription('Todavía no hay cohortes abiertas para esta carrera.');
    expect(reason.tagName).toBe('P');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('pricingErrorShowsRetryInsteadOfToDefine', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/programs/1/pricing`, () => {
        calls += 1;
        return calls === 1
          ? new HttpResponse(null, { status: 500 })
          : HttpResponse.json({
              programId: 1,
              matriculaCohorte1: { amount: '50000.00', currency: 'ARS' },
              matriculaCohorte2: { amount: '25000.00', currency: 'ARS' },
              cuotaMensual: { amount: '15000.00', currency: 'ARS' },
            });
      }),
    );
    const user = userEvent.setup();
    const { container } = renderDetail();

    expect(await screen.findByText('No pudimos cargar los precios')).toBeInTheDocument();
    expect(screen.queryByText('A definir')).not.toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();

    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('$ 50.000,00')).toBeInTheDocument();
    expect(screen.queryByText('No pudimos cargar los precios')).not.toBeInTheDocument();
  });

  it('cohortsErrorExplainsDisabledButton', async () => {
    server.use(http.get(`${API}/programs/1/cohorts`, () => new HttpResponse(null, { status: 500 })));
    renderDetail();

    const button = await screen.findByRole('button', { name: 'Inscribirme' });
    expect(await screen.findByText('No pudimos verificar las cohortes abiertas.')).toBeInTheDocument();
    expect(button).toBeDisabled();
    expect(button).toHaveAccessibleDescription('No pudimos verificar las cohortes abiertas.');
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });

  it('syllabusErrorShowsRetry', async () => {
    server.use(http.get(`${API}/programs/1/subjects`, () => new HttpResponse(null, { status: 500 })));
    renderDetail();

    expect(await screen.findByText('No pudimos cargar el plan de estudio')).toBeInTheDocument();
    expect(screen.queryByText('Todavía no se cargó el plan de estudio de esta carrera.')).not.toBeInTheDocument();
  });

  it('mainErrorOffersRetryAndBackToCatalog', async () => {
    server.use(http.get(`${API}/programs/1`, () => new HttpResponse(null, { status: 500 })));
    const { container } = renderDetail();

    expect(await screen.findByText('No pudimos cargar la carrera')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Volver al catálogo' })).toHaveAttribute('href', '/catalogo?tab=carreras');
    expect(await axe(container)).toHaveNoViolations();
  });

  it('formatsCreditsInSingularAndGroupsSubjectsWithoutYearLast', async () => {
    server.use(
      plan([
        { id: 1, subjectName: 'Taller libre', credits: 1 },
        { id: 2, subjectName: 'Álgebra', curricularYear: 1, credits: 8 },
      ]),
    );
    renderDetail();

    expect(await screen.findByText('1 crédito')).toBeInTheDocument();
    expect(screen.getByText('8 créditos')).toBeInTheDocument();
    const headings = screen.getAllByRole('heading', { level: 3 }).map((heading) => heading.textContent);
    expect(headings).toEqual(['Año 1', 'Sin año asignado']);
  });
});
