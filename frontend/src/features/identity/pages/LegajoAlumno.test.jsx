import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoAlumno } from './LegajoAlumno';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: ['PRECEPTOR'] } }),
}));

const API = 'http://localhost:8080';

const STUDENT = {
  id: 9,
  nombre: 'Lucía',
  apellido: 'Pérez',
  dni: '30111222',
  username: '30111222',
  email: 'lucia@demo.test',
  status: 'ACTIVE',
  domicilio: null,
};

describe('LegajoAlumno — load failure', () => {
  it('shows the error state instead of an endless skeleton and loads the record again on retry', async () => {
    let calls = 0;
    server.use(
      http.get(`${API}/students/9`, () => {
        calls += 1;
        return calls === 1 ? new HttpResponse(null, { status: 500 }) : HttpResponse.json(STUDENT);
      }),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <Routes>
        <Route path="/:rol/alumnos/:id" element={<LegajoAlumno />} />
      </Routes>,
      { route: '/preceptor/alumnos/9' },
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('No pudimos cargar el legajo');
    expect(screen.queryByRole('tab', { name: 'Datos personales' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByText('Pérez, Lucía')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Datos personales' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
