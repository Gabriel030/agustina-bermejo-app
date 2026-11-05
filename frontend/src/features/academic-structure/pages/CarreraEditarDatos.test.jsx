import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Routes, Route } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { CarreraEditarDatos } from './CarreraEditarDatos';

const API = 'http://localhost:8080';

const PROGRAM = {
  id: 9,
  name: 'Enfermería',
  totalYears: 3,
  totalCreditsRequired: 30,
  totalHoursRequired: 900,
  level: 'UNDERGRADUATE',
  teachingMode: 'HYBRID',
  graduationProfile: 'Perfil del egresado',
  admissionRequirements: 'Secundario completo',
  status: 'ACTIVE',
};

describe('CarreraEditarDatos', () => {
  it('sends null for the optional fields the user cleared so the backend erases them', async () => {
    let body = null;
    server.use(
      http.get(`${API}/programs/9`, () => HttpResponse.json(PROGRAM)),
      http.put(`${API}/programs/9`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(PROGRAM);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route path="/administrativo/gestion-academica/carreras/:id/editar" element={<CarreraEditarDatos />} />
        <Route path="/administrativo/gestion-academica/carreras/:id" element={<div>Detalle</div>} />
      </Routes>,
      { route: '/administrativo/gestion-academica/carreras/9/editar' },
    );

    await screen.findByDisplayValue('Perfil del egresado');
    await user.clear(screen.getByDisplayValue('Perfil del egresado'));
    await user.clear(screen.getByDisplayValue('900'));
    await user.selectOptions(screen.getAllByRole('combobox')[1], '');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(body).not.toBeNull());
    expect(body).toMatchObject({
      name: 'Enfermería',
      totalYears: 3,
      teachingMode: null,
      level: 'UNDERGRADUATE',
      totalHoursRequired: null,
      graduationProfile: null,
      admissionRequirements: 'Secundario completo',
    });
  });
});

describe('CarreraEditarDatos load failure', () => {
  it('does not mount the form nor allow saving when the program fails to load, and retries', async () => {
    let puts = 0;
    let fail = true;
    server.use(
      http.get(`${API}/programs/9`, () => (fail ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(PROGRAM))),
      http.put(`${API}/programs/9`, () => {
        puts += 1;
        return HttpResponse.json(PROGRAM);
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route path="/administrativo/gestion-academica/carreras/:id/editar" element={<CarreraEditarDatos />} />
      </Routes>,
      { route: '/administrativo/gestion-academica/carreras/9/editar' },
    );

    await screen.findByText('No pudimos cargar la carrera');
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(puts).toBe(0);

    fail = false;
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    await screen.findByDisplayValue('Enfermería');
    expect(screen.getByRole('button', { name: 'Guardar cambios' })).toBeInTheDocument();
  });

  it('shows not found without retry when the program does not exist', async () => {
    server.use(http.get(`${API}/programs/9`, () => new HttpResponse(null, { status: 404 })));
    renderWithProviders(
      <Routes>
        <Route path="/administrativo/gestion-academica/carreras/:id/editar" element={<CarreraEditarDatos />} />
      </Routes>,
      { route: '/administrativo/gestion-academica/carreras/9/editar' },
    );
    await screen.findByText('No encontramos lo que buscabas');
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
  });
});
