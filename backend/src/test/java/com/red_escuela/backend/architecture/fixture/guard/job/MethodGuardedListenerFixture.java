package com.red_escuela.backend.architecture.fixture.guard.job;

import com.red_escuela.backend.platform.api.RequiresModule;
import org.springframework.transaction.event.TransactionalEventListener;

public class MethodGuardedListenerFixture {

    @RequiresModule("billing")
    @TransactionalEventListener
    public void on(Object event) {
    }
}
