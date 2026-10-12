package com.red_escuela.backend.billing.payment.gateway;

public record OnlineCheckoutSession(String gatewayPaymentId, String checkoutUrl) {
}
