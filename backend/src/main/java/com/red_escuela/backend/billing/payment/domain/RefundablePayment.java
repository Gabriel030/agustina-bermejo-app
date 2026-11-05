package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.Objects;

public record RefundablePayment(Long paymentId, LocalDate paidOn, PaymentMethodType methodType, NonNegativeMoney refundable, RefundDestination destination) {

    public RefundablePayment {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(paidOn, "paidOn");
        Objects.requireNonNull(methodType, "methodType");
        Objects.requireNonNull(refundable, "refundable");
        Objects.requireNonNull(destination, "destination");
    }
}
