package com.red_escuela.backend.billing.operation;

public record PaymentGatewayConfigSnapshot(
        boolean mpEnabled,
        String bankAlias,
        String bankCbu,
        String accountHolder,
        String qrImagePath) {
}
