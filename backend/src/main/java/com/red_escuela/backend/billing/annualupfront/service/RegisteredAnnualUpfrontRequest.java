package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;

public record RegisteredAnnualUpfrontRequest(Long id, String status, Long invoiceId, LocalDateTime paymentDeadline, Money total, LocalDateTime acceptedAt, String acceptedTermsVersion) {
}
