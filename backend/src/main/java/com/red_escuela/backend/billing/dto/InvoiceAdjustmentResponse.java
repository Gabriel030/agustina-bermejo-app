package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.InvoiceAdjustment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record InvoiceAdjustmentResponse(String type, BigDecimal resolvedAmount, String reason, String appliedByName, LocalDateTime appliedAt) {

    public static InvoiceAdjustmentResponse from(InvoiceAdjustment adjustment) {
        return new InvoiceAdjustmentResponse(
                adjustment.getType().name(),
                adjustment.getValue().resolve(adjustment.getResolvedBase()),
                adjustment.getReason(),
                adjustment.getAppliedBy() == null ? null : adjustment.getAppliedBy().getName() + " " + adjustment.getAppliedBy().getLastname(),
                adjustment.getAppliedAt());
    }
}
