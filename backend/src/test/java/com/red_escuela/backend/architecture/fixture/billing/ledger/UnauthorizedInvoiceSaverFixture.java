package com.red_escuela.backend.architecture.fixture.billing.ledger;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;

public class UnauthorizedInvoiceSaverFixture {

    private InvoiceRepository invoiceRepository;

    public void saveInvoice(Invoice invoice) {
        invoiceRepository.save(invoice);
    }
}
