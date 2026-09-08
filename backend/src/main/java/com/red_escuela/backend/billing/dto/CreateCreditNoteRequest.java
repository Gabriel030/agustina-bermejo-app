package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateCreditNoteRequest(
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull CreditNoteReasonType reasonType,
        @NotBlank String reason) {
}
