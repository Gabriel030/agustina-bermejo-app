package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;

public record PaymentGatewayConfigResponse(
        String bankAlias,
        String bankCbu,
        String accountHolder,
        String qrImageUrl,
        boolean mpEnabled) {

    public static PaymentGatewayConfigResponse from(PaymentGatewayConfig config, String qrImageUrl) {
        return new PaymentGatewayConfigResponse(
                config.getBankAlias(),
                config.getBankCbu(),
                config.getAccountHolder(),
                qrImageUrl,
                config.isMpEnabled());
    }
}
