package com.red_escuela.backend.architecture.fixture.billing.invoice;

import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;

public class LegacyBillingDependingFixture {

    public Class<?> legacyStatusType() {
        return BankTransferReceiptStatus.class;
    }
}
