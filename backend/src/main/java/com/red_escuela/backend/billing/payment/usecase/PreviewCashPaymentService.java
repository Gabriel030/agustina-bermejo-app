package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.billing.payment.service.CashPaymentPreviewService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class PreviewCashPaymentService implements PreviewCashPaymentUseCase {
    private final CashPaymentPreviewService previewService;

    public PreviewCashPaymentService(CashPaymentPreviewService previewService) {
        this.previewService = previewService;
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    @Transactional(readOnly = true)
    public CashPaymentPreview previewCashPayment(Long studentId, Money amount) {
        return previewService.previewCashPaymentOfStudent(studentId, amount);
    }
}
