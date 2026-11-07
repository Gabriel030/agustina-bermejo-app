package com.red_escuela.backend.billing.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.billing.payment.domain.PaymentActionAvailability;

public record PaymentActionAvailabilityResponse(boolean allowed, @Schema(nullable = true) RefundBlockResponse blockedBy) {

    public static PaymentActionAvailabilityResponse from(PaymentActionAvailability availability) {
        return new PaymentActionAvailabilityResponse(availability.allowed(), RefundBlockResponse.from(availability.blockedBy()));
    }
}
