package com.red_escuela.backend.dto;

import java.time.LocalDateTime;

public record BillingStandingResponse(String enrollmentType, Long enrollmentId, String billingStanding, LocalDateTime billingStandingUpdatedAt) {
}
