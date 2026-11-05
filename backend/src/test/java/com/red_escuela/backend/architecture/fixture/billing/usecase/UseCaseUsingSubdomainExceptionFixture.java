package com.red_escuela.backend.architecture.fixture.billing.usecase;

import com.red_escuela.backend.architecture.fixture.billing.exception.SubdomainExceptionFixture;

public class UseCaseUsingSubdomainExceptionFixture {
    public Class<?> exceptionType() {
        return SubdomainExceptionFixture.class;
    }
}
