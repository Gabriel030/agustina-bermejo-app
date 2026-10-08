package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.service.InvoiceView;

public interface CreateAdHocInvoiceForCourseEnrollmentUseCase {
    InvoiceView create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request);

    InvoiceView create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request, String idempotencyKey);
}
