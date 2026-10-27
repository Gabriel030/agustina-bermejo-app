package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalPreview;
import com.red_escuela.backend.billing.payment.dto.RegisterChargebackRequest;
import com.red_escuela.backend.billing.payment.dto.RevokeBankTransferApprovalRequest;

public interface PreviewPaymentReversalUseCase {
    PaymentReversalPreview previewChargebackRegistration(Long paymentId, RegisterChargebackRequest request);

    PaymentReversalPreview previewChargebackReversal(Long chargebackId);

    PaymentReversalPreview previewApprovalRevocation(Long paymentId, RevokeBankTransferApprovalRequest request);
}
