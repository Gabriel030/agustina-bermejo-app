package com.red_escuela.backend.architecture.fixture.billing.configuration.usecase;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;

public class UseCaseDependingOnAnEntityFixture {

    public Class<?> entityType() {
        return BankTransferReceipt.class;
    }
}
