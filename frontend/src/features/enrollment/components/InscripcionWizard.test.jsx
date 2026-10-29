import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { todayIsoDate } from '@/lib/bankTransferReceipt';
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

function submittedEnrollment(fetchSpy) {
  const call = fetchSpy.mock.calls.findLast(([url]) => String(url).endsWith('/me/course-enrollments'));
  return call[1].body;
}

const VALID_CBU = '0170099220000067797370';

async function fillReceipt(user, container, overrides = {}) {
  const data = { operationNumber: '4821937', originAccount: VALID_CBU, ...overrides };
  await user.upload(container.querySelector('input[type="file"]'), new File(['%PDF'], 'comprobante.pdf', { type: 'application/pdf' }));
  await user.type(screen.getByLabelText('Número de operación'), data.operationNumber);
  await user.type(screen.getByLabelText('CBU o alias de origen'), data.originAccount);
}

function sendButton() {
  return screen.getByRole('button', { name: 'Enviar comprobante' });
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
    let enrollmentKey;
    server.use(
      http.put(`${API}/me`, async ({ request }) => {
        profileBody = await request.json();
        return HttpResponse.json({});
      }),
      http.post(`${API}/me/course-enrollments`, async ({ request }) => {
        enrollmentContentType = request.headers.get('content-type');
        enrollmentKey = request.headers.get('idempotency-key');
        return HttpResponse.json({ id: 90 }, { status: 201 });
      }),
    );
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    const { container } = renderWizard('curso');

    await fillValidData(user, { email: '  ana@demo.test ', dni: ' 40111222 ' });
    await waitFor(() => expect(continueButton()).toBeEnabled());
    await user.click(continueButton());

    expect(await screen.findByText('Monto a pagar')).toBeInTheDocument();
    expect(sendButton()).toBeDisabled();
    expect(
      screen.getByText('Para continuar falta: el comprobante de pago, el número de operación, el CBU o alias de origen.'),
    ).toBeInTheDocument();

    await fillReceipt(user, container);
    await waitFor(() => expect(sendButton()).toBeEnabled());
    await user.click(sendButton());
    expect(screen.queryByRole('button', { name: 'Sí, confirmar' })).not.toBeInTheDocument();

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
    const enrollmentForm = submittedEnrollment(fetchSpy);
    expect(enrollmentContentType).toContain('multipart/form-data');
    expect(enrollmentForm.get('courseId')).toBe('5');
    expect(enrollmentForm.get('declaredAmount')).toBe('500.00');
    expect(enrollmentForm.get('currency')).toBe('ARS');
    expect(enrollmentForm.get('transferDate')).toBe(todayIsoDate());
    expect(enrollmentForm.get('operationNumber')).toBe('4821937');
    expect(enrollmentForm.get('originAccount')).toBe(VALID_CBU);
    expect(enrollmentForm.get('holderName')).toBe('Ana Pérez');
    expect(enrollmentForm.get('bankName')).toBeNull();
    expect(enrollmentForm.get('file').name).toBe('comprobante.pdf');
    expect(enrollmentForm.has('comprobante')).toBe(false);
    expect(enrollmentKey).toBeTruthy();
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
    await fillReceipt(user, container);
    await user.click(sendButton());

    expect(await screen.findByText('Solicitud enviada', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(profileCalls).toBe(0);
  });

  async function goToPaymentStep(user) {
    await fillValidData(user);
    await waitFor(() => expect(continueButton()).toBeEnabled());
    await user.click(continueButton());
    await screen.findByText('Monto a pagar');
  }

  it('prefills the course fee as the transferred amount and the student name as the holder', async () => {
    const user = userEvent.setup();
    renderWizard('curso');
    await goToPaymentStep(user);

    expect(screen.getByLabelText('Monto transferido')).toHaveValue('500,00');
    expect(screen.getByLabelText('Titular de la cuenta de origen')).toHaveValue('Ana Pérez');
    expect(screen.getByText(/Transferencia de hoy/)).toBeInTheDocument();
  });

  it('sends the optional bank name when the student adds it', async () => {
    server.use(
      http.put(`${API}/me`, () => HttpResponse.json({})),
      http.post(`${API}/me/course-enrollments`, () => HttpResponse.json({ id: 90 }, { status: 201 })),
    );
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const user = userEvent.setup();
    const { container } = renderWizard('curso');
    await goToPaymentStep(user);
    await fillReceipt(user, container);
    await user.click(screen.getByRole('button', { name: /Agregar banco/ }));
    await user.type(screen.getByLabelText('Banco de origen (opcional)'), 'Banco Nación');
    await user.click(sendButton());

    expect(await screen.findByText('Solicitud enviada', {}, { timeout: 5000 })).toBeInTheDocument();
    expect(submittedEnrollment(fetchSpy).get('bankName')).toBe('Banco Nación');
  });

  it('shows a rejected file with its error and lets the student remove it', async () => {
    const user = userEvent.setup({ applyAccept: false });
    const { container } = renderWizard('curso');
    await goToPaymentStep(user);
    await user.upload(container.querySelector('input[type="file"]'), new File(['x'], 'foto.gif', { type: 'image/gif' }));

    expect(screen.getByText('foto.gif')).toBeInTheDocument();
    expect(screen.getByText('El comprobante tiene que ser un PDF, JPG o PNG.')).toBeInTheDocument();
    expect(sendButton()).toBeDisabled();

    await user.click(screen.getByRole('button', { name: 'Quitar foto.gif' }));
    expect(screen.queryByText('foto.gif')).not.toBeInTheDocument();
    expect(screen.getByText('Subí tu comprobante')).toBeInTheDocument();
  });

  it('shows the violation of the field returned by the server next to that field', async () => {
    server.use(
      http.put(`${API}/me`, () => HttpResponse.json({})),
      http.post(`${API}/me/course-enrollments`, () =>
        HttpResponse.json(
          { code: 'BANK_TRANSFER_DETAILS_REQUIRED', violations: [{ code: 'BANK_TRANSFER_HOLDER_NAME_REQUIRED' }] },
          { status: 400 },
        ),
      ),
    );
    const user = userEvent.setup();
    const { container } = renderWizard('curso');
    await goToPaymentStep(user);
    await fillReceipt(user, container);
    await user.click(sendButton());

    expect(await screen.findByText('Ingresá el titular de la cuenta de origen.')).toBeInTheDocument();
    expect(screen.getByText('Revisá los datos del comprobante.')).toBeInTheDocument();
  });

  it('explains a duplicated transfer and reuses the idempotency key only while the data is unchanged', async () => {
    const keys = [];
    server.use(
      http.put(`${API}/me`, () => HttpResponse.json({})),
      http.post(`${API}/me/course-enrollments`, ({ request }) => {
        keys.push(request.headers.get('idempotency-key'));
        return HttpResponse.json({ code: 'POSSIBLE_DUPLICATE_BANK_TRANSFER' }, { status: 409 });
      }),
    );
    const user = userEvent.setup();
    const { container } = renderWizard('curso');
    await goToPaymentStep(user);
    await fillReceipt(user, container);
    await user.click(sendButton());
    expect(await screen.findByText(/Ya tenemos registrada esa transferencia/)).toBeInTheDocument();

    await user.click(sendButton());
    await waitFor(() => expect(keys).toHaveLength(2));
    expect(keys[1]).toBe(keys[0]);

    await user.type(screen.getByLabelText('Número de operación'), '9');
    await user.click(sendButton());
    await waitFor(() => expect(keys).toHaveLength(3));
    expect(keys[2]).not.toBe(keys[0]);
  });
});
