package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.service.StudentDiscountOperationRunner;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RevokeStudentDiscountService implements RevokeStudentDiscountUseCase {
    private final StudentDiscountOperationRunner operationRunner;

    public RevokeStudentDiscountService(StudentDiscountOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<StudentDiscount> revokeStudentDiscount(Long studentDiscountId, String reason) {
        return operationRunner.runRevocation(studentDiscountId, reason);
    }
}
