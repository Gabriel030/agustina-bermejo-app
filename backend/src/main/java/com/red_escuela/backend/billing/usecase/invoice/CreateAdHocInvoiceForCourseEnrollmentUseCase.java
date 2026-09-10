package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.Invoice;

public interface CreateAdHocInvoiceForCourseEnrollmentUseCase {
    Invoice create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request);
}
