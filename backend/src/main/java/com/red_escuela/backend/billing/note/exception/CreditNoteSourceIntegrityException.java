package com.red_escuela.backend.billing.note.exception;

import com.red_escuela.backend.shared.exception.DataIntegrityBrokenException;

public class CreditNoteSourceIntegrityException extends DataIntegrityBrokenException {
    public CreditNoteSourceIntegrityException(Long creditNoteId) {
        super("Credit note " + creditNoteId + " referenced by an allocation source does not exist");
    }
}
