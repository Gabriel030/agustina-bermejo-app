package com.red_escuela.backend.billing.support;

import org.springframework.context.ApplicationContext;

public final class BillingTestApplicationContext {

    private static volatile ApplicationContext context;

    private BillingTestApplicationContext() {
    }

    static void useContextOfTheRunningTest(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    static <T> T bean(Class<T> type) {
        if (context == null) {
            throw new IllegalStateException("The Spring context is not available yet");
        }
        return context.getBean(type);
    }
}
