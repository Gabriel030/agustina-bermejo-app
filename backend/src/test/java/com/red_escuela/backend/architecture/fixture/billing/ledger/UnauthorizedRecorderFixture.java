package com.red_escuela.backend.architecture.fixture.billing.ledger;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public class UnauthorizedRecorderFixture {

    private StudentCurrentAccountService service;

    public void record(StudentCurrentAccount account, NonNegativeMoney amount) {
        service.recordPaymentReceivedCredit(account, 1L, amount, LocalDate.now(), "x");
    }
}
