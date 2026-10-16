package com.red_escuela.backend.billing.invoice.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface InvoiceCreditNoteShareSource {

    Map<Long, List<InvoiceCreditNoteSourceShare>> findCurrentCreditNoteSharesByInvoiceId(Collection<Long> invoiceIds);
}
