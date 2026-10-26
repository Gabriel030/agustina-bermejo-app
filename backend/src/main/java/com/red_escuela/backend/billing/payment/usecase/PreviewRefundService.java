package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.billing.payment.domain.RefundEligibility;
import com.red_escuela.backend.billing.payment.domain.RefundPlanOutcome;
import com.red_escuela.backend.billing.payment.dto.RequestRefundRequest;
import com.red_escuela.backend.billing.payment.service.RefundPreviewService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Currency;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class PreviewRefundService implements PreviewRefundUseCase {
    private final RefundPreviewService previewService;

    public PreviewRefundService(RefundPreviewService previewService) {
        this.previewService = previewService;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    @Transactional(readOnly = true)
    public RefundPlanOutcome previewOwnRefund(Long currentStudentId, RequestRefundRequest request) {
        return previewService.previewRefundOf(currentStudentId, request.amount(), destinationOf(request));
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @Transactional(readOnly = true)
    public RefundPlanOutcome previewRefundOnBehalfOfStudent(Long studentId, RequestRefundRequest request) {
        return previewService.previewRefundOfExistingStudent(studentId, request.amount(), destinationOf(request));
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    @Transactional(readOnly = true)
    public RefundEligibility describeOwnRefundEligibility(Long currentStudentId, Currency currency) {
        return previewService.describeEligibilityOf(currentStudentId, currency);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @Transactional(readOnly = true)
    public RefundEligibility describeRefundEligibilityOfStudent(Long studentId, Currency currency) {
        return previewService.describeEligibilityOfExistingStudent(studentId, currency);
    }

    private static RefundDestination destinationOf(RequestRefundRequest request) {
        return new RefundDestination(request.destinationAccount(), request.destinationHolder());
    }
}
