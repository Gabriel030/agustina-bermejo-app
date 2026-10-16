package com.red_escuela.backend.billing.note.domain;

import java.time.LocalDate;
import java.util.Objects;

public record CreditNoteSettlement(Long creditNoteId, LocalDate issueDate) {

    public CreditNoteSettlement {
        Objects.requireNonNull(creditNoteId, "creditNoteId");
        Objects.requireNonNull(issueDate, "issueDate");
    }
}
