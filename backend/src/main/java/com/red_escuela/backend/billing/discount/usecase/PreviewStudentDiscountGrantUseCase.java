package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantPreview;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;

public interface PreviewStudentDiscountGrantUseCase {
    StudentDiscountGrantPreview previewStudentDiscountGrant(EnrollmentReference enrollment, StudentDiscountGrantRequest request);
}
