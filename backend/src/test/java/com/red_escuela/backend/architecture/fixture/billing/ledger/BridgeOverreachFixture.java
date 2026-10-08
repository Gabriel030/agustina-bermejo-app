package com.red_escuela.backend.architecture.fixture.billing.ledger;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public class BridgeOverreachFixture {

    private StudentCurrentAccountService service;

    public void recordWhatTheBridgeMayRecord(StudentCurrentAccount account, NonNegativeMoney amount) {
        service.recordPaymentReceivedCredit(account, 1L, amount, LocalDate.now(), "x");
    }

    public void recordWhatTheBridgeMayNotRecord(StudentCurrentAccount account, NonNegativeMoney amount) {
        service.recordRefundPaidDebit(account, 1L, amount, LocalDate.now(), "x");
    }
}
