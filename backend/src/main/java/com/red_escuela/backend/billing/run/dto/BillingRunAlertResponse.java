package com.red_escuela.backend.billing.run.dto;

import java.time.LocalDate;

public record BillingRunAlertResponse(BillingRunAlertType type, LocalDate businessDate, Long nightlyRunId, String message) {
}
