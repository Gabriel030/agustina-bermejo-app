package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import org.springframework.stereotype.Component;

@Component
public class StoredCreditNoteLoader {
    private final CreditNoteRepository creditNotes;

    public StoredCreditNoteLoader(CreditNoteRepository creditNotes) {
        this.creditNotes = creditNotes;
    }

    public CreditNote loadCreditNoteWithLines(Long creditNoteId) {
        return creditNotes.findWithLinesById(creditNoteId).orElseThrow(() -> new IllegalStateException("The stored credit note " + creditNoteId + " does not exist"));
    }
}
