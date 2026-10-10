package com.red_escuela.backend.billing.note.dto;

import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.validation.PositiveMoney;
import jakarta.validation.constraints.NotNull;

public record CreditNoteLineAmountRequest(@NotNull Integer lineNumber, @NotNull @PositiveMoney Money amount) {

    public CreditNoteLineAmount toLineAmount() {
        return new CreditNoteLineAmount(lineNumber, amount);
    }
}
