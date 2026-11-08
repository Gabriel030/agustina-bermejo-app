import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { renderWithProviders } from '@/test/renderWithProviders';
import { createDeferred } from '@/test/deferred';
import { advanceView, obligationWith } from '@/test/fixtures/advanceEnrollmentFee';
import { AdvanceEnrollmentFeeStaffSection } from './AdvanceEnrollmentFeeStaffSection';

const API = 'http://localhost:8080';
const auth = vi.hoisted(() => ({ roles: ['ADMINISTRATIVO'] }));

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ usuario: { id: 9, roles: auth.roles }, roles: auth.roles }),
}));

function mount(advance, invoices = []) {
  server.use(http.get(`${API}/program-enrollments/3/enrollment-fee-status`, () => HttpResponse.json(obligationWith(advance))));
  return renderWithProviders(<AdvanceEnrollmentFeeStaffSection enrollmentId={3} invoices={invoices} />);
}

describe('AdvanceEnrollmentFeeStaffSection', () => {
  beforeEach(() => {
    auth.roles = ['ADMINISTRATIVO'];
  });

  it('generatesTheFeeForTheStudentAfterConfirming', async () => {
    const user = userEvent.setup();
    const posted = createDeferred();
    mount(advanceView());
    server.use(
      http.post(`${API}/program-enrollments/3/advance-enrollment-fee`, () => {
        posted.resolve(true);
        return HttpResponse.json({ invoiceId: 5, created: true });
      }),
    );
    expect(await screen.findByText('No emitida')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Generar la matrícula 2027' }));
    expect(screen.getByText(/Todavía no se cobra/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Generar la factura' }));
    await posted.promise;
  });

  it('doesNotCallTheServerUntilTheStaffConfirms', async () => {
    const user = userEvent.setup();
    let called = false;
    mount(advanceView());
    server.use(http.post(`${API}/program-enrollments/3/advance-enrollment-fee`, () => {
      called = true;
      return HttpResponse.json({ invoiceId: 5, created: true });
    }));
    await user.click(await screen.findByRole('button', { name: 'Generar la matrícula 2027' }));
    await user.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(called).toBe(false);
  });

  it('showsTheOverdueDebtReasonInsteadOfTheButton', async () => {
    mount(advanceView({ available: false, reasonCode: 'ADVANCE_FEE_OVERDUE_DEBT' }));
    expect(await screen.findByText(/cuotas vencidas/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Generar la matrícula/ })).not.toBeInTheDocument();
  });

  it('showsTheInvoiceStatusInsteadOfTheButtonOnceIssued', async () => {
    mount(advanceView({ available: false, reasonCode: 'ADVANCE_FEE_ALREADY_ISSUED', invoiceId: 5 }), [
      { id: 5, issuingReason: 'ENROLLMENT_FEE_ADVANCE', paymentStatus: 'PAID', documentStatus: 'ISSUED', dueDate: '2027-02-12' },
    ]);
    expect(await screen.findByText('Matrícula 2027')).toBeInTheDocument();
    expect(screen.queryByText('No emitida')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Generar la matrícula/ })).not.toBeInTheDocument();
  });

  it('rendersNothingForRolesWithoutBillingAccess', () => {
    auth.roles = ['PRECEPTOR'];
    const { container } = mount(advanceView());
    expect(container).toBeEmptyDOMElement();
  });
});
