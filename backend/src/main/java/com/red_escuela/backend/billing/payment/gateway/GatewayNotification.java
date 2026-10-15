package com.red_escuela.backend.billing.payment.gateway;

public record GatewayNotification(String gatewayPaymentId, String rawStatus) {
}
