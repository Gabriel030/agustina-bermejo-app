package com.red_escuela.backend.architecture.fixture.billing.configuration.service;

import com.red_escuela.backend.shared.domain.NegativeMoneyAmountException;

public class ServiceThrowingABusinessExceptionFixture {

    public void reject() {
        throw new NegativeMoneyAmountException();
    }
}
