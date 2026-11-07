package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.AnnualUpfrontCashPaymentResult;
import com.red_escuela.backend.billing.payment.dto.RegisterAnnualUpfrontCashPaymentRequest;

public interface RegisterAnnualUpfrontCashPaymentUseCase {
    AnnualUpfrontCashPaymentResult registerAnnualUpfrontCashPayment(Long studentId, Long requestId, RegisterAnnualUpfrontCashPaymentRequest request, String idempotencyKey);
}
