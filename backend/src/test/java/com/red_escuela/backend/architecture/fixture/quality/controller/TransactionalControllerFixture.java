package com.red_escuela.backend.architecture.fixture.quality.controller;

import org.springframework.transaction.annotation.Transactional;

public class TransactionalControllerFixture {

    @Transactional
    public void handle() {
    }
}
