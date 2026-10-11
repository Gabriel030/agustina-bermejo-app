package com.red_escuela.backend.billing.payment.domain;

public enum PaymentGatewayCode {
    MANUAL(false),
    MERCADO_PAGO(true),
    DLOCAL(true);

    private final boolean onlineProvider;

    PaymentGatewayCode(boolean onlineProvider) {
        this.onlineProvider = onlineProvider;
    }

    public boolean isOnlineProvider() {
        return onlineProvider;
    }
}
