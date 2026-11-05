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

  it('shows each credit note that financed the invoice as Nota de crédito N with its amount', () => {
    const invoice = {
      ...invoiceFixture(),
      creditNotes: [
        { creditNoteId: 7, creditNoteNumber: 'NC-0000007', amount: { amount: '2000.00', currency: 'ARS' }, description: 'Nota de crédito NC-0000007' },
        { creditNoteId: 8, creditNoteNumber: 'NC-0000008', amount: { amount: '500.00', currency: 'ARS' }, description: 'Nota de crédito NC-0000008' },
      ],
    };
    render(
      <MemoryRouter>
        <InvoiceDetail invoice={invoice} />
      </MemoryRouter>,
    );
    expect(screen.getByText('Nota de crédito NC-0000007')).toBeInTheDocument();
    expect(screen.getByText('Nota de crédito NC-0000008')).toBeInTheDocument();
    expect(screen.getAllByText(/Nota de crédito NC-/)).toHaveLength(2);
  });

  it('shows no credit note block when the invoice has no credit note sources', () => {
    render(
      <MemoryRouter>
        <InvoiceDetail invoice={{ ...invoiceFixture(), creditNotes: [] }} />
      </MemoryRouter>,
    );
    expect(screen.queryByText(/Nota de crédito/)).not.toBeInTheDocument();
  });
});
