package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.entity.Invoice;

public interface ApplyInvoiceAdjustmentUseCase {
    Invoice apply(ApplyInvoiceAdjustmentCommand command);
}
