package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.FeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.PositiveMoney;
import java.time.LocalDate;

public record CreateAdHocInvoiceRequest(
        @NotNull FeeType feeType,
        @NotNull @PositiveMoney Money amount,
        @NotNull LocalDate dueDate,
        @NotBlank String description,
        Long subjectId,
        Long examBoardRegistrationId) {
}
