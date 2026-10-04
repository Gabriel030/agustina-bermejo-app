import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AvisoMatricula } from './AvisoMatricula';
import { obligacionPendiente } from '@/lib/matricula';

describe('AvisoMatricula', () => {
  it('shows the pay button with the due date and sends the invoice id when the fee is pending', async () => {
    const onPagar = vi.fn();
    render(<AvisoMatricula obligacion={{ status: 'PENDING', invoiceId: 55, dueDate: '2027-03-10' }} onPagar={onPagar} />);

    expect(screen.getByText(/Para inscribirte tenés que pagar la matrícula \(vence el 10\/03\/2027\)/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Pagar matrícula' }));
    expect(onPagar).toHaveBeenCalledWith(55);
  });

  it('does not offer payment while the receipt is under review', () => {
    render(<AvisoMatricula obligacion={{ status: 'IN_REVIEW', invoiceId: 55 }} onPagar={() => {}} />);

    expect(screen.getByText(/comprobante de matrícula está en revisión/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Pagar matrícula' })).not.toBeInTheDocument();
  });

  it('explains a partial payment and keeps the pay button', () => {
    render(<AvisoMatricula obligacion={{ status: 'PARTIAL', invoiceId: 7 }} onPagar={() => {}} />);

    expect(screen.getByText(/Te falta completar el pago de la matrícula/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pagar matrícula' })).toBeInTheDocument();
  });

  it('only blocks on issued unsatisfied obligations and ignores paid, exempted and not issued ones', () => {
    expect(obligacionPendiente([
      { status: 'PAID', satisfied: true },
      { status: 'EXEMPTED', satisfied: true },
      { status: 'NOT_ISSUED', satisfied: false },
    ])).toBeNull();
    expect(obligacionPendiente([{ status: 'NOT_ISSUED', satisfied: false }, { status: 'PENDING', satisfied: false, invoiceId: 3 }]).invoiceId).toBe(3);
    expect(obligacionPendiente(undefined)).toBeNull();
  });
});
