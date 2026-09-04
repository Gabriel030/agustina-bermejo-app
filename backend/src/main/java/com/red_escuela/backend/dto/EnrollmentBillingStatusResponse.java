package com.red_escuela.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

// billingStanding is fixed "UP_TO_DATE" until Fase 6.3 defines the real state machine.
public record EnrollmentBillingStatusResponse(String billingStanding, LocalDateTime billingStandingUpdatedAt, List<InvoiceResponse> invoices) {
}
