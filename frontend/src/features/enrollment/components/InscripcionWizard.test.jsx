import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { InscripcionWizard } from './InscripcionWizard';

const API = 'http://localhost:8080';

const auth = vi.hoisted(() => ({ usuario: null, refrescarPerfil: vi.fn() }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: auth.usuario, refrescarPerfil: auth.refrescarPerfil }),
}));

vi.setConfig({ testTimeout: 20000 });

const EMPTY_STUDENT = {
  nombre: 'Ana',
  apellido: 'Pérez',
  email: '',
  documentType: 'DNI',
  dni: '',
  street: '',
  number: '',
  floor: '',
  city: '',
  state: '',
  postalCode: '',
  country: '',
};

const TARGET = { id: 5, name: 'Ceramica Inicial' };
const COHORTS = [{ id: 11, label: 'Cohorte marzo', ordenEnAnio: 1, cursadaStart: '2027-03-01' }];
const PRICING = {
  matriculaCohorte1: { amount: '1000.00', currency: 'ARS' },
  matriculaCohorte2: { amount: '2000.00', currency: 'ARS' },
  cuotaMensual: { amount: '500.00', currency: 'ARS' },
};

function renderWizard(tipo = 'curso') {
  return renderWithProviders(
    <InscripcionWizard tipo={tipo} target={TARGET} cohorts={COHORTS} pricing={PRICING} volverA="/catalogo" />,
  );
}

const field = {
  email: () => screen.getByPlaceholderText('tu@email.com'),
  dni: () => screen.getByLabelText('Número de documento'),
  street: () => screen.getByText('Calle').parentElement.querySelector('input'),
  number: () => screen.getByText('Número').parentElement.querySelector('input'),
  city: () => screen.getByText('Ciudad').parentElement.querySelector('input'),
};

async function fillValidData(user, overrides = {}) {
  const data = { email: 'ana@demo.test', dni: '40111222', street: 'Calle Falsa', number: '123', city: 'Cordoba', ...overrides };
  await user.type(field.email(), data.email);
  await user.type(field.dni(), data.dni);
  await user.type(field.street(), data.street);
  await user.type(field.number(), data.number);
  await user.type(field.city(), data.city);
}

function continueButton() {
  return screen.getByRole('button', { name: /Continuar/ });
}

describe('InscripcionWizard validation with zod', () => {
  beforeEach(() => {
    auth.usuario = { ...EMPTY_STUDENT };
    auth.refrescarPerfil.mockReset().mockResolvedValue(undefined);
  });

  it('blocks continuing and lists every missing required field until the data is valid', async () => {
    const user = userEvent.setup();
    renderWizard('curso');

    expect(continueButton()).toBeDisabled();

    await user.type(field.street(), 'Calle Falsa');

    expect(
      await screen.findByText('Para continuar falta: el email, el documento, el número, la ciudad.'),
    ).toBeInTheDocument();
    expect(continueButton()).toBeDisabled();

    await user.type(field.email(), 'ana@demo.test');
    await user.type(field.dni(), '40111222');
    await user.type(field.number(), '123');
    expect(continueButton()).toBeDisabled();
    await user.type(field.city(), 'Cordoba');

    await waitFor(() => expect(continueButton()).toBeEnabled());
    expect(screen.queryByText(/Para continuar falta/)).not.toBeInTheDocument();
  });

  it('shows the Spanish message for a malformed email once the field loses focus', async () => {
    const user = userEvent.setup();
    renderWizard('curso');

    await user.type(field.email(), 'ana@');
    await user.tab();

    expect(await screen.findByText('El email no es válido')).toBeInTheDocument();
    expect(field.email()).toHaveAttribute('aria-invalid', 'true');
    expect(continueButton()).toBeDisabled();

    await user.clear(field.email());
    await user.type(field.email(), 'ana@demo.test');
    await user.tab();

    await waitFor(() => expect(screen.queryByText('El email no es válido')).not.toBeInTheDocument());
  });

  it('treats a whitespace only value as empty thanks to trim and reports each required field in Spanish', async () => {
    const user = userEvent.setup();
    renderWizard('curso');

    await user.type(field.email(), '   ');
    await user.tab();
    expect(await screen.findByText('Ingresá tu email')).toBeInTheDocument();

    await user.type(field.street(), '   ');
    await user.tab();
    expect(await screen.findByText('Ingresá la calle')).toBeInTheDocument();

    await user.type(field.number(), '  ');
    await user.tab();
    expect(await screen.findByText('Ingresá el número')).toBeInTheDocument();

    await user.type(field.city(), ' ');
    await user.tab();
    expect(await screen.findByText('Ingresá la ciudad')).toBeInTheDocument();

    expect(continueButton()).toBeDisabled();
  });

  it('accepts an email surrounded by spaces because trim runs before the email check', async () => {
    const user = userEvent.setup();
    renderWizard('curso');

    await fillValidData(user, { email: '  ana@demo.test  ' });

    await waitFor(() => expect(continueButton()).toBeEnabled());
    expect(screen.queryByText('El email no es válido')).not.toBeInTheDocument();
  });

  it('validates the document number against the default DNI type and then against the chosen enum value', async () => {
    const user = userEvent.setup();
    renderWizard('curso');

    expect(screen.getByRole('combobox', { name: 'Tipo de documento' })).toHaveTextContent('DNI');

    await user.type(field.dni(), 'AB123');
    await user.tab();
    expect(await screen.findByText('El DNI tiene 7 u 8 números, sin puntos ni espacios')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Tipo de documento' }));
    await user.click(await screen.findByRole('option', { name: 'Pasaporte' }));

    await waitFor(() =>
      expect(
        screen.getByText('El pasaporte tiene entre 4 y 20 letras o números, sin espacios ni guiones'),
      ).toBeInTheDocument(),
    );
    expect(field.dni()).toHaveAttribute('aria-invalid', 'false');
  });

  it('shows the required document message when the number is empty after touching it', async () => {
    const user = userEvent.setup();
    renderWizard('curso');

    await user.click(field.dni());
    await user.tab();

    expect(await screen.findByText('Ingresá tu documento')).toBeInTheDocument();
  });

  it('keeps optional fields out of the required list', async () => {
    const user = userEvent.setup();
    renderWizard('curso');

    await fillValidData(user);

    await waitFor(() => expect(continueButton()).toBeEnabled());
    expect(screen.getByText('Provincia')).toBeInTheDocument();
    expect(screen.queryByText(/Para continuar falta/)).not.toBeInTheDocument();
  });

  it('submits a course enrollment with trimmed data after the receipt is uploaded', async () => {
    let profileBody;
    let enrollmentContentType;
    server.use(
      http.put(`${API}/me`, async ({ request }) => {
        profileBody = await request.json();
        return HttpResponse.json({});
      }),
      http.post(`${API}/me/course-enrollments`, async ({ request }) => {
        enrollmentContentType = request.headers.get('content-type');
        return HttpResponse.json({ id: 90 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { container } = renderWizard('curso');

    await fillValidData(user, { email: '  ana@demo.test ', dni: ' 40111222 ' });
    await waitFor(() => expect(continueButton()).toBeEnabled());
    await user.click(continueButton());

    expect(await screen.findByText('Monto a pagar')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Confirmar solicitud/ })).toBeDisabled();
    expect(screen.getByText('Para continuar falta: el comprobante de pago.')).toBeInTheDocument();

    await user.upload(container.querySelector('input[type="file"]'), new File(['%PDF'], 'comprobante.pdf', { type: 'application/pdf' }));
    await user.click(screen.getByRole('button', { name: /Confirmar solicitud/ }));
    await user.click(await screen.findByRole('button', { name: 'Sí, confirmar' }));

    expect(await screen.findByText('Solicitud enviada', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(profileBody).toEqual({
      email: 'ana@demo.test',
      documentType: 'DNI',
      dni: '40111222',
      street: 'Calle Falsa',
      number: '123',
      floor: null,
      city: 'Cordoba',
      state: null,
      postalCode: null,
      country: null,
    });
    expect(enrollmentContentType).toContain('multipart/form-data');
    expect(auth.refrescarPerfil).toHaveBeenCalledTimes(1);
  });

  it('submits a program application with its three documents once the data is valid', async () => {
    const uploadedTypes = [];
    let applicationForm;
    server.use(
      http.put(`${API}/me`, () => HttpResponse.json({})),
      http.post(`${API}/me/program-enrollment-applications`, async ({ request }) => {
        applicationForm = await request.formData();
        return HttpResponse.json({ id: 77 }, { status: 201 });
      }),
      http.post(`${API}/me/program-enrollment-applications/77/documents`, ({ request }) => {
        uploadedTypes.push(new URL(request.url).searchParams.get('type'));
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    const { container } = renderWizard('carrera');

    await user.click(screen.getByRole('button', { name: /Cohorte marzo/ }));
    await fillValidData(user);

    const confirmButton = screen.getByRole('button', { name: 'Confirmar solicitud' });
    expect(confirmButton).toBeDisabled();
    expect(
      await screen.findByText('Para continuar falta: DNI — frente, DNI — dorso, Analítico del secundario.'),
    ).toBeInTheDocument();

    const file = new File(['%PDF'], 'doc.pdf', { type: 'application/pdf' });
    for (const input of container.querySelectorAll('input[type="file"]')) {
      await user.upload(input, file);
    }
    await waitFor(() => expect(confirmButton).toBeEnabled());
    await user.click(confirmButton);
    await user.click(await screen.findByRole('button', { name: 'Sí, confirmar' }));

    expect(await screen.findByText('Solicitud enviada', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(applicationForm.get('programId')).toBe('5');
    expect(applicationForm.get('cohortId')).toBe('11');
    expect(uploadedTypes).toEqual(['DNI_FRENTE', 'DNI_DORSO', 'ANALITICO_SECUNDARIO']);
  });

  it('does not call the profile endpoint when the stored data was not modified', async () => {
    auth.usuario = {
      ...EMPTY_STUDENT,
      email: 'ana@demo.test',
      dni: '40111222',
      street: 'Calle Falsa',
      number: '123',
      city: 'Cordoba',
    };
    let profileCalls = 0;
    server.use(
      http.put(`${API}/me`, () => {
        profileCalls += 1;
        return HttpResponse.json({});
      }),
      http.post(`${API}/me/course-enrollments`, () => HttpResponse.json({ id: 90 }, { status: 201 })),
    );
    const user = userEvent.setup();
    const { container } = renderWizard('curso');

    expect(continueButton()).toBeEnabled();
    await user.click(continueButton());
    await user.upload(container.querySelector('input[type="file"]'), new File(['x'], 'c.pdf', { type: 'application/pdf' }));
    await user.click(screen.getByRole('button', { name: /Confirmar solicitud/ }));
    await user.click(await screen.findByRole('button', { name: 'Sí, confirmar' }));

    expect(await screen.findByText('Solicitud enviada', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(profileCalls).toBe(0);
  });
});
