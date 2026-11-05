package com.red_escuela.backend.billing.annualupfront.service;

public record DiscardedAnnualUpfrontRequest(Long id, String status, String discardReason, Long invoiceId) {
}
