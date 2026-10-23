package com.red_escuela.backend.billing.operation;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ReceiptInvoiceIdsLookup {

    Map<Long, List<Long>> findInvoiceIdsByPaymentId(Collection<Long> paymentIds);

    Map<Long, List<ReceiptSelectedInvoice>> findSelectedInvoicesByPaymentId(Collection<Long> paymentIds);
}
