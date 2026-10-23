package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;

public interface ReverseChargebackUseCase {
    PaymentReversalResult reverseChargeback(Long chargebackId);
}
