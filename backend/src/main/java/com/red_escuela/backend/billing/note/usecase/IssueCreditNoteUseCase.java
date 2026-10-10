package com.red_escuela.backend.billing.note.usecase;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;

public interface IssueCreditNoteUseCase {
    BillingOperationOutcome<CreditNote> issueOrRequestCreditNote(Long invoiceId, CreateCreditNoteRequest request);
}
