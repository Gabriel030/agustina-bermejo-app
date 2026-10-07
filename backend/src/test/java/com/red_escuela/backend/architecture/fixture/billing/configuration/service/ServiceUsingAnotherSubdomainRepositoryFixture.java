package com.red_escuela.backend.architecture.fixture.billing.configuration.service;

import com.red_escuela.backend.billing.fixturesubdomain.repository.OtherSubdomainRepositoryFixture;

public class ServiceUsingAnotherSubdomainRepositoryFixture {

    private final OtherSubdomainRepositoryFixture repository = new OtherSubdomainRepositoryFixture();

    public Object repository() {
        return repository;
    }
}
