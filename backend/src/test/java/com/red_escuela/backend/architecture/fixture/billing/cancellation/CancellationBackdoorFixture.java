package com.red_escuela.backend.architecture.fixture.billing.cancellation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceCreditNoteApplication;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;

public class CancellationBackdoorFixture {

    public void cancelWithoutTheOperation(Invoice invoice, InvoiceCreditNoteApplication application) {
        invoice.applyCreditNote(application, LocalDate.now(), StatusChangeActor.ofProcess("fixture"));
    }
}
