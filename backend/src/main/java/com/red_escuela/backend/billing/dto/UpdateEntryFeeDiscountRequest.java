package com.red_escuela.backend.billing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.validation.constraints.NotNull;

public record UpdateEntryFeeDiscountRequest(@NotNull @JsonProperty("discountPoints") Percentage discountPercent) {
}
