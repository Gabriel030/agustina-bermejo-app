package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;

public record CreditNoteIssuingContext(IssueCreditNoteCommand command, CreditedInvoiceFacts invoice, String creditNoteRecipientDocumentNumber) {
}
