package com.red_escuela.backend.billing.api;

import java.util.Collection;
import java.util.Map;

public interface AdmissionReceiptLookup {
    Map<Long, AdmissionReceiptSummary> findLatestReceiptByInvoiceIds(Collection<Long> invoiceIds);
}
