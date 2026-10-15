package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public record DiscountDefinitionValidationResponse(List<PolicyViolation> violations) {
}
