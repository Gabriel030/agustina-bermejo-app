package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvoiceAdjustmentRequest(
        @NotNull InvoiceAdjustmentType type,
        @NotNull MonetaryAdjustmentRequest value,
        @NotBlank String reason) {
}
