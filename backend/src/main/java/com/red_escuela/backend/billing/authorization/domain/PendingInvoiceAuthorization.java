package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.billing.operation.BillingOperationType;

import java.time.Instant;

public record PendingInvoiceAuthorization(Long id, BillingOperationType type, Long requestedByPersonId, String requestedByName, Instant requestedAt) {
}
