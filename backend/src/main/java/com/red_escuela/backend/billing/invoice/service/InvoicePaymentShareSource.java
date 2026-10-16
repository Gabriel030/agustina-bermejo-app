package com.red_escuela.backend.billing.invoice.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface InvoicePaymentShareSource {

    Map<Long, List<InvoicePaymentShare>> findCurrentSharesByInvoiceId(Collection<Long> invoiceIds);
}
