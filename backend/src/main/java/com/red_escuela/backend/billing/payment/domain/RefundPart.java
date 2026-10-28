package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;

public record RefundPart(Long paymentId, PaymentMethodType methodType, NonNegativeMoney amount, RefundDestination destination) {

    public RefundPart {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(methodType, "methodType");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(destination, "destination");
    }
}
