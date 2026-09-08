package com.red_escuela.backend.billing.dto;

import java.time.LocalDateTime;
import java.util.List;

public record EnrollmentBillingStatusResponse(String billingStanding, LocalDateTime billingStandingUpdatedAt, List<InvoiceResponse> invoices) {
}
