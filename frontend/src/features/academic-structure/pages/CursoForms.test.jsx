import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { axe } from '@/test/axe';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CursoEditarDatos } from './CursoEditarDatos';
import { CursoWizard } from './CursoWizard';
import { termFixture } from '@/test/fixtures/terms';

const API = 'http://localhost:8080';

const COURSE = {
  id: 5,
  name: 'Primeros auxilios',
  capacity: 20,
  durationMonths: 3,
  teachingMode: 'ONLINE',
  schedule: 'Lunes 18hs',
  price: { amount: '15000', currency: 'ARS' },
  status: 'ACTIVE',
};

function renderEdit() {
  server.use(http.get(`${API}/courses/5`, () => HttpResponse.json(COURSE)));
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/cursos/:id/editar" element={<CursoEditarDatos />} />
    </Routes>,
    { route: '/administrativo/gestion-academica/cursos/5/editar' },
  );
}

async function fillWizard(user) {
  server.use(http.get(`${API}/terms`, () => HttpResponse.json([termFixture({ id: 4, year: 2027, description: 'Anual' })])));
  const view = renderWithProviders(<CursoWizard />);
  await user.type(screen.getByLabelText(/Nombre del curso/), 'Excel avanzado');
  await screen.findByRole('option', { name: '2027 — Anual' });
  await user.selectOptions(screen.getByLabelText(/Período académico/), '4');
  await user.type(screen.getByLabelText(/Cupo/), '20');
  await user.type(screen.getByLabelText(/Cuota mensual/), '15000');
  await user.click(screen.getByRole('button', { name: 'Continuar' }));
  await user.type(await screen.findByLabelText(/Duración \(meses\)/), '3');
  return view;
}

describe('CursoWizard form', () => {
  it('associates every label with its control in both steps and has no axe violations', async () => {
    const user = userEvent.setup();
    const { container } = await fillWizard(user);

    expect(screen.getByLabelText('Horario')).toBeInTheDocument();
    expect(screen.getByLabelText('Inicio de inscripción')).toBeInTheDocument();
    expect(screen.getByLabelText('Cierre de inscripción')).toBeInTheDocument();
    expect(container.querySelector('.grid-cols-2:not(.sm\\:grid-cols-2)')).toBeNull();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('marks the first step fields that are missing', async () => {
    const user = userEvent.setup();
    server.use(http.get(`${API}/terms`, () => HttpResponse.json([])));
    renderWithProviders(<CursoWizard />);

    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(screen.getByLabelText(/Nombre del curso/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/Período académico/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/Cupo/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/Cuota mensual/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Corregí los 4 campos marcados para continuar.')).toBeInTheDocument();
  });
});

describe('CursoEditarDatos form', () => {
  it('has labelled controls and no axe violations', async () => {
    const { container } = renderEdit();

    expect(await screen.findByLabelText(/Nombre/)).toHaveValue('Primeros auxilios');
    expect(screen.getByLabelText(/Cuota mensual/)).toHaveValue(15000);
    expect(screen.getByLabelText('Modalidad')).toHaveValue('ONLINE');
    expect(container.querySelector('.grid-cols-2:not(.sm\\:grid-cols-2)')).toBeNull();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('does not let the price or the mode be emptied silently', async () => {
    let sent = false;
    server.use(
      http.put(`${API}/courses/5`, () => {
        sent = true;
        return HttpResponse.json(COURSE);
      }),
    );
    const user = userEvent.setup();
    renderEdit();

    await user.clear(await screen.findByLabelText(/Cuota mensual/));
    await user.selectOptions(screen.getByLabelText('Modalidad'), '');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(screen.getByLabelText(/Cuota mensual/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Modalidad')).toHaveAttribute('aria-invalid', 'true');
    expect(sent).toBe(false);
  });
});
