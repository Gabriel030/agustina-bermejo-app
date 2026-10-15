package com.red_escuela.backend.billing.configuration.dto;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public record ConfigurationChangeValidationResponse(List<PolicyViolation> violations) {
}
