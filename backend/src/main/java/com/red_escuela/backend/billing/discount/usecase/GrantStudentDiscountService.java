package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.service.StudentDiscountOperationRunner;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class GrantStudentDiscountService implements GrantStudentDiscountUseCase {
    private final StudentDiscountOperationRunner operationRunner;

    public GrantStudentDiscountService(StudentDiscountOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<StudentDiscount> grantStudentDiscount(EnrollmentReference enrollment, StudentDiscountGrantRequest request) {
        return operationRunner.runGrant(enrollment, request);
    }
}
