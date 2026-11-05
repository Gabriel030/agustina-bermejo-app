package com.red_escuela.backend.architecture.fixture.billing.usecase;

import com.red_escuela.backend.architecture.fixture.billing.payment.service.SubdomainServiceFixture;

public class UseCaseUsingSubdomainServiceFixture {
    public Class<?> serviceType() {
        return SubdomainServiceFixture.class;
    }
}
