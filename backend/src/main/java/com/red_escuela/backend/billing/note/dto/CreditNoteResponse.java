package com.red_escuela.backend.billing.note.dto;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record CreditNoteResponse(Long id, String creditNoteNumber, Long invoiceId, String invoiceNumber, String reason, String justification,
                                 Money offsetAmount, Money freeCreditAmount, Money totalAmount, LocalDate issueDate, LocalDate factDate,
                                 Long authorizationRequestId, List<CreditNoteLineResponse> lines) {

    public static CreditNoteResponse from(CreditNote note) {
        return new CreditNoteResponse(note.getId(), note.getCreditNoteNumber().value(), note.getInvoiceId(), note.getInvoiceNumber(), note.getReason().name(),
                note.getJustification(), note.getOffsetAmount().value(), note.getFreeCreditAmount().value(), note.getTotalAmount().value(),
                note.getIssueDate(), note.getFactDate(), note.getAuthorizationRequestId(),
                note.getLines().stream().map(CreditNoteLineResponse::from).toList());
    }
}
