package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record RefundablePaymentView(Long paymentId, LocalDate paidOn, PaymentMethodType methodType, NonNegativeMoney credited, NonNegativeMoney refunded,
                                    NonNegativeMoney pending, NonNegativeMoney refundableNow, RefundDestination receiptDestination) {
}
