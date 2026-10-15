package com.red_escuela.backend.billing.note.dto;

import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCreditNoteRequest(@NotNull CreditNoteReason reason, @Size(max = 1000) String justification,
                                      @NotEmpty List<@Valid @NotNull CreditNoteLineAmountRequest> lines) {

    public List<CreditNoteLineAmount> toLineAmounts() {
        return lines.stream().map(CreditNoteLineAmountRequest::toLineAmount).toList();
    }
}
