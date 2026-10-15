package com.red_escuela.backend.architecture.fixture.enrollment;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.time.LocalDate;

public class WithdrawalWritingBillingFixture {

    public void forceOverdueOnWithdrawal(Invoice invoice) {
        invoice.markInvoiceAsOverdue(LocalDate.now(), new StatusChangeActor(null, "fixture"));
    }
}
