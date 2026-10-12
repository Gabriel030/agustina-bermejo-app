package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public record GatewayRefundResult(String gatewayRefundId, NonNegativeMoney amount) {
}
