import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { AniosLectivosSection } from './AniosLectivosSection';
import { descripcionMatriculas } from '@/features/gestion-academica/lib/aniosLectivos';

const API = 'http://localhost:8080';

const TEMPLATE = {
  id: 3, programId: 12, termId: 7, recurringFeeInvoicesCount: 10, enrollmentFeeCount: 1, firstEnrollmentFeeMonth: 2,
  secondEnrollmentFeeMonth: null, enrollmentFeeLeadMonths: 1, lateEnrollmentFeeDueDays: 10, paysWhilePaused: false, blocksEnrollment: true, blocksExamRegistration: true,
  blocksCertificates: true, requireEnrollmentFeeForFinalExamOnlyStudents: false,
};

function mockApi(alerts = []) {
  server.use(
    http.get(`${API}/programs/12/year-billing-templates`, () => HttpResponse.json([TEMPLATE])),
    http.get(`${API}/terms`, () => HttpResponse.json([{ id: 7, year: 2027 }])),
    http.get(`${API}/billing/configuration-alerts`, () => HttpResponse.json(alerts)),
  );
}

describe('AniosLectivosSection', () => {
  it('describes how many enrollment fees each lective year charges', () => {
    expect(descripcionMatriculas(TEMPLATE)).toBe('1 matrícula por año (Febrero)');
    expect(descripcionMatriculas({ ...TEMPLATE, enrollmentFeeCount: 2, secondEnrollmentFeeMonth: 7 })).toBe('2 matrículas por año (Febrero y Julio)');
  });

  it('warns that nothing is emitted when the program has an open configuration alert', async () => {
    mockApi([{ id: 1, programId: 12, period: '2027-02', affectedEnrollments: 4 }]);
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
    renderWithProviders(<AniosLectivosSection programId={12} />);

    await user.click(await screen.findByRole('button', { name: /Configurar cobro de Año lectivo 2027/ }));
    await user.selectOptions(screen.getByLabelText('Cantidad de matrículas por año'), '2');
    await user.selectOptions(screen.getByLabelText('Mes de la segunda matrícula'), '2');
    await user.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(body).toBeUndefined();

    await user.selectOptions(screen.getByLabelText('Mes de la segunda matrícula'), '7');
    await user.click(screen.getByLabelText('Inscripción a mesas de examen'));
    await user.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(body).toMatchObject({
      enrollmentFeeCount: 2, firstEnrollmentFeeMonth: 2, secondEnrollmentFeeMonth: 7, enrollmentFeeLeadMonths: 1, lateEnrollmentFeeDueDays: 10, paysWhilePaused: false,
      blocksEnrollment: true, blocksExamRegistration: false, blocksCertificates: true,
      requireEnrollmentFeeForFinalExamOnlyStudents: false,
    }));
  });
});
