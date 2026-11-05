import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { axe } from '@/test/axe';
import { errorBody } from '@/test/fixtures/errors';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraEditarDatos } from './CarreraEditarDatos';
import { CarreraWizard } from './CarreraWizard';

const API = 'http://localhost:8080';

const PROGRAM = {
  id: 9,
  name: 'Enfermería',
  totalYears: 3,
  level: 'UNDERGRADUATE',
  teachingMode: 'HYBRID',
  totalCreditsRequired: 180,
  totalHoursRequired: 900,
};

function renderEdit() {
  server.use(http.get(`${API}/programs/9`, () => HttpResponse.json(PROGRAM)));
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/carreras/:id/editar" element={<CarreraEditarDatos />} />
    </Routes>,
    { route: '/administrativo/gestion-academica/carreras/9/editar' },
  );
}

describe('CarreraWizard form', () => {
  it('associates every label with its control and has no axe violations', async () => {
    const { container } = renderWithProviders(<CarreraWizard />);

    expect(screen.getByLabelText(/Nombre de la carrera/)).toBeRequired;
    expect(screen.getByLabelText(/Duración \(años\)/)).toHaveAttribute('aria-required', 'true');
    expect(screen.getByLabelText('Nivel')).toBeInTheDocument();
    expect(screen.getByLabelText('Modalidad')).toBeInTheDocument();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('marks the invalid fields and explains why it cannot continue', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CarreraWizard />);

    await user.type(screen.getByLabelText(/Duración \(años\)/), '2.5');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(screen.getByLabelText(/Nombre de la carrera/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText(/Duración \(años\)/)).toHaveAccessibleDescription(
      'La duración debe ser un número entero de 1 a 10 años.',
    );
    expect(screen.getByText('Corregí los 2 campos marcados para continuar.')).toBeInTheDocument();
  });
});

describe('CarreraEditarDatos form', () => {
  it('has labelled controls, responsive grids and no axe violations', async () => {
    const { container } = renderEdit();

    expect(await screen.findByLabelText(/Nombre/)).toHaveValue('Enfermería');
    expect(screen.getByLabelText(/Créditos requeridos/)).toHaveValue(180);
    expect(screen.getByLabelText('Modalidad')).toHaveValue('HYBRID');
    expect(container.querySelectorAll('.grid-cols-1.sm\\:grid-cols-2').length).toBe(2);
    expect(container.querySelector('.grid-cols-2:not(.sm\\:grid-cols-2)')).toBeNull();
    expect(await axe(container)).toHaveNoViolations();
  });

  it('marks a fractional duration as invalid and does not send the request', async () => {
    let sent = false;
    server.use(
      http.put(`${API}/programs/9`, () => {
        sent = true;
        return HttpResponse.json(PROGRAM);
      }),
    );
    const user = userEvent.setup();
    renderEdit();

    const years = await screen.findByLabelText(/Duración \(años\)/);
    await user.clear(years);
    await user.type(years, '2.5');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(years).toHaveAttribute('aria-invalid', 'true');
    expect(within(years.parentElement).getByRole('alert')).toHaveTextContent('entero de 1 a 10');
    expect(screen.getByText('Corregí el campo marcado para continuar.')).toBeInTheDocument();
    expect(sent).toBe(false);
  });

  it('shows the mapped message when the server rejects the validation', async () => {
    server.use(http.put(`${API}/programs/9`, () => HttpResponse.json(errorBody('VALIDATION_FAILED'), { status: 400 })));
    const user = userEvent.setup();
    renderEdit();

    await user.click(await screen.findByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByText(/El servidor rechazó los datos/)).toBeInTheDocument();
  });
});
