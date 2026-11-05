package com.red_escuela.backend.billing.discount.dto;

import com.red_escuela.backend.billing.dto.MonetaryAdjustmentRequest;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreatePromotionBenefitRequest(
        @NotNull FeeType feeType,
        @Valid @NotNull MonetaryAdjustmentRequest value,
        Integer invoiceScopeCount) {
}
