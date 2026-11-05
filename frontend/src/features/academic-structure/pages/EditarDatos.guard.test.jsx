import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraEditarDatos } from './CarreraEditarDatos';

vi.setConfig({ testTimeout: 20000 });

const API = 'http://localhost:8080';

function mount() {
  server.use(
    http.get(`${API}/programs/9`, () =>
      HttpResponse.json({ id: 9, name: 'Enfermería', totalYears: 3, totalCreditsRequired: 30, status: 'ACTIVE' }),
    ),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/administrativo/gestion-academica/carreras/:id/editar" element={<CarreraEditarDatos />} />
      <Route path="/administrativo/gestion-academica/carreras/:id" element={<div>Detalle</div>} />
    </Routes>,
    { route: '/administrativo/gestion-academica/carreras/9/editar' },
  );
}

describe('CarreraEditarDatos — exit protection', () => {
  it('cancels without asking when nothing changed', async () => {
    const user = userEvent.setup();
    mount();

    await screen.findByDisplayValue('Enfermería');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(await screen.findByText('Detalle')).toBeInTheDocument();
  });

  it('asks for confirmation when cancelling with changes and keeps them if the user stays', async () => {
    const user = userEvent.setup();
    mount();

    await user.type(await screen.findByDisplayValue('Enfermería'), ' II');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(await screen.findByRole('alertdialog', { name: '¿Salir sin guardar?' })).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: 'Cancelar' }).at(-1));
    expect(screen.getByDisplayValue('Enfermería II')).toBeInTheDocument();

    await user.click(screen.getAllByRole('button', { name: 'Cancelar' })[0]);
    await user.click(await screen.findByRole('button', { name: 'Salir sin guardar' }));
    expect(await screen.findByText('Detalle')).toBeInTheDocument();
  });
});
