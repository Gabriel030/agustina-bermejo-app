package com.red_escuela.backend.billing.payment.gateway.mercadopago;

import com.mercadopago.MercadoPagoConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoClientTimeouts {
    private final int connectionTimeoutMs;
    private final int connectionRequestTimeoutMs;
    private final int socketTimeoutMs;

    public MercadoPagoClientTimeouts(
            @Value("${mercadopago.connection-timeout-ms:3000}") int connectionTimeoutMs,
            @Value("${mercadopago.connection-request-timeout-ms:3000}") int connectionRequestTimeoutMs,
            @Value("${mercadopago.socket-timeout-ms:10000}") int socketTimeoutMs) {
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.connectionRequestTimeoutMs = connectionRequestTimeoutMs;
        this.socketTimeoutMs = socketTimeoutMs;
    }

    @PostConstruct
    void applyToSdk() {
        MercadoPagoConfig.setConnectionTimeout(connectionTimeoutMs);
        MercadoPagoConfig.setConnectionRequestTimeout(connectionRequestTimeoutMs);
        MercadoPagoConfig.setSocketTimeout(socketTimeoutMs);
    }
}
