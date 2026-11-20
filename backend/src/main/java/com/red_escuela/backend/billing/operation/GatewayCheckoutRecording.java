package com.red_escuela.backend.billing.operation;

public interface GatewayCheckoutRecording {

    void recordMercadoPagoCheckout(NewGatewayCheckout checkout);
}
