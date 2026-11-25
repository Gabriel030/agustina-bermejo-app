package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.invoice.service.CreateAdHocInvoiceForProgramEnrollmentTransaction;
import com.red_escuela.backend.billing.invoice.service.InvoiceView;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateAdHocInvoiceForProgramEnrollmentService implements CreateAdHocInvoiceForProgramEnrollmentUseCase {

    private final CreateAdHocInvoiceForProgramEnrollmentTransaction transaction;

    public CreateAdHocInvoiceForProgramEnrollmentService(CreateAdHocInvoiceForProgramEnrollmentTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public InvoiceView create(Long programEnrollmentId, CreateAdHocInvoiceRequest request) {
        return create(programEnrollmentId, request, java.util.UUID.randomUUID().toString());
    }

    @Override
    public InvoiceView create(Long programEnrollmentId, CreateAdHocInvoiceRequest request, String idempotencyKey) {
        return transaction.create(programEnrollmentId, request, idempotencyKey);
    }
}
