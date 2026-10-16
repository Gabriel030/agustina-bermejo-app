package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;

public interface RevokeStudentDiscountUseCase {
    BillingOperationOutcome<StudentDiscount> revokeStudentDiscount(Long studentDiscountId, String reason);
}
