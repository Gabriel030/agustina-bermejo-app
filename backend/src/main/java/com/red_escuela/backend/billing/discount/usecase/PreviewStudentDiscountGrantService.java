package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantPreview;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.service.StudentDiscountOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class PreviewStudentDiscountGrantService implements PreviewStudentDiscountGrantUseCase {
    private final StudentDiscountOperationRunner operationRunner;

    public PreviewStudentDiscountGrantService(StudentDiscountOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public StudentDiscountGrantPreview previewStudentDiscountGrant(EnrollmentReference enrollment, StudentDiscountGrantRequest request) {
        return operationRunner.previewGrant(enrollment, request);
    }
}
