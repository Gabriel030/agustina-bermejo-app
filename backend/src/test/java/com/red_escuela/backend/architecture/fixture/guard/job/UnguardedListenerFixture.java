package com.red_escuela.backend.architecture.fixture.guard.job;

import org.springframework.context.event.EventListener;

public class UnguardedListenerFixture {

    @EventListener
    public void on(Object event) {
    }
}
