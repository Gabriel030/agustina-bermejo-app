import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router-dom';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { GrantDiscountPage } from './GrantDiscountPage';
import { API, CATALOG, mockOffers } from './discountFixtures';
import { currentMonth, shiftMonth } from './grantForm';

const auth = vi.hoisted(() => ({ roles: ['DIRECTOR'] }));
vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { nombre: 'Ana', apellido: 'Pérez', roles: auth.roles } }),
}));

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('sonner', async (importOriginal) => ({ ...(await importOriginal()), toast: toastMock }));

vi.setConfig({ testTimeout: 30000 });

const THIS_MONTH = currentMonth();

function previewResponse(overrides) {
  return {
    allowed: true,
    violations: [],
    type: 'INDIVIDUAL',
    invoiceType: 'MONTHLY_INSTALLMENT',
    calculation: { type: 'NOMINAL', amount: { amount: '20000.00', currency: 'ARS' } },
    fromPeriod: THIS_MONTH,
    toPeriod: THIS_MONTH,
    nextInstallmentPeriod: shiftMonth(THIS_MONTH, 1),
    coveredPeriods: 1,
    priority: 1,
    discountDefinitionId: null,
    annualUpfrontCoversRange: false,
    enrollmentFeeAlreadyIssued: false,
    overlappingDiscountId: null,
    requiresDirectorApproval: false,
    ...overrides,
  };
}

function renderGrant(enrollment = 'program-4') {
  return renderWithProviders(
    <Routes>
      <Route path="/:role/alumnos/:id/descuentos/nuevo" element={<GrantDiscountPage />} />
      <Route path="/:role/alumnos/:id" element={<p>Legajo del alumno</p>} />
      <Route path="/:role/aprobaciones" element={<p>Bandeja de aprobaciones</p>} />
    </Routes>,
    { route: `/${auth.roles[0].toLowerCase()}/alumnos/9/descuentos/nuevo?enrollment=${enrollment}` },
  );
}

function mockEnvironment({ preview = previewResponse(), onPreview, grant, onGrant } = {}) {
  mockOffers();
  server.use(
    http.get(`${API}/students/9`, () => HttpResponse.json({ id: 9, nombre: 'Valentina', apellido: 'Suárez' })),
    http.get(`${API}/program-enrollments/4`, () => HttpResponse.json({ id: 4, programId: 2, programName: 'Enfermería', status: 'ENROLLED', studentId: 9, enrollmentDate: '2026-03-01T00:00:00', completionDate: '2027-12-15T00:00:00' })),
    http.get(`${API}/course-enrollments/11`, () => HttpResponse.json({ id: 11, courseId: 3, courseName: 'Primeros Auxilios', status: 'ENROLLED', studentId: 9, enrollmentDate: '2026-03-01T00:00:00', completionDate: '2026-12-15T00:00:00' })),
    http.get(`${API}/discount-definitions`, () => HttpResponse.json(CATALOG)),
    http.post(`${API}/program-enrollments/4/student-discounts/preview`, async ({ request }) => {
      onPreview?.(await request.json());
      return HttpResponse.json(preview);
    }),
    http.post(`${API}/course-enrollments/11/student-discounts/preview`, async ({ request }) => {
      onPreview?.(await request.json());
      return HttpResponse.json(preview);
    }),
    http.post(`${API}/program-enrollments/4/student-discounts`, async ({ request }) => {
      onGrant?.(await request.json());
      return HttpResponse.json(grant?.body ?? { id: 500, authorizationRequestId: null }, { status: grant?.status ?? 201 });
    }),
  );
}

async function fillIndividual(user, value = '20000', reason = 'Hermanos en la institución') {
  await user.type(await screen.findByRole('spinbutton', { name: 'Monto' }), value);
  await user.type(screen.getByRole('textbox', { name: 'Motivo' }), reason);
}

describe('GrantDiscountPage', () => {
  beforeEach(() => {
    auth.roles = ['DIRECTOR'];
    toastMock.success.mockClear();
    toastMock.error.mockClear();
  });

  it('shows the student and the enrollment, previews with the server and grants directly as director', async () => {
    const previews = [];
    let granted;
    mockEnvironment({ onPreview: (body) => previews.push(body), onGrant: (body) => (granted = body) });
    const user = userEvent.setup();
    renderGrant();

    expect(await screen.findByText(/Suárez, Valentina · Enfermería · carrera/)).toBeInTheDocument();
    expect(await screen.findByDisplayValue('Enfermería · carrera')).toHaveAttribute('readonly');
    await fillIndividual(user);

    expect(await screen.findByText(/Rige desde la cuota de/)).toBeInTheDocument();
    expect(previews.at(-1)).toMatchObject({
      type: 'INDIVIDUAL',
      percent: null,
      amount: { amount: '20000.00', currency: 'ARS' },
      invoiceType: 'MONTHLY_INSTALLMENT',
      fromPeriod: THIS_MONTH,
      toPeriod: THIS_MONTH,
    });
    await user.click(screen.getByRole('button', { name: 'Otorgar descuento' }));

    expect(await screen.findByText('Legajo del alumno')).toBeInTheDocument();
    expect(granted).toMatchObject({ type: 'INDIVIDUAL', discountDefinitionId: null, reason: 'Hermanos en la institución' });
    expect(toastMock.success).toHaveBeenCalledWith('Descuento otorgado.');
  });

  it('lists the violations that the preview reports and hides the effect', async () => {
    mockEnvironment({
      preview: previewResponse({
        allowed: false,
        violations: [{ code: 'STUDENT_DISCOUNT_OVERLAP', message: 'Ya hay una beca vigente de marzo a diciembre de 2026 sobre esta inscripción.' }],
      }),
    });
    const user = userEvent.setup();
    renderGrant();

    await fillIndividual(user);

    expect(await screen.findByText('Ya hay una beca vigente de marzo a diciembre de 2026 sobre esta inscripción.')).toBeInTheDocument();
    expect(screen.getByText('STUDENT_DISCOUNT_OVERLAP')).toBeInTheDocument();
    expect(screen.getByText('Corregí los problemas marcados para ver qué va a pasar.')).toBeInTheDocument();
  });

  it('proposes the discount as administrative and warns that it expires', async () => {
    auth.roles = ['ADMINISTRATIVO'];
    mockEnvironment({ grant: { status: 202, body: { authorizationRequestId: 77 } } });
    const user = userEvent.setup();
    renderGrant();

    expect(await screen.findByRole('heading', { name: 'Proponer descuento' })).toBeInTheDocument();
    expect(screen.getByText(/si nadie decide antes del primer día del período de inicio, la propuesta vence/i)).toBeInTheDocument();
    await fillIndividual(user);
    await user.click(screen.getByRole('button', { name: 'Enviar al director' }));

    expect(await screen.findByText('Legajo del alumno')).toBeInTheDocument();
    const [message, options] = toastMock.success.mock.calls[0];
    expect(message).toBe('Enviado al director para aprobación.');
    expect(options.action.label).toBe('Ver mis pedidos');
  });

  it('validates the value, the reason and the range before calling the server', async () => {
    let granted = false;
    mockEnvironment({ onGrant: () => (granted = true) });
    const user = userEvent.setup();
    renderGrant();

    await user.type(await screen.findByRole('spinbutton', { name: 'Monto' }), '0');
    await user.click(screen.getByRole('button', { name: 'Otorgar descuento' }));

    expect(await screen.findByText('El porcentaje o el monto debe ser mayor a cero.')).toBeInTheDocument();
    expect(screen.getByText('El motivo es obligatorio y admite hasta 450 caracteres.')).toBeInTheDocument();
    expect(toastMock.error).toHaveBeenCalledWith('Revisá los campos marcados.');
    expect(granted).toBe(false);
  });

  it('asks for the catalog scholarship, restricts it to monthly installments and sends its definition', async () => {
    let granted;
    mockEnvironment({ onGrant: (body) => (granted = body) });
    const user = userEvent.setup();
    renderGrant();

    await user.click(await screen.findByRole('radio', { name: 'Beca' }));
    expect(screen.getByRole('combobox', { name: 'Aplica a' })).toBeDisabled();
    await user.click(screen.getByRole('radio', { name: 'Porcentaje' }));
    await user.type(screen.getByRole('spinbutton', { name: 'Porcentaje' }), '15');
    await user.type(screen.getByRole('textbox', { name: 'Motivo' }), 'Situación económica');
    await user.click(screen.getByRole('button', { name: 'Otorgar descuento' }));
    expect(await screen.findByText('Elegí la beca del catálogo.')).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Beca del catálogo' }));
    expect(screen.queryByRole('option', { name: 'Beca vieja' })).not.toBeInTheDocument();
    await user.click(await screen.findByRole('option', { name: 'Beca' }));
    await user.click(screen.getByRole('button', { name: 'Otorgar descuento' }));

    await waitFor(() => expect(granted).toBeDefined());
    expect(granted).toMatchObject({ type: 'SCHOLARSHIP', discountDefinitionId: 1, percent: 15, amount: null, invoiceType: 'MONTHLY_INSTALLMENT' });
  });

  it('disables the scholarship and explains why when the annual payment covers the range', async () => {
    mockEnvironment({ preview: previewResponse({ annualUpfrontCoversRange: true }) });
    const user = userEvent.setup();
    renderGrant();

    await fillIndividual(user);

    expect(await screen.findByText('Con el pago anual confirmado no se puede otorgar una beca este año lectivo.')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Beca' })).toBeDisabled();
    expect(screen.getByText(/El pago anual confirmado cubre ese rango/)).toBeInTheDocument();
  });

  it('asks for a single month and explains the first enrollment fee rule for the enrollment fee', async () => {
    mockEnvironment({ preview: previewResponse({ invoiceType: 'ENROLLMENT_FEE', toPeriod: null }) });
    const user = userEvent.setup();
    renderGrant();

    await user.click(await screen.findByRole('combobox', { name: 'Aplica a' }));
    await user.click(await screen.findByRole('option', { name: 'Matrícula' }));

    expect(screen.queryByText('Hasta la cuota')).not.toBeInTheDocument();
    expect(screen.getByText('Período')).toBeInTheDocument();
    await fillIndividual(user, '50000');
    expect(await screen.findByText(/Solo la primera matrícula del año lectivo/)).toBeInTheDocument();
  });

  it('does not offer the enrollment fee for a course', async () => {
    mockEnvironment();
    const user = userEvent.setup();
    renderGrant('course-11');

    expect(await screen.findByDisplayValue('Primeros Auxilios · curso')).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: 'Aplica a' }));
    const options = (await screen.findAllByRole('option')).map((option) => option.textContent);
    expect(options).not.toContain('Matrícula');
    expect(options).toContain('Cuota mensual');
  });

  it('shows the violations that the server rejects on submit', async () => {
    mockEnvironment({
      grant: { status: 400, body: { code: 'INVALID_STUDENT_DISCOUNT', violations: [{ code: 'SCHOLARSHIP_START_BEFORE_NEXT_INSTALLMENT', message: 'La beca debe empezar en la próxima cuota a emitir.' }] } },
    });
    const user = userEvent.setup();
    renderGrant();

    await fillIndividual(user);
    await user.click(screen.getByRole('button', { name: 'Otorgar descuento' }));

    expect(await screen.findByText('La beca debe empezar en la próxima cuota a emitir.')).toBeInTheDocument();
    expect(toastMock.error).toHaveBeenCalledWith('Revisá los problemas marcados.');
    expect(screen.queryByText('Legajo del alumno')).not.toBeInTheDocument();
  });

  it('asks to pick an enrollment from the record when none is given', async () => {
    mockOffers();
    renderWithProviders(
      <Routes>
        <Route path="/:role/alumnos/:id/descuentos/nuevo" element={<GrantDiscountPage />} />
      </Routes>,
      { route: '/director/alumnos/9/descuentos/nuevo' },
    );
    server.use(http.get(`${API}/students/9`, () => HttpResponse.json({ id: 9, nombre: 'Valentina', apellido: 'Suárez' })));

    expect(await screen.findByText('Elegí una inscripción desde el legajo del alumno.')).toBeInTheDocument();
  });
});
