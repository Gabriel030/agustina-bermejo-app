package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.shared.domain.Money;

public record RefundRequestCheck(Money requestedAmount, RefundDestination requestedDestination) {
}
