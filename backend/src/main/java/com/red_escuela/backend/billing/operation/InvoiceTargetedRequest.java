package com.red_escuela.backend.billing.operation;

import java.util.List;

public interface InvoiceTargetedRequest {
    List<Long> targetedInvoiceIds();
}
