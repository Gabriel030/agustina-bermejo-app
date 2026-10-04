package com.red_escuela.backend.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateEntryFeeDiscountRequest(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountPoints) {
}
