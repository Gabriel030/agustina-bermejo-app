package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.UUID;

public record NewGatewayCheckout(UUID publicReference, Long invoiceId, NonNegativeMoney amount, String gatewayPreferenceId) {
}
