package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.CreditNote;

import com.red_escuela.backend.shared.domain.Money;
import java.time.LocalDateTime;

public record CreditNoteResponse(
        Long id, Long studentId, Money amount, Money remainingAmount, String reasonType, String reason,
        String status, String createdByName, LocalDateTime createdAt) {

    public static CreditNoteResponse from(CreditNote creditNote) {
        return new CreditNoteResponse(
                creditNote.getId(), creditNote.getStudent().getId(), creditNote.getAmount(), creditNote.getRemainingAmount(),
                creditNote.getReasonType().name(), creditNote.getReason(), creditNote.getStatus().name(),
                creditNote.getCreatedBy().getName() + " " + creditNote.getCreatedBy().getLastname(), creditNote.getCreatedAt());
    }
}
