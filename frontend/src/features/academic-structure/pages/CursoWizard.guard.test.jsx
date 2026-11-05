import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CursoWizard } from './CursoWizard';

vi.setConfig({ testTimeout: 20000 });

const API = 'http://localhost:8080';

function mount() {
  server.use(http.get(`${API}/terms`, () => HttpResponse.json([{ id: 4, year: 2027, description: 'Anual' }])));
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/cursos/nuevo" element={<CursoWizard />} />
      <Route path="/administrativo/gestion-academica" element={<div>Banco</div>} />
    </Routes>,
    { route: '/administrativo/gestion-academica/cursos/nuevo' },
  );
}

describe('CursoWizard — stepper and exit protection', () => {
  it('blocks jumping to the next step through the stepper when the name was cleared', async () => {
    const user = userEvent.setup();
    mount();

    await screen.findByRole('option', { name: /2027/ });
    await user.type(screen.getByPlaceholderText('Ej. Curso de Excel Avanzado'), 'Excel');
    await user.selectOptions(screen.getByLabelText(/Período/), '4');
    await user.type(screen.getByPlaceholderText('Ej. 30'), '20');
    await user.click(screen.getByRole('button', { name: 'Continuar' }));
    await user.click(await screen.findByRole('button', { name: 'Datos básicos' }));
    await user.clear(screen.getByPlaceholderText('Ej. Curso de Excel Avanzado'));
    await user.click(screen.getByRole('button', { name: 'Detalle' }));

    expect(screen.getByRole('button', { name: 'Datos básicos' })).toHaveAttribute('aria-current', 'step');
    expect(screen.getByText('El nombre es obligatorio.')).toBeInTheDocument();
  });

  it('asks before leaving with unsaved data', async () => {
    const user = userEvent.setup();
    mount();

    await user.type(screen.getByPlaceholderText('Ej. Curso de Excel Avanzado'), 'Excel');
    await user.click(screen.getByRole('link', { name: /Cursos/ }));

    expect(await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' })).toBeInTheDocument();
  });
});
