package com.red_escuela.backend.billing.support;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;

import java.util.List;

public final class InvoiceViews {

    private InvoiceViews() {
    }

    public static List<InvoiceDiscount> discountsOf(Invoice invoice) {
        return invoice.getLines().stream().map(InvoiceLine::getDiscount).filter(java.util.Objects::nonNull).toList();
    }
}
