package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.PaymentActionAvailability;

public record PaymentActionAvailabilityResponse(boolean allowed, RefundBlockResponse blockedBy) {

    public static PaymentActionAvailabilityResponse from(PaymentActionAvailability availability) {
        return new PaymentActionAvailabilityResponse(availability.allowed(), RefundBlockResponse.from(availability.blockedBy()));
    }
}
