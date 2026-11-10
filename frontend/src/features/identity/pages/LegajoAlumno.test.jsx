import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoAlumno } from './LegajoAlumno';
import { studentFixture } from '@/test/fixtures/students';

const auth = vi.hoisted(() => ({ roles: ['PRECEPTOR'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

const API = 'http://localhost:8080';

const STUDENT = studentFixture({
  id: 9,
  nombre: 'Lucía',
  apellido: 'Pérez',
  dni: '30111222',
  username: '30111222',
  email: 'lucia@demo.test',
  status: 'ACTIVE',
});

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

  it('shows the minor indicator, the guardian and the emergency contact in the personal data tab', async () => {
    server.use(
      http.get(`${API}/students/9`, () =>
        HttpResponse.json({
          ...STUDENT,
          birthDate: '2011-03-04',
          minor: true,
          guardian: { fullName: 'Marta Gómez', documentNumber: '20111222', relationship: 'Madre', phone: '3511234567', email: 'marta@demo.test' },
          emergencyContact: { name: 'Raúl Gómez', relationship: 'Padre', phone: '3517654321' },
        }),
      ),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/:rol/alumnos/:id" element={<LegajoAlumno />} />
      </Routes>,
      { route: '/preceptor/alumnos/9' },
    );

    expect(await screen.findByText('Menor de edad')).toBeInTheDocument();
    expect(screen.getByText('Tutor')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Marta Gómez')).toBeInTheDocument();
    expect(screen.getByDisplayValue('20111222')).toBeInTheDocument();
    expect(screen.getByText('Contacto de emergencia')).toBeInTheDocument();
    expect(screen.getByDisplayValue('Raúl Gómez')).toBeInTheDocument();
    expect(screen.getByDisplayValue('04/03/2011')).toBeInTheDocument();
  });

  it('hides the guardian sections for an adult without guardian', async () => {
    server.use(
      http.get(`${API}/students/9`, () => HttpResponse.json({ ...STUDENT, minor: false })),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/:rol/alumnos/:id" element={<LegajoAlumno />} />
      </Routes>,
      { route: '/preceptor/alumnos/9' },
    );

    await screen.findByText('Pérez, Lucía');
    expect(screen.queryByText('Menor de edad')).not.toBeInTheDocument();
    expect(screen.queryByText('Tutor')).not.toBeInTheDocument();
  });
});

describe('LegajoAlumno — failed actions', () => {
  afterEach(() => {
    auth.roles = ['PRECEPTOR'];
  });

  function renderAs(role) {
    auth.roles = [role.toUpperCase()];
    server.use(
      http.get(`${API}/students/9`, () => HttpResponse.json(STUDENT)),
      http.get(`${API}/*`, () => HttpResponse.json([])),
    );
    renderWithProviders(
      <Routes>
        <Route path="/:rol/alumnos/:id" element={<LegajoAlumno />} />
      </Routes>,
      { route: `/${role}/alumnos/9` },
    );
  }

  it('keepsTheWithdrawalModalOpenAndExplainsTheActiveEnrollmentsConflict', async () => {
    server.use(
      http.post(`${API}/students/9/withdraw`, () =>
        HttpResponse.json({ code: 'STUDENT_HAS_ACTIVE_ENROLLMENTS', retryable: false }, { status: 409 }),
      ),
    );
    const user = userEvent.setup();
    renderAs('director');
    await user.click(await screen.findByRole('button', { name: 'Dar de baja' }));
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Abandono' }));
    await user.type(screen.getByPlaceholderText(/Información adicional/), 'Dejó de asistir');
    await user.click(screen.getByRole('button', { name: 'Confirmar baja' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El alumno tiene inscripciones activas');
    expect(screen.getByRole('button', { name: 'Confirmar baja' })).toBeEnabled();
  });

  it('showsTheFailureInsideTheResetPasswordModalInsteadOfClosingSilently', async () => {
    server.use(
      http.post(`${API}/students/9/reset-password`, () =>
        HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false }, { status: 500 }),
      ),
    );
    const user = userEvent.setup();
    renderAs('preceptor');
    await user.click(await screen.findByRole('button', { name: 'Resetear contraseña' }));
    await user.click(screen.getByRole('button', { name: 'Enviar link' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Tuvimos un problema de nuestro lado');
    expect(screen.getByRole('button', { name: 'Enviar link' })).toBeEnabled();
  });
});
