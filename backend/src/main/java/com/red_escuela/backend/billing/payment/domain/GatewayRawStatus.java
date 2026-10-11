package com.red_escuela.backend.billing.payment.domain;

public record GatewayRawStatus(String status, String detail) {

    private static final GatewayRawStatus NONE = new GatewayRawStatus(null, null);

    public static GatewayRawStatus none() {
        return NONE;
    }
}
