import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { DiscountDefinitionFormPage } from './DiscountDefinitionFormPage';
import { API, mockOffers } from './discountFixtures';

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

vi.setConfig({ testTimeout: 30000 });

function renderForm(mode) {
  const path = mode === 'promotion' ? 'promociones/nueva' : 'nuevo';
  return renderWithProviders(
    <Routes>
      <Route path={`/:role/descuentos/${path}`} element={<DiscountDefinitionFormPage mode={mode} />} />
      <Route path="/:role/descuentos" element={<p>Lista de descuentos</p>} />
      <Route path="/:role/aprobaciones" element={<p>Bandeja de aprobaciones</p>} />
    </Routes>,
    { route: `/${auth.roles[0].toLowerCase()}/descuentos/${path}` },
  );
}

function mockValidation(violations = [], onRequest) {
  server.use(
    http.post(`${API}/discount-definitions/validation`, async ({ request }) => {
      onRequest?.(await request.json());
      return HttpResponse.json({ violations });
    }),
  );
}

function mockCreate(status, body, onRequest) {
  server.use(
    http.post(`${API}/discount-definitions`, async ({ request }) => {
      onRequest?.(await request.json());
      return HttpResponse.json(body, { status });
    }),
  );
}

async function fillBasics(user, name = 'Pago anual 2027', value = '15') {
  await user.type(screen.getByLabelText('Nombre'), name);
  await user.type(screen.getByRole('spinbutton', { name: 'Porcentaje' }), value);
}

async function pick(user, label, option) {
  await user.click(screen.getByRole('combobox', { name: label }));
  await user.click(await screen.findByRole('option', { name: option }));
}

describe('DiscountDefinitionFormPage', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    toastMock.success.mockClear();
    toastMock.error.mockClear();
    mockOffers();
    mockValidation();
  });

  it('lets the director create an annual upfront discount directly and returns to the list', async () => {
    let created;
    mockCreate(201, { id: 12, authorizationRequestId: null }, (body) => (created = body));
    const user = userEvent.setup();
    renderForm('discount');

    await fillBasics(user);
    await user.click(screen.getByRole('button', { name: 'Crear descuento' }));

    expect(await screen.findByText('Lista de descuentos')).toBeInTheDocument();
    expect(toastMock.success).toHaveBeenCalledWith('Descuento creado.');
    expect(created).toMatchObject({
      name: 'Pago anual 2027',
      discountType: 'ANNUAL_UPFRONT',
      applicableInvoiceType: 'ANNUAL_UPFRONT',
      calculation: { type: 'PROPORTIONAL', ratePercent: 15 },
      availableUntil: null,
      priority: 1,
      capPerInvoice: null,
      quota: null,
      programId: null,
    });
  });

  it('sends the proposal of an administrative to the director and links to their requests', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    mockCreate(202, { authorizationRequestId: 31 });
    const user = userEvent.setup();
    renderForm('discount');

    expect(screen.getByText(/queda pendiente hasta que el director la apruebe/)).toBeInTheDocument();
    await fillBasics(user);
    await user.click(screen.getByRole('button', { name: 'Enviar al director' }));

    expect(await screen.findByText('Lista de descuentos')).toBeInTheDocument();
    const [message, options] = toastMock.success.mock.calls[0];
    expect(message).toBe('Enviado al director para aprobación.');
    expect(options.action.label).toBe('Ver mis pedidos');
  });

  it('blocks the submit and marks the invalid fields without calling the server', async () => {
    let created = false;
    mockCreate(201, { id: 1 }, () => (created = true));
    const user = userEvent.setup();
    renderForm('discount');

    await user.type(screen.getByRole('spinbutton', { name: 'Porcentaje' }), '130');
    await user.click(screen.getByRole('button', { name: 'Crear descuento' }));

    expect(await screen.findByText('El nombre es obligatorio y admite hasta 200 caracteres.')).toBeInTheDocument();
    expect(screen.getByText('El porcentaje va de 0 a 100.')).toBeInTheDocument();
    expect(toastMock.error).toHaveBeenCalledWith('Revisá los campos marcados.');
    expect(created).toBe(false);
  });

  it('does not ask for a value on scholarships and sends no calculation', async () => {
    let created;
    mockCreate(201, { id: 2 }, (body) => (created = body));
    const user = userEvent.setup();
    renderForm('discount');

    await pick(user, 'Tipo', 'Beca');
    expect(screen.getByDisplayValue('Lo fija cada otorgamiento')).toHaveAttribute('readonly');
    expect(screen.queryByRole('spinbutton', { name: 'Porcentaje' })).not.toBeInTheDocument();
    expect(screen.getByText('El valor se fija en cada otorgamiento.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Nombre'), 'Beca social');
    await user.click(screen.getByRole('button', { name: 'Crear descuento' }));

    await waitFor(() => expect(created).toBeDefined());
    expect(created).toMatchObject({ discountType: 'SCHOLARSHIP', applicableInvoiceType: 'MONTHLY_INSTALLMENT', calculation: null });
  });

  it('validates live with the server and lists the violations in the panel', async () => {
    const validated = [];
    mockValidation([{ code: 'DISCOUNT_TYPE_NOT_IN_CATALOG', message: 'Este tipo de promoción no se ofrece en el catálogo.' }], (body) => validated.push(body));
    const user = userEvent.setup();
    renderForm('discount');

    expect(screen.getByText('Completá los datos para revisar las reglas del catálogo.')).toBeInTheDocument();
    await fillBasics(user);

    expect(await screen.findByText('1 problema')).toBeInTheDocument();
    expect(screen.getByText('Este tipo de promoción no se ofrece en el catálogo.')).toBeInTheDocument();
    expect(validated.at(-1)).toMatchObject({ name: 'Pago anual 2027', calculation: { ratePercent: 15 } });
  });

  it('shows the example with the cap applied', async () => {
    const user = userEvent.setup();
    renderForm('discount');

    await fillBasics(user, 'Pago anual', '50');
    await user.type(screen.getByLabelText('Tope por factura (opcional)'), '1000000');

    expect(await screen.findByText('Descuento (con tope)')).toBeInTheDocument();
    expect(screen.getByText(/−\s*\$\s*1\.000\.000,00/)).toBeInTheDocument();
  });

  it('shows the violations that the server rejects on submit', async () => {
    mockCreate(400, { code: 'INVALID_DISCOUNT_DEFINITION', violations: [{ code: 'DISCOUNT_TYPE_NOT_IN_CATALOG', message: 'Este tipo de promoción no se ofrece en el catálogo.' }] });
    const user = userEvent.setup();
    renderForm('discount');

    await fillBasics(user);
    await user.click(screen.getByRole('button', { name: 'Crear descuento' }));

    expect(await screen.findByText('El catálogo no aceptó los datos')).toBeInTheDocument();
    expect(toastMock.error).toHaveBeenCalledWith('Revisá las reglas del catálogo.');
    expect(screen.queryByText('Lista de descuentos')).not.toBeInTheDocument();
  });

  describe('promotion', () => {
    it('requires scope, enrollment window and quota', async () => {
      const user = userEvent.setup();
      renderForm('promotion');

      await fillBasics(user, 'Cohorte febrero 2027', '25');
      await user.click(screen.getByRole('button', { name: 'Crear promoción' }));

      expect(await screen.findByText('Elegí la carrera.')).toBeInTheDocument();
      expect(screen.getAllByText('La ventana de inscripción es obligatoria.')).toHaveLength(2);
      expect(screen.getByText('El cupo es de al menos una persona.')).toBeInTheDocument();
    });

    it('limits the applies to options, hides the type and creates the promotion with its scope', async () => {
      let created;
      mockCreate(201, { id: 30 }, (body) => (created = body));
      const user = userEvent.setup();
      renderForm('promotion');

      expect(screen.queryByRole('combobox', { name: 'Tipo' })).not.toBeInTheDocument();
      await user.click(screen.getByRole('combobox', { name: 'Aplica a' }));
      const options = (await screen.findAllByRole('option')).map((option) => option.textContent);
      expect(options).toEqual(['Cuota mensual', 'Matrícula', 'Materia recursada']);
      await user.keyboard('{Escape}');

      await fillBasics(user, 'Cohorte febrero 2027', '25');
      await user.type(screen.getByLabelText('Cantidad de cuotas'), '3');
      await pick(user, 'Carrera', 'Enfermería');
      expect(screen.queryByRole('option', { name: 'Programa viejo' })).not.toBeInTheDocument();
      await pick(user, 'Cohorte (opcional)', 'Febrero 2027');
      await user.type(screen.getByLabelText('Cupo'), '30');
      await user.type(screen.getByLabelText('Inscripción desde'), '2026-12-01');
      await user.type(screen.getByLabelText('Inscripción hasta'), '2027-02-28');
      await user.click(screen.getByRole('button', { name: 'Crear promoción' }));

      await waitFor(() => expect(created).toBeDefined());
      expect(created).toMatchObject({
        name: 'Cohorte febrero 2027',
        discountType: 'COHORT_PROMOTION',
        applicableInvoiceType: 'MONTHLY_INSTALLMENT',
        calculation: { type: 'PROPORTIONAL', ratePercent: 25 },
        installmentsTotal: 3,
        programId: 2,
        programCohortId: 31,
        courseId: null,
        enrollmentWindowFrom: '2026-12-01',
        enrollmentWindowTo: '2027-02-28',
        quota: 30,
      });
      expect(toastMock.success).toHaveBeenCalledWith('Promoción creada.');
    });

    it('hides the installments for a fee that is not a monthly installment and offers courses as scope', async () => {
      const user = userEvent.setup();
      renderForm('promotion');

      expect(screen.getByLabelText('Cantidad de cuotas')).toBeInTheDocument();
      await pick(user, 'Aplica a', 'Matrícula');
      expect(screen.queryByLabelText('Cantidad de cuotas')).not.toBeInTheDocument();
      await user.click(screen.getByRole('radio', { name: 'Curso' }));
      await pick(user, 'Curso', 'Primeros Auxilios');
      expect(screen.queryByLabelText('Cohorte (opcional)')).not.toBeInTheDocument();
    });

    it('warns when the enrollment window ends after the promotion is available', async () => {
      const user = userEvent.setup();
      renderForm('promotion');

      await fillBasics(user, 'Cohorte', '10');
      await pick(user, 'Carrera', 'Enfermería');
      await user.type(screen.getByLabelText('Cupo'), '5');
      await user.type(screen.getByLabelText('Disponible hasta'), '2026-11-15');
      await user.type(screen.getByLabelText('Inscripción desde'), '2026-11-01');
      await user.type(screen.getByLabelText('Inscripción hasta'), '2026-11-20');

      expect(await screen.findByText(/termina después de la disponibilidad de la promoción/)).toBeInTheDocument();
    });

    it('sends the promotion proposal of an administrative with the promotion vocabulary', async () => {
      auth.roles = ['ADMINISTRATIVO'];
      renderForm('promotion');

      expect(screen.getByRole('heading', { name: 'Nueva promoción de cohorte' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Enviar al director' })).toBeInTheDocument();
      expect(screen.getByText(/Una promoción cubre un solo tipo de factura/)).toBeInTheDocument();
    });
  });
});
