package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.FeeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreatePromotionBenefitRequest(
        @NotNull FeeType feeType,
        @Valid @NotNull MonetaryAdjustmentValueRequest value,
        Integer invoiceScopeCount) {
}
