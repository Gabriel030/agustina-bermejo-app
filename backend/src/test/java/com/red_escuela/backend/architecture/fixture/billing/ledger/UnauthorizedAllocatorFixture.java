package com.red_escuela.backend.architecture.fixture.billing.ledger;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

public class UnauthorizedAllocatorFixture {

    private StudentCurrentAccountService service;

    public void allocate(StudentCurrentAccount account, NonNegativeMoney amount) {
        service.allocateUnallocatedCreditToInvoicePayment(account, amount);
    }
}
