package com.red_escuela.backend.billing.annualupfront.dto;

import jakarta.validation.constraints.Size;

public record RequestAnnualUpfrontBody(boolean termsAccepted, @Size(max = 50) String termsVersion) {
}
