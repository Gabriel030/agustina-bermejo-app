package com.red_escuela.backend.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.billing.entity.PaymentGatewayConfig;

public record PaymentGatewayConfigResponse(
        @Schema(nullable = true) String bankAlias,
        @Schema(nullable = true) String bankCbu,
        @Schema(nullable = true) String accountHolder,
        @Schema(nullable = true) String qrImageUrl,
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
