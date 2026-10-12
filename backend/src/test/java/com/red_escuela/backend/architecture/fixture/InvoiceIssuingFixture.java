package com.red_escuela.backend.architecture.fixture;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceNumber;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;

public class InvoiceIssuingFixture {

    public void forgeInvoice(Invoice invoice, InvoiceNumber number, StudentSnapshot snapshot, NonNegativeMoney amount) {
        StatusChangeActor actor = new StatusChangeActor(null, "fixture");
        invoice.issueInvoice(number, LocalDate.now(), LocalDate.now(), snapshot, actor);
        invoice.allocateFullInvoicePayment(amount, LocalDate.now(), actor);
    }
}
