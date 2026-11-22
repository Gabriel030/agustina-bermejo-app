package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalPreview;
import com.red_escuela.backend.billing.payment.dto.RegisterChargebackRequest;
import com.red_escuela.backend.billing.payment.dto.RevokeBankTransferApprovalRequest;
import com.red_escuela.backend.billing.payment.service.PaymentReversalPreviewService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewPaymentReversalService implements PreviewPaymentReversalUseCase {
    private final PaymentReversalPreviewService previewService;

    public PreviewPaymentReversalService(PaymentReversalPreviewService previewService) {
        this.previewService = previewService;
    }

    @Override
    public PaymentReversalPreview previewChargebackRegistration(Long paymentId, RegisterChargebackRequest request) {
        return previewService.previewChargebackRegistration(paymentId, request.amount(), request.chargedBackOn(), request.reference());
    }

    @Override
    public PaymentReversalPreview previewChargebackReversal(Long chargebackId) {
        return previewService.previewChargebackReversal(chargebackId);
    }

    @Override
    public PaymentReversalPreview previewApprovalRevocation(Long paymentId, RevokeBankTransferApprovalRequest request) {
        return previewService.previewApprovalRevocation(paymentId, request.reason());
    }
}
