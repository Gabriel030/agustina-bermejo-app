package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;

public interface GrantStudentDiscountUseCase {
    BillingOperationOutcome<StudentDiscount> grantStudentDiscount(EnrollmentReference enrollment, StudentDiscountGrantRequest request);
}
