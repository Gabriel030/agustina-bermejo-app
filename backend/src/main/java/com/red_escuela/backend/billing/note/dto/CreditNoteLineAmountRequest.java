package com.red_escuela.backend.billing.note.dto;

import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreditNoteLineAmountRequest(@NotNull Integer lineNumber, @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {

    public CreditNoteLineAmount toLineAmount() {
        return new CreditNoteLineAmount(lineNumber, amount);
    }
}
