package com.red_escuela.backend.billing.authorization.dto;

import jakarta.validation.constraints.Size;

public record AuthorizationRequestDecisionRequest(@Size(max = 500) String decisionReason) {
}
