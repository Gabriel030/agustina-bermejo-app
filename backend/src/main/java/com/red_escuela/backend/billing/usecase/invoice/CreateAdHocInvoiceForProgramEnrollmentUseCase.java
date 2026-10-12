package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.invoice.service.InvoiceView;

public interface CreateAdHocInvoiceForProgramEnrollmentUseCase {
    InvoiceView create(Long programEnrollmentId, CreateAdHocInvoiceRequest request);

    InvoiceView create(Long programEnrollmentId, CreateAdHocInvoiceRequest request, String idempotencyKey);
}
