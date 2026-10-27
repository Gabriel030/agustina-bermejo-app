package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.StudentCreditBalance;
import com.red_escuela.backend.billing.payment.service.RefundPreviewService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class DescribeStudentCreditBalanceService implements DescribeStudentCreditBalanceUseCase {
    private final RefundPreviewService previewService;

    public DescribeStudentCreditBalanceService(RefundPreviewService previewService) {
        this.previewService = previewService;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    @Transactional(readOnly = true)
    public StudentCreditBalance describeOwnCreditBalance(Long currentStudentId, Currency currency) {
        return previewService.describeCreditBalanceOf(currentStudentId, currency);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @Transactional(readOnly = true)
    public StudentCreditBalance describeCreditBalanceOfStudent(Long studentId, Currency currency) {
        return previewService.describeCreditBalanceOfExistingStudent(studentId, currency);
    }
}
