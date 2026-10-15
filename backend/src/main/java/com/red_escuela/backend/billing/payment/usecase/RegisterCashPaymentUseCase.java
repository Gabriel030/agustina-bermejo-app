package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.domain.CashPaymentRegistrationResult;

public interface RegisterCashPaymentUseCase {
    CashPaymentRegistrationResult registerCashPayment(Long studentId, RegisterCashPaymentRequest request, String idempotencyKey);
}
