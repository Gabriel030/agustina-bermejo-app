package com.red_escuela.backend.architecture.fixture.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceCreditNoteApplication;
import com.red_escuela.backend.billing.note.domain.CreditNote;

public class NoteServiceBackdoorFixture {

    public InvoiceCreditNoteApplication buildCancellationWithoutTheOperation(CreditNote note) {
        return note.toInvoiceCancellationApplication(true);
    }
}
