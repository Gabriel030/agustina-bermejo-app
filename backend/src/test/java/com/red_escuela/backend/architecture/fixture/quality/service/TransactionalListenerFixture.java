package com.red_escuela.backend.architecture.fixture.quality.service;

import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

public class TransactionalListenerFixture {

    @EventListener
    @Transactional
    public void on(Object event) {
    }
}
