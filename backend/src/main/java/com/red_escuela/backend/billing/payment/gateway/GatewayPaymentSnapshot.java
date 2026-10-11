package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.billing.payment.domain.GatewayRawStatus;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public record GatewayPaymentSnapshot(String gatewayPaymentId, PaymentStatus status, GatewayRawStatus rawStatus, NonNegativeMoney amount, LocalDate paidOn) {
}
