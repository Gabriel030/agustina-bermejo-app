package com.red_escuela.backend.billing.invoice.service;

import java.util.Collection;

public interface InvoicePaymentShareSource {

    InvoicePaymentShares findCurrentSharesByInvoiceId(Collection<Long> invoiceIds);
}
