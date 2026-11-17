package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.Size;

public record UpdatePaymentGatewayConfigRequest(
        @Size(max = 255) String bankAlias,
        @Size(max = 255) String bankCbu,
        @Size(max = 255) String accountHolder,
        Boolean mpEnabled) {
}
