package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.dto.RegisterChargebackRequest;

public interface RegisterChargebackUseCase {
    PaymentReversalResult registerChargeback(Long paymentId, RegisterChargebackRequest request);
}
