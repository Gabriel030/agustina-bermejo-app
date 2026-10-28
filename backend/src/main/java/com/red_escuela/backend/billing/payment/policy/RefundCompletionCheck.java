package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;

import java.time.LocalDate;

public record RefundCompletionCheck(PaymentMethodType originalMethod, PaymentMethodType declaredMethod, LocalDate completedOn, LocalDate requestedOn, LocalDate today,
                                    String reference) {
}
