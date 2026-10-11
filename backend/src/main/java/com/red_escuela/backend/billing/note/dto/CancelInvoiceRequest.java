package com.red_escuela.backend.billing.note.dto;

import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelInvoiceRequest(@NotNull CreditNoteReason reason, @Size(max = 1000) String justification) {
}
