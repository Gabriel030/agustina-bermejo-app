package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.FeeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateAdHocInvoiceRequest(
        @NotNull FeeType feeType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotNull LocalDate dueDate,
        @NotBlank String description,
        Long subjectId,
        Long examBoardRegistrationId) {
}
