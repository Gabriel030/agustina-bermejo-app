package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.InvoiceAdjustment;

import com.red_escuela.backend.shared.domain.Money;
import java.time.LocalDateTime;

public record InvoiceAdjustmentResponse(String type, Money resolvedAmount, String reason, String appliedByName, LocalDateTime appliedAt) {

    public static InvoiceAdjustmentResponse from(InvoiceAdjustment adjustment) {
        return new InvoiceAdjustmentResponse(
                adjustment.getType().name(),
                adjustment.getValue().applyTo(adjustment.getResolvedBase()),
                adjustment.getReason(),
                adjustment.getAppliedBy() == null ? null : adjustment.getAppliedBy().getName() + " " + adjustment.getAppliedBy().getLastname(),
                adjustment.getAppliedAt());
    }
}
