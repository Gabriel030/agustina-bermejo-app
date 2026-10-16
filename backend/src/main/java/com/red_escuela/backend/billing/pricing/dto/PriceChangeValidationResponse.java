package com.red_escuela.backend.billing.pricing.dto;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public record PriceChangeValidationResponse(List<PolicyViolation> violations) {
}
