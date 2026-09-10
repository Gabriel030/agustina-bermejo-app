package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.Invoice;

public interface CreateAdHocInvoiceForProgramEnrollmentUseCase {
    Invoice create(Long programEnrollmentId, CreateAdHocInvoiceRequest request);
}
