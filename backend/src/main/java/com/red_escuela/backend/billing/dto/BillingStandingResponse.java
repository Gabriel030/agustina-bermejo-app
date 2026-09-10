package com.red_escuela.backend.billing.dto;

import java.time.LocalDateTime;

public record BillingStandingResponse(String enrollmentType, Long enrollmentId, String billingStanding, LocalDateTime billingStandingUpdatedAt) {
}
