package com.red_escuela.backend.billing.dto;

public record UpdatePaymentGatewayConfigRequest(
        String bankAlias,
        String bankCbu,
        String accountHolder,
        Boolean mpEnabled) {
}
