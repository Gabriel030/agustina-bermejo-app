package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.invoice.service.CreateAdHocInvoiceForCourseEnrollmentTransaction;
import com.red_escuela.backend.billing.invoice.service.InvoiceView;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateAdHocInvoiceForCourseEnrollmentService implements CreateAdHocInvoiceForCourseEnrollmentUseCase {

    private final CreateAdHocInvoiceForCourseEnrollmentTransaction transaction;

    public CreateAdHocInvoiceForCourseEnrollmentService(CreateAdHocInvoiceForCourseEnrollmentTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public InvoiceView create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request) {
        return create(courseEnrollmentId, request, java.util.UUID.randomUUID().toString());
    }

    @Override
    public InvoiceView create(Long courseEnrollmentId, CreateAdHocInvoiceRequest request, String idempotencyKey) {
        return transaction.create(courseEnrollmentId, request, idempotencyKey);
    }
}
