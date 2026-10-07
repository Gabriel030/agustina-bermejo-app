package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

public class InvoiceDraftCreatingFixture {

    public Invoice forgeDraft(InvoiceDraftHeader header) {
        return Invoice.createDraftInvoice(header, StatusChangeActor.ofProcess("fixture"));
    }
}
