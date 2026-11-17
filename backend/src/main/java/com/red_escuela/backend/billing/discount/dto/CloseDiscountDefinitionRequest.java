package com.red_escuela.backend.billing.discount.dto;

import jakarta.validation.constraints.Size;

public record CloseDiscountDefinitionRequest(@Size(max = 500) String reason) {
}
