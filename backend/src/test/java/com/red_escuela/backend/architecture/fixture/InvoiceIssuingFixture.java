package com.red_escuela.backend.architecture.fixture;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;

public class InvoiceIssuingFixture {

    public Invoice forgeInvoice() {
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.PAID);
        return invoice;
    }
}
