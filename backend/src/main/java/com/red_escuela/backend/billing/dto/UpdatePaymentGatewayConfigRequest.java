package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.Size;

public record UpdatePaymentGatewayConfigRequest(
        @Size(max = 50) String bankAlias,
        @Size(max = 22) String bankCbu,
        @Size(max = 100) String accountHolder,
        Boolean mpEnabled) {
}
