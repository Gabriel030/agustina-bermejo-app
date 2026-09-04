package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.InvoiceAdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateInvoiceAdjustmentRequest(
        @NotNull InvoiceAdjustmentType type,
        @NotNull MonetaryAdjustmentValueRequest value,
        @NotBlank String reason) {
}
