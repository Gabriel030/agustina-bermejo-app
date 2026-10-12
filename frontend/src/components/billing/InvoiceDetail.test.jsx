import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { InvoiceDetail } from '@/components/billing/InvoiceDetail';
import { invoiceFixture } from '@/test/fixtures/invoices';

function multiLineInvoice() {
  const invoice = invoiceFixture({ total: '48000.00' });
  invoice.lines.push({ ...invoice.lines[0], lineNumber: 2, feeType: 'LATE_FEE', description: 'Recargo por mora' });
  return invoice;
}

describe('InvoiceDetail', () => {
  it('shows the line breakdown when the invoice has more than one line', () => {
    render(
      <MemoryRouter>
        <InvoiceDetail invoice={multiLineInvoice()} />
      </MemoryRouter>,
    );
    expect(screen.getByText('Recargo por mora')).toBeInTheDocument();
    expect(screen.getByText('Total')).toBeInTheDocument();
  });

  it('hides the document status from the student and shows it to the administrative', () => {
    const invoice = invoiceFixture();
    const { rerender } = render(
      <MemoryRouter>
        <InvoiceDetail invoice={invoice} />
      </MemoryRouter>,
    );
    expect(screen.queryByText(/Emitida/)).not.toBeInTheDocument();
    expect(screen.getByText(/emitida el 10\/09\/2026/)).toBeInTheDocument();
    rerender(
      <MemoryRouter>
        <InvoiceDetail invoice={invoice} showDocumentStatus />
      </MemoryRouter>,
    );
    expect(screen.getByText(/· Emitida/)).toBeInTheDocument();
  });
});
