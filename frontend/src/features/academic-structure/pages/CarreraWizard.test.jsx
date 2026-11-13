import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraWizard } from './CarreraWizard';

const API = 'http://localhost:8080';

async function fillFirstStep(user) {
  await user.type(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'), 'Enfermería');
  await user.type(screen.getByPlaceholderText('Ej. 3'), '3');
  await user.click(screen.getByRole('button', { name: 'Continuar' }));
}

describe('CarreraWizard', () => {
  it('marks the current step and lets the user go back to a visited step', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CarreraWizard />);

    expect(screen.getByRole('button', { name: 'Datos básicos' })).toHaveAttribute('aria-current', 'step');

    await user.type(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería'), 'Enfermería');
    await user.type(screen.getByPlaceholderText('Ej. 3'), '3');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));

    expect(await screen.findByRole('button', { name: 'Contenido académico' })).toHaveAttribute('aria-current', 'step');
    await user.click(screen.getByRole('button', { name: 'Datos básicos' }));

    expect(screen.getByPlaceholderText('Ej. Licenciatura en Enfermería')).toHaveValue('Enfermería');
  });

  it('does not create the program without a monthly fee and an enrollment fee greater than zero', async () => {
    let posted = false;
    server.use(http.post(`${API}/programs`, () => {
      posted = true;
      return HttpResponse.json({ id: 1 }, { status: 201 });
    }));
    const user = userEvent.setup();
    renderWithProviders(<CarreraWizard />);
    await fillFirstStep(user);
    await user.type(await screen.findByPlaceholderText('Ej. 180'), '180');
    await user.type(screen.getByPlaceholderText('Ej. 48000'), '0');

    await user.click(screen.getByRole('button', { name: 'Crear carrera' }));

    expect(await screen.findByText(/La cuota debe ser mayor que cero/)).toBeInTheDocument();
    expect(screen.getByText(/La matrícula es obligatoria y debe ser mayor que cero/)).toBeInTheDocument();
    expect(posted).toBe(false);
  });
});
