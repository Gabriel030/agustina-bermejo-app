import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { errorBody } from '@/test/fixtures/errors';
import { server } from '@/test/mocks/server';
import { AuthProvider } from '@/context/AuthContext';
import { Registrarse } from '@/features/identity/pages/Registrarse';

const API = 'http://localhost:8080';

function renderRegistrarse() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/registrarse']}>
        <AuthProvider>
          <Registrarse />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function yearsAgo(years) {
  const date = new Date();
  date.setFullYear(date.getFullYear() - years);
  return date.toISOString().slice(0, 10);
}

async function fillBasicData(user, { birthDate = yearsAgo(30), passwords = 'Test1234' } = {}) {
  await user.type(screen.getByPlaceholderText('Nombre'), 'Sofía');
  await user.type(screen.getByPlaceholderText('Apellido'), 'Ramírez');
  await user.type(screen.getByPlaceholderText('tu@email.com'), 'sofia@example.com');
  await user.type(screen.getByPlaceholderText('Contraseña'), 'Test1234');
  await user.type(screen.getByPlaceholderText('Repetir contraseña'), passwords);
  fireEvent.change(screen.getByLabelText('Fecha de nacimiento'), { target: { value: birthDate } });
}

function captureRegisterBody() {
  const captured = {};
  server.use(
    http.post(`${API}/auth/register`, async ({ request }) => {
      captured.body = await request.json();
      return HttpResponse.json({ accessToken: 'access-1', expiresIn: 900 }, { status: 201 });
    }),
  );
  return captured;
}

describe('Registrarse', () => {
  it('rejectsMismatchedPasswordsWithoutCallingTheBackend', async () => {
    server.use(
      http.post(`${API}/auth/register`, () => {
        throw new Error('el backend no debería recibir contraseñas que no coinciden');
      }),
    );
    const user = userEvent.setup();
    renderRegistrarse();

    await fillBasicData(user, { passwords: 'Distinta123' });
    await user.click(screen.getByRole('checkbox', { name: /avisos institucionales/i }));
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText('Las contraseñas no coinciden.')).toBeInTheDocument();
  });

  it('showsEmailAlreadyRegisteredOnConflict', async () => {
    server.use(http.post(`${API}/auth/register`, () => HttpResponse.json(errorBody('EMAIL_ALREADY_EXISTS'), { status: 409 })));
    const user = userEvent.setup();
    renderRegistrarse();

    await fillBasicData(user);
    await user.click(screen.getByRole('checkbox', { name: /avisos institucionales/i }));
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText('Ese email ya está registrado.')).toBeInTheDocument();
  });

  it('offersASingleConsentCheckboxWithTheTermsAndPrivacyLinks', async () => {
    renderRegistrarse();

    expect(screen.getAllByRole('checkbox')).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Términos y condiciones' })).toHaveAttribute('href', '/legal/terminos');
    expect(screen.getByRole('link', { name: 'Política de privacidad' })).toHaveAttribute('href', '/legal/privacidad');
  });

  it('keepsSubmitBlockedWithAnAccessibleHintUntilTheConsentIsChecked', async () => {
    const captured = captureRegisterBody();
    const user = userEvent.setup();
    renderRegistrarse();

    await fillBasicData(user);
    const submit = screen.getByRole('button', { name: /crear cuenta/i });

    expect(submit).toBeDisabled();
    expect(submit).toHaveAccessibleDescription(/marcá la casilla de aceptación/i);
    expect(captured.body).toBeUndefined();
  });

  it('sendsAcceptanceBirthDateAndInstitutionalNoticesForAnAdult', async () => {
    const captured = captureRegisterBody();
    const user = userEvent.setup();
    renderRegistrarse();

    await fillBasicData(user, { birthDate: '1990-05-14' });
    expect(screen.queryByLabelText('Nombre del responsable')).not.toBeInTheDocument();
    await user.click(screen.getByRole('checkbox', { name: /avisos institucionales/i }));
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    await screen.findByRole('button', { name: /crear cuenta/i });
    await vi.waitFor(() => expect(captured.body).toBeDefined());
    expect(captured.body).toMatchObject({
      birthDate: '1990-05-14',
      acceptedTerms: true,
      acceptedPrivacy: true,
      acceptedInstitutionalNotices: true,
    });
    expect(captured.body).not.toHaveProperty('guardianName');
  });

  it('consent checkbox starts unchecked and blocks account creation until checked', async () => {
    const user = userEvent.setup();
    renderRegistrarse();

    const notices = screen.getByRole('checkbox', { name: /avisos institucionales/i });
    expect(notices).not.toBeChecked();
    expect(screen.getByRole('button', { name: /crear cuenta/i })).toBeDisabled();
    expect(screen.queryByText(/texto provisional/i)).not.toBeInTheDocument();

    await user.click(notices);
    expect(screen.getByRole('button', { name: /crear cuenta/i })).toBeEnabled();
  });

  it('asksForGuardianDataOnlyWhenTheBirthDateMakesThePersonAMinor', async () => {
    const captured = captureRegisterBody();
    const user = userEvent.setup();
    renderRegistrarse();

    await fillBasicData(user, { birthDate: yearsAgo(10) });
    await user.type(screen.getByLabelText('Nombre del responsable'), 'Marcela Ramírez');
    await user.type(screen.getByLabelText('Correo del responsable'), 'marcela@example.com');
    await user.type(screen.getByLabelText('Vínculo con el responsable'), 'Madre');
    await user.click(screen.getByRole('checkbox', { name: /avisos institucionales/i }));
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    await vi.waitFor(() => expect(captured.body).toBeDefined());
    expect(captured.body).toMatchObject({
      guardianName: 'Marcela Ramírez',
      guardianEmail: 'marcela@example.com',
      guardianRelationship: 'Madre',
      acceptedInstitutionalNotices: true,
    });
  });

  it('hidesGuardianFieldsAgainWhenTheBirthDateIsCorrectedToAnAdult', async () => {
    const user = userEvent.setup();
    renderRegistrarse();

    await fillBasicData(user, { birthDate: yearsAgo(10) });
    expect(screen.getByLabelText('Nombre del responsable')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Fecha de nacimiento'), { target: { value: yearsAgo(25) } });
    expect(screen.queryByLabelText('Nombre del responsable')).not.toBeInTheDocument();
  });

  it.each([
    ['MINOR_GUARDIAN_REQUIRED', 'Si sos menor de 18 años tenés que cargar nombre, correo y vínculo de un responsable.'],
    ['BIRTH_DATE_REQUIRED', 'Falta la fecha de nacimiento.'],
    ['LEGAL_ACCEPTANCE_REQUIRED', 'Para registrarte tenés que aceptar los términos y condiciones y la política de privacidad.'],
  ])('showsTheBackendMessageFor%s', async (code, message) => {
    server.use(http.post(`${API}/auth/register`, () => HttpResponse.json(errorBody(code), { status: 400 })));
    const user = userEvent.setup();
    renderRegistrarse();

    await fillBasicData(user);
    await user.click(screen.getByRole('checkbox', { name: /avisos institucionales/i }));
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }));

    expect(await screen.findByText(message)).toBeInTheDocument();
  });
});
