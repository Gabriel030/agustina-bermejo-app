import { afterEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { LegajoAlumno } from './LegajoAlumno';
import { studentFixture } from '@/test/fixtures/students';

const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));
const toasts = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { roles: auth.roles } }),
}));

vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toasts }));

const API = 'http://localhost:8080';

const STUDENT = studentFixture({ id: 9, nombre: 'Lucía', apellido: 'Pérez', dni: '30111222', email: 'lucia@demo.test', birthDate: '2000-05-04' });

function renderAs(role, record = STUDENT) {
  auth.roles = [role.toUpperCase()];
  server.use(
    http.get(`${API}/students/9`, () => HttpResponse.json(record)),
    http.get(`${API}/*`, () => HttpResponse.json([])),
  );
  renderWithProviders(
    <Routes>
      <Route path="/:rol/alumnos/:id" element={<LegajoAlumno />} />
    </Routes>,
    { route: `/${role}/alumnos/9` },
  );
}

function captureUpdate(response = () => HttpResponse.json(STUDENT)) {
  const captured = { calls: 0, body: null };
  server.use(
    http.put(`${API}/students/9`, async ({ request }) => {
      captured.calls += 1;
      captured.body = await request.json();
      return response();
    }),
  );
  return captured;
}

describe('LegajoAlumno — edit student data', () => {
  afterEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
    toasts.success.mockClear();
    toasts.error.mockClear();
  });

  it.each(['administrativo', 'subdirector', 'director'])('shows the edit button to %s', async (role) => {
    renderAs(role);
    expect(await screen.findByRole('button', { name: 'Editar datos' })).toBeInTheDocument();
  });

  it.each(['preceptor', 'docente'])('hides the edit button from %s', async (role) => {
    renderAs(role);
    await screen.findByText('Pérez, Lucía');
    expect(screen.queryByRole('button', { name: 'Editar datos' })).not.toBeInTheDocument();
  });

  it('hides the edit button for a withdrawn student', async () => {
    renderAs('director', { ...STUDENT, status: 'DELETED' });
    await screen.findByText('Pérez, Lucía');
    expect(screen.queryByRole('button', { name: 'Editar datos' })).not.toBeInTheDocument();
  });

  it('saves without confirmation when neither email nor document change', async () => {
    const captured = captureUpdate();
    const user = userEvent.setup();
    renderAs('administrativo');
    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    const phone = screen.getByLabelText('Teléfono');
    await user.clear(phone);
    await user.type(phone, '3519999999');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(toasts.success).toHaveBeenCalledWith('Datos actualizados'));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(captured.body).toMatchObject({ firstName: 'Lucía', lastName: 'Pérez', documentNumber: '30111222', phone: '3519999999', email: 'lucia@demo.test' });
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
  });

  it('asks for confirmation with before and after when the email changes and sends only after confirming', async () => {
    const captured = captureUpdate();
    const user = userEvent.setup();
    renderAs('administrativo');
    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    const email = screen.getByLabelText(/^Email/);
    await user.clear(email);
    await user.type(email, 'nuevo@demo.test');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('Antes: lucia@demo.test');
    expect(dialog).toHaveTextContent('Después: nuevo@demo.test');
    expect(dialog).toHaveTextContent('queda sin verificar');
    expect(screen.getByRole('button', { name: 'Cancelar', hidden: false })).toBeInTheDocument();
    expect(captured.calls).toBe(0);
    await user.click(screen.getByRole('button', { name: 'Confirmar y guardar' }));

    await waitFor(() => expect(captured.body).toMatchObject({ email: 'nuevo@demo.test' }));
  });

  it('asks for confirmation when the document changes and omits the email notice', async () => {
    captureUpdate();
    const user = userEvent.setup();
    renderAs('administrativo');
    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    const dni = screen.getByLabelText(/^DNI/);
    await user.clear(dni);
    await user.type(dni, '30111999');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    const dialog = await screen.findByRole('alertdialog');
    expect(dialog).toHaveTextContent('Antes: 30111222');
    expect(dialog).toHaveTextContent('Después: 30111999');
    expect(dialog).not.toHaveTextContent('queda sin verificar');
  });

  it('shows the duplicated document error inside the DNI field', async () => {
    captureUpdate(() => HttpResponse.json({ code: 'STUDENT_DOCUMENT_ALREADY_EXISTS', retryable: false }, { status: 409 }));
    const user = userEvent.setup();
    renderAs('administrativo');
    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    const dni = screen.getByLabelText(/^DNI/);
    await user.clear(dni);
    await user.type(dni, '30111999');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));
    await user.click(await screen.findByRole('button', { name: 'Confirmar y guardar' }));

    expect(await screen.findByText('Ya existe otra persona con ese documento.')).toBeInTheDocument();
    expect(dni).toHaveAttribute('aria-invalid', 'true');
    expect(toasts.error).not.toHaveBeenCalled();
  });

  it('reports other mutation errors with a toast', async () => {
    captureUpdate(() => HttpResponse.json({ code: 'INTERNAL_ERROR', retryable: false }, { status: 500 }));
    const user = userEvent.setup();
    renderAs('administrativo');
    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    await user.type(screen.getByLabelText('Teléfono'), '1');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(toasts.error).toHaveBeenCalled());
    expect(toasts.success).not.toHaveBeenCalled();
  });

  it('discards the edits when cancelling', async () => {
    const captured = captureUpdate();
    const user = userEvent.setup();
    renderAs('administrativo');
    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));
    await user.clear(screen.getByLabelText(/^Nombre/));
    await user.type(screen.getByLabelText(/^Nombre/), 'Otra');
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(await screen.findByDisplayValue('Lucía Pérez')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
    expect(captured.calls).toBe(0);
  });

  it('disables the email field for a Google account and still saves the other fields with the unchanged email', async () => {
    const captured = captureUpdate();
    const user = userEvent.setup();
    renderAs('administrativo', { ...STUDENT, emailEditable: false });
    await user.click(await screen.findByRole('button', { name: 'Editar datos' }));

    expect(screen.getByLabelText(/^Email/)).toBeDisabled();
    expect(screen.getByText('Esta cuenta inicia sesión con Google: el email no se edita por acá.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Teléfono'), '1');
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(captured.body).toMatchObject({ email: 'lucia@demo.test' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('keeps the email editable when the account is not a Google one', async () => {
    renderAs('administrativo', { ...STUDENT, emailEditable: true });
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Editar datos' }));

    expect(screen.getByLabelText(/^Email/)).toBeEnabled();
  });
});
