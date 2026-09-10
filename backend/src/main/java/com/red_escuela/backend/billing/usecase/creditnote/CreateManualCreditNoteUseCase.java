package com.red_escuela.backend.billing.usecase.creditnote;

import com.red_escuela.backend.billing.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.entity.CreditNote;

public interface CreateManualCreditNoteUseCase {
    CreditNote create(Long studentId, CreateCreditNoteRequest request);
}
