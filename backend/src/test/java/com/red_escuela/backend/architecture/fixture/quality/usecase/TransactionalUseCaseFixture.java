package com.red_escuela.backend.architecture.fixture.quality.usecase;

import org.springframework.transaction.annotation.Transactional;

public class TransactionalUseCaseFixture {

    @Transactional
    public void execute() {
    }
}
