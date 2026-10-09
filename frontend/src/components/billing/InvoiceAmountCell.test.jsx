import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { InvoiceAmountCell } from '@/components/billing/InvoiceAmountCell';
import { invoiceFixture } from '@/test/fixtures/invoices';

describe('InvoiceAmountCell', () => {
  it('shows the outstanding balance on every unpaid invoice', () => {
    render(<InvoiceAmountCell invoice={invoiceFixture({ total: '48000.00' })} />);
    expect(screen.getByText('Saldo $ 48.000,00')).toBeInTheDocument();
  });

  it('does not show a balance on a paid invoice', () => {
    render(<InvoiceAmountCell invoice={invoiceFixture({ paid: '48000.00', paymentStatus: 'PAID' })} />);
    expect(screen.queryByText(/Saldo/)).not.toBeInTheDocument();
  });

  it('flags an amount that includes a discount', () => {
    const discount = {
      discountName: 'Beca',
      discountSource: 'SCHOLARSHIP',
      discountAmount: { amount: '4800.00', currency: 'ARS' },
    };
    render(<InvoiceAmountCell invoice={invoiceFixture({ total: '43200.00', discount })} />);
    expect(screen.getByText('con descuento')).toBeInTheDocument();
  });

  it('flags an amount that includes a late fee line', () => {
    const invoice = invoiceFixture();
    invoice.lines.push({ ...invoice.lines[0], lineNumber: 2, feeType: 'LATE_FEE' });
    render(<InvoiceAmountCell invoice={invoice} />);
    expect(screen.getByText('incluye recargo')).toBeInTheDocument();
  });
});
