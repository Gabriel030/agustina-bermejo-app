package com.red_escuela.backend.architecture.fixture.billing.ledger;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public class AuthorizedOperationFixture implements FixtureOperation {

    private StudentCurrentAccountService service;
    private InvoiceRepository invoiceRepository;

    public void run(StudentCurrentAccount account, NonNegativeMoney amount, Invoice invoice) {
        service.recordPaymentReceivedCredit(account, 1L, amount, LocalDate.now(), "x");
        invoiceRepository.save(invoice);
    }
}
