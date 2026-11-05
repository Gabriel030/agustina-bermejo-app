package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.CashPaymentPreview;
import com.red_escuela.backend.shared.domain.Money;

public interface PreviewCashPaymentUseCase {
    CashPaymentPreview previewCashPayment(Long studentId, Money amount);
}
