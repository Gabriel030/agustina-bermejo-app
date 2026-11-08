import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AniosLectivosSection } from './AniosLectivosSection';
import { descripcionMatriculas } from '@/features/academic-structure/lib/aniosLectivos';
import { termFixture } from '@/test/fixtures/terms';

const API = 'http://localhost:8080';

const TEMPLATE = {
  id: 3, programId: 12, termId: 7, recurringFeeInvoicesCount: 10, enrollmentFeeCount: 1, firstEnrollmentFeeMonth: 2,
  secondEnrollmentFeeMonth: null, enrollmentFeeLeadMonths: 1, lateEnrollmentFeeDueDays: 10, paysWhilePaused: false, blocksEnrollment: true, blocksExamRegistration: true,
  blocksCertificates: true, requireEnrollmentFeeForFinalExamOnlyStudents: false,
};

function mockApi(alerts = []) {
  server.use(
    http.get(`${API}/programs/12/year-billing-templates`, () => HttpResponse.json([TEMPLATE])),
    http.get(`${API}/terms`, () => HttpResponse.json([termFixture({ id: 7, year: 2027 })])),
    http.get(`${API}/billing/configuration-alerts`, () => HttpResponse.json(alerts)),
  );
}

describe('AniosLectivosSection', () => {
  it('describes how many enrollment fees each lective year charges', () => {
    expect(descripcionMatriculas(TEMPLATE)).toBe('1 matrícula por año (Febrero)');
    expect(descripcionMatriculas({ ...TEMPLATE, enrollmentFeeCount: 2, secondEnrollmentFeeMonth: 7 })).toBe('2 matrículas por año (Febrero y Julio)');
  });

  it('warns that nothing is emitted when the program has an open configuration alert', async () => {
    mockApi([{ id: 1, programId: 12, period: '2027-02', affectedEnrollments: 4, programName: 'Carrera', reason: 'MISSING_CONFIGURATION', detectedAt: '2027-01-01T00:00:00', lastDetectedAt: '2027-01-02T00:00:00' }]);
    renderWithProviders(<AniosLectivosSection programId={12} />);

    expect(await screen.findByText(/no tiene un año lectivo configurado para 2027-02/)).toBeInTheDocument();
  });

  it('rejects a second month that is not after the first and sends the full configuration otherwise', async () => {
    mockApi();
    let body;
    server.use(http.patch(`${API}/year-billing-templates/3/configuration`, async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(TEMPLATE);
    }));
    const user = userEvent.setup();
    renderWithProviders(<AniosLectivosSection programId={12} canEdit />);

    await user.click(await screen.findByRole('button', { name: /Configurar cobro de Año lectivo 2027/ }));
    await user.selectOptions(screen.getByLabelText('Cantidad de matrículas por año'), '2');
    await user.selectOptions(screen.getByLabelText('Mes de la segunda matrícula'), '2');
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    expect(screen.queryByRole('button', { name: 'Guardar cambios' })).not.toBeInTheDocument();
    expect(body).toBeUndefined();

    await user.selectOptions(screen.getByLabelText('Mes de la segunda matrícula'), '7');
    await user.click(screen.getByLabelText('Inscripción a mesas de examen'));
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(body).toMatchObject({
      enrollmentFeeCount: 2, firstEnrollmentFeeMonth: 2, secondEnrollmentFeeMonth: 7, enrollmentFeeLeadMonths: 1, lateEnrollmentFeeDueDays: 10, paysWhilePaused: false,
      blocksEnrollment: true, blocksExamRegistration: false, blocksCertificates: true,
      requireEnrollmentFeeForFinalExamOnlyStudents: false,
    }));
  });

  it('lists every violation message returned by a rejected configuration', async () => {
    mockApi();
    server.use(http.patch(`${API}/year-billing-templates/3/configuration`, () => HttpResponse.json({
      code: 'BILLING_CONFIGURATION_FROZEN',
      violations: [{ code: 'ENROLLMENT_FEE_COUNT_FROZEN', message: 'Ya se emitió una matrícula de este año: no se puede cambiar la cantidad de matrículas.' }],
    }, { status: 409 })));
    const user = userEvent.setup();
    renderWithProviders(<AniosLectivosSection programId={12} canEdit />);

    await user.click(await screen.findByRole('button', { name: /Configurar cobro de Año lectivo 2027/ }));
    await user.click(screen.getByLabelText('Pedido de certificados'));
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Ya se emitió una matrícula de este año');
  });

  it('shows the configuration read-only, with no save button, when the user cannot edit', async () => {
    mockApi();
    renderWithProviders(<AniosLectivosSection programId={12} />);

    expect(await screen.findByText(/Año lectivo 2027/)).toBeInTheDocument();
    expect(screen.getByText('Solo el director puede modificar la configuración de cobro.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Configurar cobro/ })).not.toBeInTheDocument();
  });

  it('shows the opening date of the advance enrollment fee and both alerts', async () => {
    server.use(
      http.get(`${API}/programs/12/year-billing-templates`, () => HttpResponse.json([
        { ...TEMPLATE, advanceEnrollmentFeeOpensOn: '2026-11-01', advanceOpeningAfterRegularEmission: true, nextYearPriceMissing: true },
      ])),
      http.get(`${API}/terms`, () => HttpResponse.json([termFixture({ id: 7, year: 2027 })])),
      http.get(`${API}/billing/configuration-alerts`, () => HttpResponse.json([])),
    );
    renderWithProviders(<AniosLectivosSection programId={12} canEdit />);

    expect(await screen.findByText('Matrícula anticipada: abre el 01/11/2026')).toBeInTheDocument();
    expect(screen.getByText(/Falta el precio de la matrícula del año siguiente/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Cargar el precio' })).toHaveAttribute('href', '/preceptor/precios');
    expect(screen.getByText(/posterior a la emisión regular/)).toBeInTheDocument();
  });

  it('sends the chosen opening date and explains every field with an info button', async () => {
    mockApi();
    let body;
    server.use(http.patch(`${API}/year-billing-templates/3/configuration`, async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(TEMPLATE);
    }));
    const user = userEvent.setup();
    renderWithProviders(<AniosLectivosSection programId={12} canEdit />);

    await user.click(await screen.findByRole('button', { name: /Configurar cobro de Año lectivo 2027/ }));
    await user.click(screen.getByRole('button', { name: 'Qué significa Meses de anticipación' }));
    expect(await screen.findByText(/se genera en/)).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Matrícula anticipada · Abre'), 'date');
    const dateInput = screen.getByLabelText('Fecha de apertura de la matrícula anticipada');
    await user.clear(dateInput);
    await user.type(dateInput, '2026-09-01');
    await user.click(screen.getByRole('button', { name: 'Revisar cambios' }));
    expect(screen.getAllByText('01/09/2026').length).toBeGreaterThan(1);
    await user.click(screen.getByRole('button', { name: 'Guardar cambios' }));

    await waitFor(() => expect(body).toMatchObject({ advanceEnrollmentFeeOpensOn: '2026-09-01' }));
  });
});
