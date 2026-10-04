package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.CreditNoteReasonType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.PositiveMoney;

public record CreateCreditNoteRequest(
        @NotNull @PositiveMoney Money amount,
        @NotNull CreditNoteReasonType reasonType,
        @NotBlank String reason) {
}
